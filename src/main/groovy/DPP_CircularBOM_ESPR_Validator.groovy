import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.security.MessageDigest
import java.time.Instant
import java.math.RoundingMode

def Message processData(Message message) {
    def reader = message.getBody(java.io.Reader)
    def payload = new JsonSlurper().parse(reader)

    String materialId = (payload.MaterialId ?: "").trim().toUpperCase()
    String productDescription = (payload.ProductDescription ?: "").trim()
    String gtin14 = (payload.GTIN14 ?: "").replaceAll("[^0-9]", "")
    String batchSerial = (payload.BatchSerial ?: "BATCH-2026-001").trim().toUpperCase()
    BigDecimal minRecycledThreshold = (payload.MinRecycledThresholdPct ?: 25.0) as BigDecimal
    List components = (payload.BomComponents ?: []) as List

    List<String> complianceViolations = []
    if (!materialId || materialId.length() < 3) complianceViolations.add("INVALID_MATERIAL_ID: MaterialId is mandatory for S/4HANA 2025 API_PRODUCT_SRV.")
    if (!isValidGs1Gtin14(gtin14)) complianceViolations.add("INVALID_GS1_GTIN14: Failed GS1 Mod-10 check-digit verification for [${gtin14}].")
    if (components.isEmpty()) complianceViolations.add("EMPTY_CIRCULAR_BOM: At least one Bill of Materials component is required for ESPR DPP.")

    BigDecimal totalMassKg = BigDecimal.ZERO
    BigDecimal weightedRecycledMass = BigDecimal.ZERO
    BigDecimal totalCarbonFootprintKg = BigDecimal.ZERO

    components.each { comp ->
        String compId = (comp.ComponentId ?: "UNKNOWN").toString()
        BigDecimal mass = (comp.MassKg ?: 0.0) as BigDecimal
        BigDecimal recycledPct = (comp.RecycledContentPct ?: 0.0) as BigDecimal
        BigDecimal carbonFactor = (comp.CarbonFootprintPerKg ?: 0.0) as BigDecimal
        BigDecimal svhcPct = (comp.SvhcConcentrationPct ?: 0.0) as BigDecimal

        if (mass <= BigDecimal.ZERO) complianceViolations.add("INVALID_COMPONENT_MASS: Component [${compId}] must have positive MassKg.")
        if (svhcPct > new BigDecimal("0.1")) complianceViolations.add("REACH_SVHC_THRESHOLD_EXCEEDED: Component [${compId}] contains ${svhcPct}% SVHC (EU limit: 0.1%).")

        totalMassKg = totalMassKg.add(mass)
        weightedRecycledMass = weightedRecycledMass.add(mass.multiply(recycledPct))
        totalCarbonFootprintKg = totalCarbonFootprintKg.add(mass.multiply(carbonFactor))
    }

    BigDecimal overallRecycledPct = totalMassKg > BigDecimal.ZERO ? weightedRecycledMass.divide(totalMassKg, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO
    BigDecimal roundedCarbonFootprint = totalCarbonFootprintKg.setScale(3, RoundingMode.HALF_UP)
    BigDecimal roundedTotalMass = totalMassKg.setScale(3, RoundingMode.HALF_UP)

    if (totalMassKg > BigDecimal.ZERO && overallRecycledPct < minRecycledThreshold) {
        complianceViolations.add("ESPR_CIRCULARITY_DEFICIT: Weighted recycled content ${overallRecycledPct}% is below required ${minRecycledThreshold}%.")
    }

    String gs1DigitalLink = "https://id.gs1.org/01/${gtin14}/21/${batchSerial}"
    String dppProvenanceHash = MessageDigest.getInstance("SHA-256")
        .digest("${materialId}|${gtin14}|${batchSerial}|${roundedTotalMass}|${overallRecycledPct}|${roundedCarbonFootprint}".getBytes("UTF-8"))
        .encodeHex().toString()
    message.setHeader("X-DPP-Provenance-Hash", dppProvenanceHash)

    if (!complianceViolations.isEmpty()) {
        message.setHeader("X-DPP-Compliance-Status", "REJECTED")
        message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson([
            status: "QUARANTINED_ESPR_NON_COMPLIANT", regulation: "EU_ESPR_2024_1781_AND_REACH_ART33",
            timestamp: Instant.now().toString(), materialId: materialId, gtin14: gtin14,
            dppProvenanceSha256: dppProvenanceHash,
            computedMetrics: [totalMassKg: roundedTotalMass, weightedRecycledContentPct: overallRecycledPct, productCarbonFootprintKgCO2e: roundedCarbonFootprint],
            violations: complianceViolations
        ])))
    } else {
        message.setHeader("X-DPP-Compliance-Status", "CERTIFIED")
        message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson([
            "@odata.context": "\$metadata#A_Product/\$entity", Product: materialId, ProductType: "FERT",
            BaseUnit: "KG", GrossWeight: roundedTotalMass, NetWeight: roundedTotalMass, WeightUnit: "KG",
            to_Description: [[Language: "EN", ProductDescription: productDescription]],
            DigitalProductPassport_JSONLD: [
                "@context": "https://w3id.org/dpp/v1", type: ["VerifiableCredential", "DigitalProductPassport"],
                issuer: "did:web:sap-mdi.enterprise.eu", issuanceDate: Instant.now().toString(),
                gs1DigitalLink: gs1DigitalLink, provenanceSha256: dppProvenanceHash,
                sustainabilityMetrics: [totalMassKg: roundedTotalMass, weightedRecycledContentPct: overallRecycledPct, cradleToGatePcfKgCO2e: roundedCarbonFootprint, esprComplianceStatus: "CERTIFIED_FOR_EU_MARKET"]
            ]
        ])))
    }
    return message
}

private boolean isValidGs1Gtin14(String gtin) {
    if (!gtin || gtin.length() != 14 || !gtin.matches("\\d{14}")) return false
    int sum = 0
    for (int i = 0; i < 13; i++) {
        int digit = Character.getNumericValue(gtin.charAt(12 - i))
        sum += digit * ((i % 2 == 0) ? 3 : 1)
    }
    return ((10 - (sum % 10)) % 10) == Character.getNumericValue(gtin.charAt(13))
}

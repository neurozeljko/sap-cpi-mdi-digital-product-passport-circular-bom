import spock.lang.Specification
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import com.sap.gateway.ip.core.customdev.util.Message

class DppCircularBomValidatorSpec extends Specification {
    def script = new GroovyShell().parse(new File("src/main/groovy/DPP_CircularBOM_ESPR_Validator.groovy"))

    def "Should certify ESPR-compliant Circular BOM and mint W3C DPP JSON-LD"() {
        given:
        def msg = new Message()
        msg.setBody(JsonOutput.toJson([
            MaterialId: "EV-BAT-MOD-800V", ProductDescription: "800V LFP Battery Module",
            GTIN14: "10614141000415", BatchSerial: "DE-MUC-2026-88412", MinRecycledThresholdPct: 30.0,
            BomComponents: [
                [ComponentId: "ALU-6061", MassKg: 18.5, RecycledContentPct: 65.0, CarbonFootprintPerKg: 2.15, SvhcConcentrationPct: 0.0],
                [ComponentId: "LFP-CELL", MassKg: 42.0, RecycledContentPct: 18.0, CarbonFootprintPerKg: 6.80, SvhcConcentrationPct: 0.02]
            ]
        ]))
        when:
        def res = script.processData(msg)
        def json = new JsonSlurper().parseText(res.getBody(String))
        then:
        res.getHeaders()["X-DPP-Compliance-Status"] == "CERTIFIED"
        json.DigitalProductPassport_JSONLD.sustainabilityMetrics.weightedRecycledContentPct == 32.36
    }

    def "Should reject BOM exceeding EU REACH Article 33 SVHC threshold"() {
        given:
        def msg = new Message()
        msg.setBody(JsonOutput.toJson([
            MaterialId: "IND-CTRL-X9", ProductDescription: "Controller", GTIN14: "10614141000415",
            BomComponents: [[ComponentId: "PCB-01", MassKg: 2.4, RecycledContentPct: 5.0, CarbonFootprintPerKg: 14.2, SvhcConcentrationPct: 0.45]]
        ]))
        when:
        def res = script.processData(msg)
        then:
        res.getHeaders()["X-DPP-Compliance-Status"] == "REJECTED"
    }

    def "Should reject invalid GS1 GTIN-14 check-digit"() {
        given:
        def msg = new Message()
        msg.setBody(JsonOutput.toJson([
            MaterialId: "MAT-ERR", ProductDescription: "Invalid GTIN", GTIN14: "10614141000419",
            BomComponents: [[ComponentId: "C1", MassKg: 1.0, RecycledContentPct: 50.0, CarbonFootprintPerKg: 1.0, SvhcConcentrationPct: 0.0]]
        ]))
        when:
        def res = script.processData(msg)
        then:
        res.getHeaders()["X-DPP-Compliance-Status"] == "REJECTED"
    }
}

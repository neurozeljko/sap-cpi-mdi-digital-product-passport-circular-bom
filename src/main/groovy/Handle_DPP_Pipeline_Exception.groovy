import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import java.time.Instant

def Message processData(Message message) {
    def ex = message.getProperties().get("CamelExceptionCaught")
    message.setHeader("CamelHttpResponseCode", 500)
    message.setHeader("Content-Type", "application/json")
    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson([
        error: "DPP_CIRCULAR_BOM_PIPELINE_ERROR", timestamp: Instant.now().toString(),
        details: ex ? ex.getMessage() : "Unhandled MDI DPP Execution Failure"
    ])))
    return message
}

package com.sap.gateway.ip.core.customdev.util
class Message {
    private Object body
    Map<String, Object> headers = [:]
    Map<String, Object> properties = [:]
    def <T> T getBody(Class<T> type) {
        if (type == Reader && body != null) return new StringReader(body.toString()) as T
        return body as T
    }
    void setBody(Object body) { this.body = body }
    void setHeader(String name, Object value) { headers[name] = value }
    void setProperty(String name, Object value) { properties[name] = value }
}

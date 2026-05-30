package com.caterktor.media.plugins

import com.caterktor.media.util.generateRequestId
import com.caterktor.media.util.generateTraceId
import io.ktor.server.application.*
import io.ktor.util.AttributeKey

val RequestIdKey = AttributeKey<String>("RequestId")
val TraceIdKey = AttributeKey<String>("TraceId")

fun Application.configureRequestTracking() {
    intercept(ApplicationCallPipeline.Setup) {
        val requestId = call.request.headers["X-Request-ID"]?.takeIf { it.isNotBlank() }
            ?: generateRequestId()
        call.attributes.put(RequestIdKey, requestId)
        call.attributes.put(TraceIdKey, generateTraceId())
    }
    intercept(ApplicationCallPipeline.Call) {
        val requestId = call.attributes.getOrNull(RequestIdKey) ?: ""
        val traceId = call.attributes.getOrNull(TraceIdKey) ?: ""
        call.response.headers.append("X-Request-ID", requestId)
        call.response.headers.append("X-Trace-ID", traceId)
        proceed()
    }
}

package com.caterktor.media.plugins

import com.caterktor.media.util.generateTraceId
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.util.AttributeKey

val TraceIdKey = AttributeKey<String>("TraceId")

fun Application.configureRequestTracking() {
    install(CallId) {
        retrieveFromHeader("X-Request-ID")
        replyToHeader("X-Request-ID")
        generate { "req_${java.util.UUID.randomUUID().toString().replace("-", "").take(10)}" }
    }
    intercept(ApplicationCallPipeline.Setup) {
        call.attributes.put(TraceIdKey, generateTraceId())
    }
    intercept(ApplicationCallPipeline.Call) {
        call.response.headers.append("X-Trace-ID", call.attributes.getOrNull(TraceIdKey) ?: "")
    }
}

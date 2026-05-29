package com.caterktor.media.routes

import com.caterktor.media.models.ApiMeta
import com.caterktor.media.util.ALLOWED_CONTENT_TYPES
import com.caterktor.media.util.requestId
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val data: HealthData, val meta: ApiMeta)
@Serializable
data class HealthData(val status: String, val version: String)

@Serializable
data class CapabilitiesResponse(val data: CapabilitiesData, val meta: ApiMeta)
@Serializable
data class CapabilitiesData(
    val maxUploadBytes: Long,
    val allowedContentTypes: List<String>,
    val rangeRequests: Boolean,
    val sse: Boolean,
    val debugEndpoints: Boolean
)

fun Route.healthRoutes(debugEnabled: Boolean, maxUploadBytes: Long) {
    get("/v1/health") {
        call.respond(HealthResponse(HealthData("ok", "1.0.0"), ApiMeta(call.requestId())))
    }
    get("/v1/capabilities") {
        call.respond(
            CapabilitiesResponse(
                CapabilitiesData(
                    maxUploadBytes = maxUploadBytes,
                    allowedContentTypes = ALLOWED_CONTENT_TYPES.toList(),
                    rangeRequests = true,
                    sse = true,
                    debugEndpoints = debugEnabled
                ),
                ApiMeta(call.requestId())
            )
        )
    }
}

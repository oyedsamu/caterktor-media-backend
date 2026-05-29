package com.caterktor.media.plugins

import com.caterktor.media.models.ApiError
import com.caterktor.media.models.ApiMeta
import com.caterktor.media.models.ErrorEnvelope
import com.caterktor.media.util.emptyJsonObject
import com.caterktor.media.util.requestId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ApiException(
    val statusCode: HttpStatusCode,
    val errorCode: String,
    message: String,
    val details: kotlinx.serialization.json.JsonObject = emptyJsonObject()
) : Exception(message)

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(
                cause.statusCode,
                ErrorEnvelope(
                    error = ApiError(cause.errorCode, cause.message ?: "", cause.details),
                    meta = ApiMeta(call.requestId())
                )
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorEnvelope(
                    error = ApiError("INTERNAL_ERROR", "An unexpected error occurred", emptyJsonObject()),
                    meta = ApiMeta(call.requestId())
                )
            )
        }
        status(HttpStatusCode.Unauthorized) { call, _ ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorEnvelope(
                    error = ApiError("UNAUTHORIZED", "Missing or invalid bearer token", emptyJsonObject()),
                    meta = ApiMeta(call.requestId())
                )
            )
        }
    }
}

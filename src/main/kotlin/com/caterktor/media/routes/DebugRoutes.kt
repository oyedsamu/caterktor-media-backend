package com.caterktor.media.routes

import com.caterktor.media.models.ApiMeta
import com.caterktor.media.plugins.ApiException
import com.caterktor.media.services.MediaStore
import com.caterktor.media.util.requestId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable data class SimpleResponse(val data: SimpleData, val meta: ApiMeta)
@Serializable data class SimpleData(val message: String)

fun Route.debugRoutes() {
    get("/v1/debug/status/{statusCode}") {
        val code = call.parameters["statusCode"]?.toIntOrNull()
            ?: throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "Invalid status code")
        call.respond(HttpStatusCode.fromValue(code))
    }

    get("/v1/debug/delay") {
        val ms = call.request.queryParameters["ms"]?.toLongOrNull() ?: 1000L
        val capped = minOf(ms, 30_000L)
        delay(capped)
        call.respond(SimpleResponse(SimpleData("Delayed ${capped}ms"), ApiMeta(call.requestId())))
    }

    get("/v1/debug/chunked/{mediaId}") {
        val media = MediaStore.media[call.parameters["mediaId"]]
            ?: throw ApiException(HttpStatusCode.NotFound, "MEDIA_NOT_FOUND", "Media not found")
        streamFileResponse(call, media, forceChunked = true)
    }

    get("/v1/debug/interrupt/{mediaId}") {
        val after = call.request.queryParameters["after"]?.toLongOrNull() ?: 1024L
        val media = MediaStore.media[call.parameters["mediaId"]]
            ?: throw ApiException(HttpStatusCode.NotFound, "MEDIA_NOT_FOUND", "Media not found")
        streamFileResponse(call, media, interruptAfter = after)
    }

    post("/v1/debug/fail-scan/{mediaId}") {
        val media = MediaStore.media[call.parameters["mediaId"]]
            ?: throw ApiException(HttpStatusCode.NotFound, "MEDIA_NOT_FOUND", "Media not found")
        media.forceScanFail = true
        call.respond(SimpleResponse(SimpleData("Scan will fail for ${media.id}"), ApiMeta(call.requestId())))
    }

    post("/v1/debug/reset") {
        MediaStore.reset()
        call.respond(SimpleResponse(SimpleData("Store reset to seed state"), ApiMeta(call.requestId())))
    }
}

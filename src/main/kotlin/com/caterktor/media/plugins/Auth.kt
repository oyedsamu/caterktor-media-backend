package com.caterktor.media.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*

fun ApplicationCall.requireBearerAuth() {
    val auth = request.headers["Authorization"] ?: ""
    if (!auth.startsWith("Bearer ") || auth.removePrefix("Bearer ").isBlank()) {
        throw ApiException(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Missing or invalid bearer token")
    }
}

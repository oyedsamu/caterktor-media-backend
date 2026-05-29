package com.caterktor.media.util

import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.security.MessageDigest
import java.util.UUID

fun generateId(prefix: String) = "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(12)}"
fun generateRequestId() = "req_${UUID.randomUUID().toString().replace("-", "").take(10)}"
fun generateTraceId() = "trc_${UUID.randomUUID().toString().replace("-", "").take(10)}"

fun ApplicationCall.requestId(): String =
    callId ?: request.headers["X-Request-ID"] ?: generateRequestId()

fun ByteArray.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(this).joinToString("") { "%02x".format(it) }
}

fun java.io.File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { stream ->
        val buf = ByteArray(8192)
        var read: Int
        while (stream.read(buf).also { read = it } != -1) {
            digest.update(buf, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun sanitizeFilename(raw: String): String {
    val name = raw.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_")
    val dangerous = setOf("exe", "bat", "cmd", "sh", "ps1", "vbs", "js", "jar", "dll", "so", "dylib")
    val ext = name.substringAfterLast('.', "").lowercase()
    if (ext in dangerous) throw IllegalArgumentException("Dangerous file extension: $ext")
    if (name.contains("..")) throw IllegalArgumentException("Path traversal detected")
    return name.take(255)
}

val ALLOWED_CONTENT_TYPES = setOf(
    "application/pdf", "image/jpeg", "image/png", "application/octet-stream"
)

fun emptyJsonObject(): JsonObject = buildJsonObject {}

package com.caterktor.media.routes

import com.caterktor.media.models.MediaObject
import com.caterktor.media.plugins.ApiException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun streamFileResponse(
    call: ApplicationCall,
    media: MediaObject,
    forceChunked: Boolean = false,
    interruptAfter: Long? = null
) {
    val file = media.tempFile
    val fileSize = file.length()
    val etag = "\"${media.sha256.take(16)}\""

    val rangeHeader = call.request.headers[HttpHeaders.Range]
    val ifRangeHeader = call.request.headers[HttpHeaders.IfRange]
    val useRange = rangeHeader != null && ifRangeHeader.let { it == null || it == etag }

    call.response.headers.append(HttpHeaders.ETag, etag)
    call.response.headers.append(HttpHeaders.AcceptRanges, "bytes")
    call.response.headers.append(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, media.filename).toString()
    )

    if (useRange && rangeHeader != null) {
        val range = parseRange(rangeHeader, fileSize)
            ?: throw ApiException(
                HttpStatusCode.fromValue(416),
                "RANGE_NOT_SATISFIABLE",
                "Range not satisfiable",
                buildJsonObject { put("contentLength", fileSize) }
            )
        val (start, end) = range
        val length = end - start + 1
        call.response.headers.append(HttpHeaders.ContentRange, "bytes $start-$end/$fileSize")
        if (!forceChunked) call.response.headers.append(HttpHeaders.ContentLength, length.toString())
        call.response.status(HttpStatusCode.PartialContent)
        call.respondBytesWriter(contentType = ContentType.parse(media.contentType)) {
            streamBytes(file, this, start, length, interruptAfter)
        }
    } else {
        if (!forceChunked) call.response.headers.append(HttpHeaders.ContentLength, fileSize.toString())
        call.response.status(HttpStatusCode.OK)
        call.respondBytesWriter(contentType = ContentType.parse(media.contentType)) {
            streamBytes(file, this, 0, fileSize, interruptAfter)
        }
    }
}

private suspend fun streamBytes(
    file: java.io.File,
    channel: ByteWriteChannel,
    offset: Long,
    length: Long,
    interruptAfter: Long?
) {
    file.inputStream().use { stream ->
        stream.skip(offset)
        val buf = ByteArray(65536)
        var remaining = length
        var totalWritten = 0L
        while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val read = stream.read(buf, 0, toRead)
            if (read == -1) break
            if (interruptAfter != null && totalWritten + read > interruptAfter) {
                val allowed = (interruptAfter - totalWritten).toInt()
                if (allowed > 0) channel.writeFully(buf, 0, allowed)
                channel.flushAndClose()
                return
            }
            channel.writeFully(buf, 0, read)
            remaining -= read
            totalWritten += read
        }
    }
}

private fun parseRange(header: String, fileSize: Long): Pair<Long, Long>? {
    if (!header.startsWith("bytes=")) return null
    val spec = header.removePrefix("bytes=").trim()
    return when {
        spec.startsWith("-") -> {
            val suffix = spec.drop(1).toLongOrNull() ?: return null
            val start = fileSize - suffix
            if (start < 0) return null
            Pair(start, fileSize - 1)
        }
        spec.endsWith("-") -> {
            val start = spec.dropLast(1).toLongOrNull() ?: return null
            if (start >= fileSize) return null
            Pair(start, fileSize - 1)
        }
        else -> {
            val parts = spec.split("-")
            if (parts.size != 2) return null
            val start = parts[0].toLongOrNull() ?: return null
            val end = parts[1].toLongOrNull() ?: return null
            if (start > end || end >= fileSize) return null
            Pair(start, end)
        }
    }
}

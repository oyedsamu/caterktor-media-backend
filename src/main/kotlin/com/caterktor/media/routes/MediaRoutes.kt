package com.caterktor.media.routes

import com.caterktor.media.models.*
import com.caterktor.media.plugins.ApiException
import com.caterktor.media.plugins.requireBearerAuth
import com.caterktor.media.services.MediaStore
import com.caterktor.media.services.ProcessingService
import com.caterktor.media.util.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

@Serializable data class UploadResponse(val data: UploadResponseData, val meta: ApiMeta)
@Serializable data class MediaResponse(val data: MediaObjectDto, val meta: ApiMeta)

fun Route.mediaRoutes(processingService: ProcessingService, maxUploadBytes: Long) {
    post("/v1/media") {
        call.requireBearerAuth()

        var uploadedFile: File? = null
        var metadata: UploadMetadata? = null
        var uploadedContentType = "application/octet-stream"
        var totalBytes = 0L
        val digest = MessageDigest.getInstance("SHA-256")

        val tmpDir = Files.createTempDirectory("caterktor-upload").toFile()
        val tmpFile = File(tmpDir, "upload.tmp")

        try {
            val multipart = call.receiveMultipart(formFieldLimit = Long.MAX_VALUE)
            multipart.forEachPart { part ->
                when {
                    part is PartData.FormItem && part.name == "metadata" -> {
                        metadata = Json.decodeFromString(part.value)
                        part.dispose()
                    }
                    part is PartData.FileItem && part.name == "file" -> {
                        uploadedContentType = part.contentType?.toString() ?: "application/octet-stream"
                        if (uploadedContentType !in ALLOWED_CONTENT_TYPES) {
                            part.dispose()
                            throw ApiException(
                                HttpStatusCode.UnprocessableEntity,
                                "UNSUPPORTED_MEDIA_TYPE",
                                "Content type not allowed",
                                buildJsonObject { put("allowedTypes", JsonArray(ALLOWED_CONTENT_TYPES.map { JsonPrimitive(it) })) }
                            )
                        }
                        tmpFile.outputStream().use { out ->
                            val buf = ByteArray(65536)
                            val channel: ByteReadChannel = part.provider()
                            while (!channel.isClosedForRead) {
                                val read = channel.readAvailable(buf)
                                if (read <= 0) continue
                                if (totalBytes + read > maxUploadBytes) {
                                    part.dispose()
                                    throw ApiException(
                                        HttpStatusCode.PayloadTooLarge,
                                        "MEDIA_TOO_LARGE",
                                        "Uploaded file exceeds maximum size",
                                        buildJsonObject { put("maxBytes", maxUploadBytes) }
                                    )
                                }
                                digest.update(buf, 0, read)
                                out.write(buf, 0, read)
                                totalBytes += read
                            }
                        }
                        uploadedFile = tmpFile
                        part.dispose()
                    }
                    else -> part.dispose()
                }
            }
        } catch (e: ApiException) {
            tmpDir.deleteRecursively()
            throw e
        }

        val meta = metadata ?: throw ApiException(
            HttpStatusCode.BadRequest, "BAD_REQUEST", "Missing metadata part"
        )
        val file = uploadedFile ?: throw ApiException(
            HttpStatusCode.BadRequest, "BAD_REQUEST", "Missing file part"
        )

        val safeFilename = try { sanitizeFilename(meta.filename) }
        catch (e: IllegalArgumentException) {
            tmpDir.deleteRecursively()
            throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", e.message ?: "Invalid filename")
        }

        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        val id = generateId("file")

        val mediaObj = MediaObject(
            id = id,
            filename = safeFilename,
            contentType = uploadedContentType,
            size = totalBytes,
            sha256 = sha,
            status = MediaStatus.uploaded,
            tempFile = file,
            category = meta.category,
            origin = meta.origin
        )
        MediaStore.media[id] = mediaObj
        processingService.startProcessing(id)

        call.respond(
            HttpStatusCode.Created,
            UploadResponse(
                UploadResponseData(id, MediaStatus.uploaded.name, totalBytes, sha),
                ApiMeta(call.requestId())
            )
        )
    }

    get("/v1/media/{mediaId}") {
        call.requireBearerAuth()
        val media = MediaStore.media[call.parameters["mediaId"]]
            ?: throw ApiException(HttpStatusCode.NotFound, "MEDIA_NOT_FOUND", "Media not found")
        call.respond(MediaResponse(media.toDto(), ApiMeta(call.requestId())))
    }

    get("/v1/media/{mediaId}/download") {
        call.requireBearerAuth()
        val media = MediaStore.media[call.parameters["mediaId"]]
            ?: throw ApiException(HttpStatusCode.NotFound, "MEDIA_NOT_FOUND", "Media not found")
        streamFileResponse(call, media)
    }

    get("/v1/media/{mediaId}/processing-stream") {
        call.requireBearerAuth()
        val media = MediaStore.media[call.parameters["mediaId"]]
            ?: throw ApiException(HttpStatusCode.NotFound, "MEDIA_NOT_FOUND", "Media not found")

        call.response.headers.append(HttpHeaders.ContentType, "text/event-stream")
        call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
        call.response.headers.append("X-Accel-Buffering", "no")

        call.respondTextWriter(contentType = ContentType.parse("text/event-stream")) {
            var eventId = 1

            fun sseEvent(event: String, data: String) {
                write("id: proc_${eventId++}\n")
                write("event: $event\n")
                write("data: $data\n\n")
                flush()
            }

            sseEvent("upload.accepted", """{"mediaId":"${media.id}","status":"uploaded"}""")

            var heartbeats = 0
            while (media.status == MediaStatus.uploaded || media.status == MediaStatus.scanning) {
                delay(1_000)
                heartbeats++
                if (heartbeats % 10 == 0) {
                    write(": heartbeat\n\n")
                    flush()
                }
            }

            if (media.status == MediaStatus.rejected) {
                sseEvent("processing.failed", """{"mediaId":"${media.id}","status":"rejected"}""")
                return@respondTextWriter
            }

            sseEvent("scan.completed", """{"mediaId":"${media.id}","status":"scanning_complete"}""")

            while (media.status == MediaStatus.processing) {
                delay(1_000)
                heartbeats++
                if (heartbeats % 10 == 0) {
                    write(": heartbeat\n\n")
                    flush()
                }
            }

            if (media.status == MediaStatus.available) {
                sseEvent("processing.completed", """{"mediaId":"${media.id}","status":"available"}""")
            } else {
                sseEvent("processing.failed", """{"mediaId":"${media.id}","status":"${media.status.name}"}""")
            }
        }
    }
}

private fun MediaObject.toDto() = MediaObjectDto(
    id = id,
    filename = filename,
    contentType = contentType,
    size = size,
    sha256 = sha256,
    status = status.name,
    category = category,
    origin = origin
)

package com.caterktor.media.routes

import com.caterktor.media.models.*
import com.caterktor.media.plugins.ApiException
import com.caterktor.media.plugins.requireBearerAuth
import com.caterktor.media.services.ExportService
import com.caterktor.media.services.MediaStore
import com.caterktor.media.util.generateId
import com.caterktor.media.util.requestId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable data class ExportCreateResponse(val data: ExportJobDto, val meta: ApiMeta)
@Serializable data class ExportStatusResponse(val data: ExportJobDto, val meta: ApiMeta)

fun Route.exportRoutes(exportService: ExportService) {
    post("/v1/exports") {
        call.requireBearerAuth()
        val req = call.receive<ExportRequest>()
        val job = ExportJob(
            id = generateId("exp"),
            type = req.type,
            from = req.from,
            to = req.to,
            status = ExportStatus.queued
        )
        MediaStore.exports[job.id] = job
        exportService.startExport(job)
        call.respond(
            HttpStatusCode.Created,
            ExportCreateResponse(job.toDto(), ApiMeta(call.requestId()))
        )
    }

    get("/v1/exports/{exportId}") {
        call.requireBearerAuth()
        val job = MediaStore.exports[call.parameters["exportId"]]
            ?: throw ApiException(HttpStatusCode.NotFound, "EXPORT_NOT_FOUND", "Export job not found")
        call.respond(ExportStatusResponse(job.toDto(), ApiMeta(call.requestId())))
    }

    get("/v1/exports/{exportId}/download") {
        call.requireBearerAuth()
        val job = MediaStore.exports[call.parameters["exportId"]]
            ?: throw ApiException(HttpStatusCode.NotFound, "EXPORT_NOT_FOUND", "Export job not found")

        if (job.status != ExportStatus.ready) {
            throw ApiException(
                HttpStatusCode.NotFound,
                "EXPORT_NOT_READY",
                "The export is not ready for download yet",
                buildJsonObject { put("status", job.status.name) }
            )
        }

        val artifactId = job.artifactId
            ?: throw ApiException(HttpStatusCode.InternalServerError, "INTERNAL_ERROR", "No artifact linked")
        val media = MediaStore.media[artifactId]
            ?: throw ApiException(HttpStatusCode.NotFound, "MEDIA_NOT_FOUND", "Artifact not found")

        streamFileResponse(call, media)
    }
}

private fun ExportJob.toDto() = ExportJobDto(
    id = id,
    status = status.name,
    downloadUrl = if (status == ExportStatus.ready) "/v1/exports/$id/download" else null,
    contentLength = if (status == ExportStatus.ready) artifactId?.let { MediaStore.media[it]?.size } else null,
    sha256 = if (status == ExportStatus.ready) artifactId?.let { MediaStore.media[it]?.sha256 } else null,
    failureReason = failureReason
)

package com.caterktor.media.models

import kotlinx.serialization.Serializable

enum class ExportStatus { queued, processing, ready, failed }
enum class MediaStatus { uploaded, scanning, processing, available, rejected }

data class ExportJob(
    val id: String,
    val type: String,
    val from: String,
    val to: String,
    var status: ExportStatus,
    val artifactId: String? = null,
    val failureReason: String? = null
)

data class MediaObject(
    val id: String,
    val filename: String,
    val contentType: String,
    val size: Long,
    val sha256: String,
    var status: MediaStatus,
    val tempFile: java.io.File,
    val category: String,
    val origin: String,
    var forceScanFail: Boolean = false
)

@Serializable data class ExportRequest(val type: String, val from: String, val to: String)
@Serializable data class UploadMetadata(val category: String, val origin: String, val filename: String)

@Serializable
data class ApiMeta(val requestId: String)

@Serializable
data class ApiError(val code: String, val message: String, val details: kotlinx.serialization.json.JsonObject)

@Serializable
data class ErrorEnvelope(val error: ApiError, val meta: ApiMeta)

@Serializable
data class ExportJobDto(
    val id: String,
    val status: String,
    val downloadUrl: String? = null,
    val contentLength: Long? = null,
    val sha256: String? = null,
    val failureReason: String? = null
)

@Serializable
data class MediaObjectDto(
    val id: String,
    val filename: String,
    val contentType: String,
    val size: Long,
    val sha256: String,
    val status: String,
    val category: String,
    val origin: String
)

@Serializable
data class UploadResponseData(
    val id: String,
    val status: String,
    val size: Long,
    val sha256: String
)

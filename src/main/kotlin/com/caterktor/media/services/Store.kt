package com.caterktor.media.services

import com.caterktor.media.models.*
import com.caterktor.media.util.generateId
import com.caterktor.media.util.sha256Hex
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

object MediaStore {
    private val log = LoggerFactory.getLogger(MediaStore::class.java)
    val media = ConcurrentHashMap<String, MediaObject>()
    val exports = ConcurrentHashMap<String, ExportJob>()

    fun seedData() {
        val tmpDir = Files.createTempDirectory("caterktor-seed").toFile()

        fun seedMedia(id: String, filename: String, contentType: String, size: Int, seed: Int): MediaObject {
            val file = File(tmpDir, filename)
            val bytes = ByteArray(size) { i -> ((i * seed + seed) % 256).toByte() }
            file.writeBytes(bytes)
            return MediaObject(
                id = id,
                filename = filename,
                contentType = contentType,
                size = file.length(),
                sha256 = file.sha256Hex(),
                status = MediaStatus.available,
                tempFile = file,
                category = "seed",
                origin = "server"
            ).also { media[id] = it }
        }

        seedMedia("file_seed_1", "sample.pdf", "application/pdf", 50 * 1024, 7)
        seedMedia("file_seed_2", "large_blob.bin", "application/octet-stream", 5 * 1024 * 1024, 13)
        seedMedia("file_seed_3", "photo.jpg", "image/jpeg", 120 * 1024, 31)

        fun seedExport(id: String, artifactId: String): ExportJob {
            val artifact = media[artifactId]!!
            return ExportJob(
                id = id,
                type = "seed_export",
                from = "2026-01-01",
                to = "2026-01-31",
                status = ExportStatus.ready,
                artifactId = artifactId
            ).also { exports[id] = it }
        }

        seedExport("exp_seed_1", "file_seed_1")
        seedExport("exp_seed_2", "file_seed_2")

        log.info("Seed data loaded: ${media.size} media, ${exports.size} exports")
    }

    fun reset() {
        media.keys.filter { !it.startsWith("file_seed_") }.forEach { media.remove(it) }
        exports.keys.filter { !it.startsWith("exp_seed_") }.forEach { exports.remove(it) }
    }
}

class ProcessingService(private val scope: CoroutineScope) {
    private val log = LoggerFactory.getLogger(ProcessingService::class.java)

    fun startProcessing(mediaId: String) {
        scope.launch {
            val obj = MediaStore.media[mediaId] ?: return@launch
            obj.status = MediaStatus.scanning
            delay(2_000)

            if (obj.forceScanFail) {
                obj.status = MediaStatus.rejected
                obj.forceScanFail = false
                log.info("mediaId=$mediaId scan forced-fail → rejected")
                return@launch
            }

            obj.status = MediaStatus.processing
            delay(3_000)
            obj.status = MediaStatus.available
            log.info("mediaId=$mediaId processing complete → available")
        }
    }
}

class ExportService(private val scope: CoroutineScope) {
    private val log = LoggerFactory.getLogger(ExportService::class.java)

    fun startExport(job: ExportJob) {
        scope.launch {
            delay(2_000)
            job.status = ExportStatus.processing
            delay(5_000)
            job.status = ExportStatus.ready
            log.info("exportId=${job.id} → ready")
        }
    }
}

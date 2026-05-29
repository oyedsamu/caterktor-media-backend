package com.caterktor.media

import com.caterktor.media.plugins.*
import com.caterktor.media.routes.*
import com.caterktor.media.services.ExportService
import com.caterktor.media.services.MediaStore
import com.caterktor.media.services.ProcessingService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(CIO, port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    val debugEnabled = System.getenv("DEBUG_ENDPOINTS")?.lowercase() != "false"
    val corsPermissive = System.getenv("CORS_PERMISSIVE")?.lowercase() != "false"
    val maxUploadBytes = System.getenv("MAX_UPLOAD_BYTES")?.toLongOrNull() ?: 2_147_483_648L

    val appScope = CoroutineScope(SupervisorJob())
    val processingService = ProcessingService(appScope)
    val exportService = ExportService(appScope)

    MediaStore.seedData()

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    if (corsPermissive) {
        install(CORS) {
            anyHost()
            allowHeader("Authorization")
            allowHeader("Content-Type")
            allowHeader("X-Request-ID")
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Patch)
        }
    }

    install(DefaultHeaders)
    configureRequestTracking()
    configureErrorHandling()

    routing {
        // Swagger UI (CDN-backed, serves spec from resources)
        get("/openapi.yaml") {
            val spec = javaClass.classLoader
                .getResourceAsStream("openapi/documentation.yaml")
                ?.readBytes()?.toString(Charsets.UTF_8)
                ?: ""
            call.respondText(spec, ContentType.parse("application/yaml"))
        }
        get("/docs") {
            call.respondText(
                contentType = ContentType.Text.Html,
                text = """<!DOCTYPE html>
<html><head>
  <title>CaterKtor Media Backend</title>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css">
</head><body>
<div id="swagger-ui"></div>
<script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
<script>
window.onload=()=>SwaggerUIBundle({
  url:"/openapi.yaml",dom_id:"#swagger-ui",
  presets:[SwaggerUIBundle.presets.apis,SwaggerUIBundle.SwaggerUIStandalonePreset],
  layout:"StandaloneLayout"
})
</script>
</body></html>"""
            )
        }

        healthRoutes(debugEnabled, maxUploadBytes)
        exportRoutes(exportService)
        mediaRoutes(processingService, maxUploadBytes)
        if (debugEnabled) debugRoutes()
    }
}

# CaterKtor Media Backend — API Reference

Base URL: `http://localhost:8080` (local) or your Railway URL.

**Auth:** All endpoints except `/v1/health` and `/v1/capabilities` require:
```
Authorization: Bearer <any-non-empty-token>
```

**Every response includes:**
- `X-Request-ID` header (echoed from client or generated)
- `X-Trace-ID` header (generated per request)

---

## Success Envelope

```json
{
  "data": { ... },
  "meta": { "requestId": "req_abc123" }
}
```

## Error Envelope

```json
{
  "error": {
    "code": "MEDIA_TOO_LARGE",
    "message": "Uploaded file exceeds maximum size",
    "details": { "maxBytes": 2147483648 }
  },
  "meta": { "requestId": "req_abc123" }
}
```

### Error Codes

| Code | HTTP | Details |
|------|------|---------|
| `MEDIA_TOO_LARGE` | 413 | `maxBytes` |
| `UNSUPPORTED_MEDIA_TYPE` | 422 | `allowedTypes` |
| `EXPORT_NOT_READY` | 404 | `status` |
| `EXPORT_NOT_FOUND` | 404 | — |
| `MEDIA_NOT_FOUND` | 404 | — |
| `RANGE_NOT_SATISFIABLE` | 416 | `contentLength` |
| `PROCESSING_FAILED` | — | `reason` |
| `UNAUTHORIZED` | 401 | — |

---

## Health

### `GET /v1/health`
No auth required.

**Response 200:**
```json
{ "data": { "status": "ok", "version": "1.0.0" }, "meta": { "requestId": "req_..." } }
```

### `GET /v1/capabilities`
No auth required. Returns server feature flags including `maxUploadBytes`, `allowedContentTypes`, `rangeRequests`, `sse`, `debugEndpoints`.

---

## Export Jobs

### `POST /v1/exports`
Create an export job. Transitions `queued → processing` after 2s, `processing → ready` after 5s.

**Request:**
```json
{ "type": "transactions_csv", "from": "2026-04-01", "to": "2026-04-30" }
```

**Response 201:**
```json
{
  "data": { "id": "exp_abc123", "status": "queued" },
  "meta": { "requestId": "req_..." }
}
```

### `GET /v1/exports/{exportId}`
Poll export status.

**Response 200 (when ready):**
```json
{
  "data": {
    "id": "exp_abc123",
    "status": "ready",
    "downloadUrl": "/v1/exports/exp_abc123/download",
    "contentLength": 51200,
    "sha256": "a1b2c3..."
  },
  "meta": { "requestId": "req_..." }
}
```

**Response 404 (not found):**
```json
{ "error": { "code": "EXPORT_NOT_FOUND", "message": "Export job not found", "details": {} }, "meta": { ... } }
```

### `GET /v1/exports/{exportId}/download`
Stream the export artifact. Only available when `status=ready`.

**Headers on success:**
```
Content-Type: application/octet-stream
Content-Length: 51200
Accept-Ranges: bytes
ETag: "a1b2c3d4e5f6a7b8"
Content-Disposition: attachment; filename="sample.pdf"
```

**Range request:**
```
GET /v1/exports/exp_seed_1/download
Range: bytes=0-1023
```
Response: `206 Partial Content` with `Content-Range: bytes 0-1023/51200`.

**Response 404 (not ready):**
```json
{
  "error": { "code": "EXPORT_NOT_READY", "message": "The export is not ready for download yet", "details": { "status": "processing" } },
  "meta": { ... }
}
```

---

## Media Upload & Download

### `POST /v1/media`
Multipart upload. Parts: `file` (binary) + `metadata` (JSON form field).

```
POST /v1/media
Authorization: Bearer mytoken
Content-Type: multipart/form-data; boundary=---boundary

-----boundary
Content-Disposition: form-data; name="metadata"

{"category":"report","origin":"mobile","filename":"report.pdf"}
-----boundary
Content-Disposition: form-data; name="file"; filename="report.pdf"
Content-Type: application/pdf

<binary bytes>
-----boundary--
```

**Allowed content types:** `application/pdf`, `image/jpeg`, `image/png`, `application/octet-stream`
**Max size:** 2 GB (configurable via `MAX_UPLOAD_BYTES` env var)

**Response 201:**
```json
{
  "data": { "id": "file_xyz789", "status": "uploaded", "size": 8342212, "sha256": "abc..." },
  "meta": { "requestId": "req_..." }
}
```

### `GET /v1/media/{mediaId}`
Get media metadata and current processing state.

**Media states:** `uploaded → scanning → processing → available | rejected`

**Response 200:**
```json
{
  "data": {
    "id": "file_xyz789",
    "filename": "report.pdf",
    "contentType": "application/pdf",
    "size": 8342212,
    "sha256": "abc123...",
    "status": "available",
    "category": "report",
    "origin": "mobile"
  },
  "meta": { "requestId": "req_..." }
}
```

### `GET /v1/media/{mediaId}/download`
Stream file bytes. Supports `Range` and `If-Range`.

Same headers as export download. Returns `206` for valid range, `416` for invalid range.

**Invalid range example:**
```
GET /v1/media/file_seed_1/download
Range: bytes=99999999-
```
Response `416`:
```json
{ "error": { "code": "RANGE_NOT_SATISFIABLE", "message": "Range not satisfiable", "details": { "contentLength": 51200 } }, "meta": { ... } }
```

### `GET /v1/media/{mediaId}/processing-stream`
SSE stream. `Content-Type: text/event-stream`. Stream closes after terminal event.

**Event sequence:**
```
id: proc_1
event: upload.accepted
data: {"mediaId":"file_xyz789","status":"uploaded"}

: heartbeat

id: proc_2
event: scan.completed
data: {"mediaId":"file_xyz789","status":"scanning_complete"}

id: proc_3
event: processing.completed
data: {"mediaId":"file_xyz789","status":"available"}
```

**On scan failure:**
```
id: proc_2
event: processing.failed
data: {"mediaId":"file_xyz789","status":"rejected"}
```

---

## Debug Endpoints
Enabled when `DEBUG_ENDPOINTS=true` (default). Used for validating CaterKtor client behavior.

### `GET /v1/debug/status/{statusCode}`
Returns any HTTP status code, e.g. `/v1/debug/status/503`.

### `GET /v1/debug/delay?ms=2000`
Delays response by `ms` milliseconds (max 30000).

### `GET /v1/debug/chunked/{mediaId}`
Streams the media file **without** `Content-Length` (chunked transfer encoding). Validates client streaming without known length.

### `GET /v1/debug/interrupt/{mediaId}?after=1024`
Streams `after` bytes then closes the connection mid-transfer. Validates client retry/resume behavior.

### `POST /v1/debug/fail-scan/{mediaId}`
Flags the media item so its next scan phase will be rejected (`status → rejected`). Use before `GET /v1/media/{mediaId}/processing-stream` to observe a `processing.failed` event.

### `POST /v1/debug/reset`
Clears all uploaded media and exports created since startup. Seed data (`file_seed_*`, `exp_seed_*`) is preserved.

---

## Seed Data (available on cold start)

| ID | Content | Size | Type |
|----|---------|------|------|
| `file_seed_1` | Deterministic PDF-like bytes | 50 KB | `application/pdf` |
| `file_seed_2` | Deterministic binary blob | 5 MB | `application/octet-stream` |
| `file_seed_3` | Deterministic JPEG stub | ~120 KB | `image/jpeg` |
| `exp_seed_1` | Export pointing to `file_seed_1` | `ready` | — |
| `exp_seed_2` | Export pointing to `file_seed_2` | `ready` | — |

---

## Swagger UI
`GET /docs` — interactive API explorer served by Swagger UI.

---

## Environment Variables

| Var | Default | Description |
|-----|---------|-------------|
| `PORT` | `8080` | Server port (Railway injects) |
| `DEBUG_ENDPOINTS` | `true` | Enable `/v1/debug/*` routes |
| `CORS_PERMISSIVE` | `true` | Allow all origins |
| `MAX_UPLOAD_BYTES` | `2147483648` | Max upload size in bytes |

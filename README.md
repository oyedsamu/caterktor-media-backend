# CaterKtor Media Backend

Standalone Ktor/JVM server that acts as the file-transfer showcase backend for
[CaterKtor](https://github.com/oyedsamu/caterktor) — the Kotlin Multiplatform
networking library.

## Quick start

```bash
./gradlew run
# Server starts on http://localhost:8080
```

**Swagger UI:** http://localhost:8080/docs  
**Health check:** http://localhost:8080/v1/health

## Auth

Pass any non-empty bearer token:
```
Authorization: Bearer mytoken
```
Token validation is simplified: any non-empty bearer string is accepted. Full
auth lives in `caterktor-orders-backend`.

## API docs

See [docs/api.md](docs/api.md) for the complete endpoint reference including
SSE event sequences and Range request examples.

## Configuration (env vars)

| Var | Default | Description |
|-----|---------|-------------|
| `PORT` | `8080` | HTTP port |
| `DEBUG_ENDPOINTS` | `true` | Enable `/v1/debug/*` routes |
| `CORS_PERMISSIVE` | `true` | Allow all origins |
| `MAX_UPLOAD_BYTES` | `2147483648` | Max upload in bytes (2 GB) |

## Docker

```bash
docker build -t caterktor-media .
docker run -p 8080:8080 caterktor-media
```

## Railway

Deploy via `railway up`. `railway.json` configures the `caterktor-media` service
with `/v1/health` as the health check path. The ephemeral filesystem is fine for
this showcase — uploads don't persist across deploys.

## Seed data

Three media objects and two export jobs in `ready`/`available` state are loaded at startup:

| ID | Size | Status |
|----|------|--------|
| `file_seed_1` | 50 KB PDF | available |
| `file_seed_2` | 5 MB binary | available |
| `file_seed_3` | 120 KB JPEG | available |
| `exp_seed_1` | links file_seed_1 | ready |
| `exp_seed_2` | links file_seed_2 | ready |

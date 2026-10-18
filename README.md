# hermes-audiobooks-api

Audiobooks API for the olympus lab: manage audiobooks and chapters, upload covers and audio to S3, stream with HTTP Range, and track listening progress.

Part of the [olympus-cluster](https://github.com/olympus-cluster) lab. Runs on the local
[platform](https://github.com/olympus-cluster/platform) kind cluster.

| | |
|---|---|
| Stack | Spring Boot 4.1 · Java 21 · PostgreSQL (CloudNativePG) · S3 (RustFS) |
| URL (PC) | http://hermes.localtest.me |
| URL (Android emulator) | http://hermes.10.0.2.2.nip.io |
| Kubernetes | namespace `apps`, Deployment `hermes-audiobooks-api` |
| Config | ConfigMap `hermes-audiobooks-api-config` + Secret `hermes-audiobooks-api` (created by `platform/scripts/secrets.ps1`) |

## API (`/api/v1`)

Interactive docs: http://hermes.localtest.me/swagger-ui.html · OpenAPI spec: `/v3/api-docs`

| Method & path | What |
|---|---|
| `GET /audiobooks?q=&page=&size=` | Search by title/author (case-insensitive), paged |
| `POST /audiobooks` | Create; 201 with `Location` |
| `GET/PUT/PATCH/DELETE /audiobooks/{id}` | Detail includes chapters, `coverUrl`, `totalDurationSeconds`. DELETE (204) also removes the files |
| `PUT /audiobooks/{id}/cover` · `GET /audiobooks/{id}/cover` | Upload (multipart `file`: jpeg/png/webp; 204) / fetch the cover |
| `POST /audiobooks/{id}/chapters` | Multipart `file` (mp3, m4a/m4b, aac, ogg, flac, wav) + `title`, optional `position`, `durationSeconds`; 201 with `Location` |
| `PUT/PATCH/DELETE /audiobooks/{id}/chapters/{chapterId}` | Edit or remove a chapter |
| `GET/HEAD /audiobooks/{id}/chapters/{chapterId}/audio` | Stream the audio. Honours `Range` (206 / 416), so players can seek |
| `GET/PUT /audiobooks/{id}/progress` | Listening position `{chapterId, positionSeconds}` (single user until auth is added). GET is 204 until something is saved |

**Updates.** `PUT` replaces: optional audiobook fields left out (or null) are cleared. `PATCH` changes only the
fields present and non-null, so it is what a client should use to edit single fields (it cannot clear a field; use
`PUT` for that). Exception: a chapter `PUT` without `durationSeconds` keeps the stored duration, because the duration
describes the audio file rather than editable metadata.

**Validation.** `positionSeconds` must be `>= 0` and, when the chapter's `durationSeconds` is known, not larger than
it (400 otherwise). Duplicate chapter positions are 409.

**HTTP caching.** Chapter audio and covers send `ETag` and `Last-Modified` and answer `If-None-Match` /
`If-Modified-Since` with 304. Audio also honours `If-Range` (a range request for a changed object gets the full body).
- Audio: `Cache-Control: max-age=31536000, immutable`. A chapter's bytes never change under its URL (uploading again
  creates a new chapter id).
- Covers: `coverUrl` carries the cover version (`.../cover?v=<epoch millis of the last cover upload>`), so it changes
  whenever the cover is replaced and URL-keyed image caches (Coil, Glide) load the new image. With the current `v` the
  response is `max-age=31536000, immutable`; without it, or with an old one, it is `no-cache` (revalidated via ETag).

**Status codes** are documented per operation in the OpenAPI spec, so generated clients (e.g. Retrofit/Kotlin) know
them: 200, 201 + `Location`, 204, 206, 304, 400, 404, 409, 413, 415, 416, 502. Every operation has a stable
`operationId`. Errors are RFC 9457 `application/problem+json` (`ProblemDetail` schema; validation errors add
`errors: [{field, message}]`, unsupported uploads add `supported`). Health: `/actuator/health/liveness`,
`/actuator/health/readiness` (db + s3).

**Storage:** metadata in PostgreSQL (database `hermes`, Flyway migrations in `src/main/resources/db/migration`),
files in S3 (RustFS bucket `hermes-audio`, keys `audiobooks/{id}/cover` and `audiobooks/{id}/chapters/{chapterId}`).

## Import an audiobook folder

```powershell
powershell -ExecutionPolicy Bypass -File scripts\import-audiobook.ps1 `
  -Folder "C:\path\to\My Book" -Title "My Book" -Author "Author Name" -Narrator "Narrator" -Language en
```

Files are uploaded in name order; `NN - Chapter title.mp3` becomes chapter NN. MP3 durations are read from the files.
Audio files never belong in this repository.

## Configuration (env vars)

| Variable | Default | |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | – | PostgreSQL |
| `S3_ENDPOINT`, `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` | –, `us-east-1`, `hermes-audio` | S3 / RustFS |
| `HERMES_MAX_UPLOAD` | `1GB` | Max upload size |
| `HERMES_STREAM_TIMEOUT` | `6h` | Max duration of one stream |
| `HERMES_API_DOCS_ENABLED` | `true` | Swagger / OpenAPI |

On the cluster these come from Secret `hermes-audiobooks-api`, created by `platform/scripts/secrets.ps1`.

## Build & test

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'   # if JAVA_HOME points elsewhere
.\mvnw.cmd verify     # unit + integration tests (Testcontainers: postgres:18 + rustfs; needs Docker)
```

## Run it

Prerequisite: the platform is up (`platform\scripts\up.ps1`).

```powershell
tilt up                      # build -> push to localhost:5001 -> deploy -> live updates (UI: http://localhost:10350)
```

Without Tilt:

```powershell
docker build -t localhost:5001/hermes-audiobooks-api:dev .
docker push localhost:5001/hermes-audiobooks-api:dev
kubectl apply -k k8s/overlays/local
kubectl -n apps rollout restart deploy/hermes-audiobooks-api
kubectl -n apps logs -f deploy/hermes-audiobooks-api
```

## Layout

| Path | What |
|---|---|
| `k8s/base` | Deployment, Service, Ingress, ConfigMap |
| `k8s/overlays/local` | Local image (`localhost:5001/hermes-audiobooks-api:dev`) |
| `Tiltfile` | Dev loop |
| `Dockerfile` | Multi-stage, non-root image |
| `.github/workflows/ci.yml` | Tests + image build |

# Agent guide — HTTP Ingestion Service

## Testing policy (mandatory)

Every new feature or behavior change must include:

| Layer | Location | Runner |
|-------|----------|--------|
| Unit | `http-ingestion-core`, sink modules | Maven Surefire |
| Java E2E | `http-ingestion-boot/src/test/java` | Testcontainers + Podman/Docker |
| UI E2E | `http-ingestion-admin-ui/e2e/*.spec.ts` | Playwright + `global-setup.ts` stack |

Update `docs/testing/test-matrix.md` when adding scenarios.

## Commands

```powershell
# CI backend gate
.\mvnw-jdk21.ps1 -pl http-ingestion-boot -am test `
  -Dtest=HttpIngestionE2ETest,OpenApiImportE2ETest,OpenApiPaginationInferenceTest,JiaduSignVerifierTest,RequestBodyComposerTest,TransformPipelineTest,IncrementalSupportTest,RuntimeConfigParserMonotonicIdTest,RuntimeConfigParserRollingWindowTest,PostgreSql*,KafkaSinkE2ETest,OpenApiImportServiceTest `
  -Dsurefire.failIfNoSpecifiedTests=false

# Playwright (build jar first)
.\mvnw-jdk21.ps1 -pl http-ingestion-boot -am package -DskipTests
cd http-ingestion-admin-ui
$env:E2E_SERVER_PORT="18080"
npm run test:e2e
```

Requires **Podman or Docker** on PATH.

## E2E mock endpoints (`spring.profiles.active=e2e`)

- `POST /mock/_test/reset` — reset integration mock data
- `GET /mock/e2e/kafka-users` — static JSON array for Kafka pull UI tests
- `GET /mock/_test/kafka/count?topic=` — Kafka message count (when `EXTERNAL_KAFKA_BOOTSTRAP_SERVERS` set)

## Module map

- `http-ingestion-core` — engine, sync, templates
- `http-ingestion-sink-pg` / `http-ingestion-sink-kafka` — sink implementations
- `http-ingestion-api` — REST + mock sources
- `http-ingestion-boot` — runnable jar + E2E tests

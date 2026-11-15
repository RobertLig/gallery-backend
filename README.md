# Gallery backend

Quarkus REST API for the gallery application. The backend and frontend are maintained as separate projects and Git repositories.

## Local development

Java 21, Maven, and Docker are required. The dev profile uses Quarkus Dev Services for PostgreSQL and S3-compatible local storage.

```bash
mvn quarkus:dev
```

The local `.env` is intentionally ignored by Git. Copy `.env.example` when setting up another local environment. Production credentials and JWT key files must be supplied through environment variables and secret files, never committed.

## Initial API

- `GET /api/v1/health` — public liveness check.
- Errors use a stable JSON shape and messages follow `Accept-Language` (`en` or `pl`).

## Planned backend layout

```text
src/main/java/com/robert/gallery/
  api/             REST resources and request/response contracts
  api/error/       API error format, domain exceptions, exception mappers
  auth/            Google sign-in, access JWT, refresh-cookie lifecycle
  config/          application/security configuration
  domain/          entities, enums, and domain rules
  i18n/            Accept-Language resolution and message lookup
  mapper/          entity-to-DTO mapping
  repository/      Panache persistence queries
  service/         use cases and transaction boundaries
  storage/         Supabase S3 presigning and object cleanup
src/main/resources/
  db/migration/    versioned Flyway migrations
  i18n/            English and Polish server messages
src/test/java/     endpoint, service, and persistence tests
```

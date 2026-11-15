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
- `POST /api/v1/auth/google` — exchange a Google ID token for a short-lived access JWT and an HttpOnly refresh cookie.
- `POST /api/v1/auth/refresh` and `POST /api/v1/auth/logout` — rotate or revoke the refresh session.
- `GET/PATCH /api/v1/users/me` — read the authenticated user's profile or update their optional phone number.
- `GET /api/v1/cart`, `POST /api/v1/cart/items`, and `DELETE /api/v1/cart/items/{paintingId}` — authenticated shopping-cart operations.
- `POST /api/v1/checkout/cart` — create a Stripe Checkout session for available cart items.
- `POST /api/v1/auctions/{id}/checkout` — winning bidder creates a Stripe Checkout session after the auction ends.
- `GET /api/v1/checkout/orders/{id}` — authenticated owner checks payment status.
- `GET /api/v1/checkout/orders/pending` and `POST /api/v1/checkout/orders/{id}/session` — list pending orders and safely retry session creation.
- `POST /api/v1/payments/stripe/webhook` — Stripe-signed payment and expiration events.
- `GET /api/v1/paintings` and `GET /api/v1/paintings/{id}` — public, paginated catalog (drafts excluded).
- `/api/v1/admin/paintings` — admin-only painting CRUD, filtering, and photo management.
- `GET /api/v1/auctions` and `GET /api/v1/auctions/{id}` — public active-auction list and auction details.
- `POST /api/v1/admin/paintings/{id}/auction` — admin starts a one-hour auction at the painting's listed price.
- `POST /api/v1/auctions/{id}/bids` — authenticated users place bids above the current price.
- Errors use a stable JSON shape and messages follow `Accept-Language` (`en` or `pl`).

Set `GOOGLE_CLIENT_ID` to the same OAuth client ID used by the frontend and `ADMIN_EMAIL` to the verified Google account that should receive the `ADMIN` role. New users otherwise receive `USER`. The server validates Google ID tokens against Google's signing keys and the configured audience. Access tokens last 15 minutes; refresh tokens are random, rotated on use, and stored only as SHA-256 hashes.

Production refresh cookies use `SameSite=None; Secure` by default so the Cloudflare Pages frontend can call the Render API with credentials. Set `CORS_ORIGINS` to the exact frontend origin. Production also requires the configured JWT signing and verification key files.

Painting prices are stored as integer minor units (`priceMinor`) in the single configured `GALLERY_CURRENCY` (default `PLN`). The API returns both `priceMinor` and the ISO currency code. Catalog filters include `q`, `medium`, `minPrice`, `maxPrice`, `minYear`, `maxYear`, `status`, `sort`, `page`, and `size`; sorting options are `recent`, `oldest`, `price-asc`, and `price-desc`.

Only admins can request photo uploads. The API creates an upload intent and returns a five-minute presigned PUT URL for JPEG, PNG, or WebP files up to 5 MB (5,000,000 bytes). The browser uploads directly to the private S3 bucket, then calls the photo completion endpoint. Public painting responses use 15-minute presigned GET URLs. Each painting supports up to 10 ordered photos; photos are deleted from S3 when removed, and painting deletion cleans up all associated objects. Configure the Supabase bucket's CORS policy to allow the frontend origin and browser `GET`/`PUT` requests with the `Content-Type` header.

Auctions last exactly one hour and start at the painting's listed price. The API serializes bids per auction and only accepts a bid above the current amount; the active state is determined from the server's UTC clock. An active auction prevents that painting from being edited or deleted. After an auction with bids ends, the painting is held for its winner for 24 hours; an unpaid painting becomes available for relisting when this deadline passes.

The shopping cart is persistent per authenticated user, contains unique painting entries, and is not a reservation until checkout. Adding a painting requires it to be available and not in an active auction. If a painting later becomes unavailable, it remains in the cart marked `purchasable: false`; the cart total includes only currently purchasable items. Removing a painting is idempotent.

Checkout uses Stripe-hosted payment pages, with PLN amounts sent from server-side price snapshots. Configure `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` in `.env` and production secrets; never expose them to the frontend. Set the Stripe webhook endpoint to `/api/v1/payments/stripe/webhook` and subscribe to `checkout.session.completed`, `checkout.session.expired`, `checkout.session.async_payment_succeeded`, and `checkout.session.async_payment_failed`. The Checkout Session offers card, BLIK, and Przelewy24 when enabled for the Stripe account. Cart checkout sessions last 31 minutes (Stripe requires at least 30 minutes); auction winners must complete payment within 24 hours after auction end. Paintings are reserved until Stripe confirms payment or session expiry, then become sold or available. When an asynchronous payment method reports that payment is pending, inventory remains reserved for up to 48 hours while Stripe completes or fails processing. A scheduled reconciler queries Stripe for expired sessions if webhooks are delayed. Return URLs point to `/checkout/success` (or the auction's corresponding success path); the frontend must check order status through the authenticated API and must not treat a redirect as proof of payment.

Stripe charges transaction fees for completed payments; test mode is free. Production checkout requires a verified Stripe account and enabled payment methods. Stripe payouts can be settled to an eligible PKO Bank Polski account.

For local development, create a throwaway RSA key pair under ignored `target/` before starting Quarkus:

```bash
mkdir -p target
openssl genpkey -algorithm RSA -out target/jwt-private.pem -pkeyopt rsa_keygen_bits:2048
openssl pkey -in target/jwt-private.pem -pubout -out target/jwt-public.pem
```

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

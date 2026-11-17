# Gallery Backend

Quarkus REST API for the Artistry Gallery application. The backend and frontend are maintained as separate projects and Git repositories.

## Features

- Google OAuth authentication with JWT and refresh token cookies
- User management with roles (USER, ADMIN), phone numbers, and locale
- Painting CRUD with photo management via Supabase S3
- Auction system (1-hour auctions, bidding)
- Shopping cart functionality
- Stripe checkout integration (cards, BLIK, Przelewy24)
- i18n support (English/Polish) based on Accept-Language header
- Exception mappers with translated error messages

## Local Development

### Prerequisites

- Java 21
- Maven
- Docker (for Dev Services)

### Running in Development

```bash
mvn quarkus:dev
```

The dev profile uses Quarkus Dev Services for:
- PostgreSQL (automatically started in Docker)
- Localstack (S3-compatible storage for testing)

### Local Environment Variables

Create a `.env` file in the project root (gitignored):

```bash
# Google OAuth
GOOGLE_CLIENT_ID=995827113545-eljgj7lpk0jannjvieacsgc932r1kh9h.apps.googleusercontent.com

# Admin configuration
ADMIN_EMAIL=your-admin-email@gmail.com

# CORS
CORS_ORIGINS=http://localhost:5173
FRONTEND_BASE_URL=http://localhost:5173

# Database (Dev Services uses defaults, these are for custom setup)
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/gallery
QUARKUS_DATASOURCE_USERNAME=quarkus
QUARKUS_DATASOURCE_PASSWORD=quarkus

# S3 (Dev Services uses Localstack, these are for custom setup)
SUPABASE_S3_BUCKET=gallery-bucket
SUPABASE_S3_ENDPOINT=http://localhost:4566
SUPABASE_S3_REGION=us-east-1
SUPABASE_S3_ACCESS_KEY_ID=test
SUPABASE_S3_SECRET_ACCESS_KEY=test

# Stripe (test mode)
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Collection address
COLLECTION_ADDRESS="Your address here"

# JWT (dev mode generates keys in target/)
JWT_SIGNING_KEY_LOCATION=target/jwt-private.pem
JWT_VERIFY_PUBLIC_KEY_LOCATION=target/jwt-public.pem
```

### Generate JWT Keys for Local Development

```bash
mkdir -p target
openssl genpkey -algorithm RSA -out target/jwt-private.pem -pkeyopt rsa_keygen_bits:2048
openssl pkey -in target/jwt-private.pem -pubout -out target/jwt-public.pem
```

## Production Deployment (Render)

### Environment Variables

Set these in your Render dashboard:

```bash
# CORS (replace with your Cloudflare Pages URL)
CORS_ORIGINS=https://your-site.pages.dev
FRONTEND_BASE_URL=https://your-site.pages.dev

# Google OAuth
GOOGLE_CLIENT_ID=995827113545-eljgj7lpk0jannjvieacsgc932r1kh9h.apps.googleusercontent.com

# Admin configuration
ADMIN_EMAIL=your-admin-email@gmail.com

# Neon PostgreSQL
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://your-neon-host/your-database
QUARKUS_DATASOURCE_USERNAME=your-username
QUARKUS_DATASOURCE_PASSWORD=your-password

# Supabase S3
SUPABASE_S3_BUCKET=gallery-bucket
SUPABASE_S3_ENDPOINT=https://your-project.supabase.co
SUPABASE_S3_REGION=us-east-1
SUPABASE_S3_ACCESS_KEY_ID=your-access-key
SUPABASE_S3_SECRET_ACCESS_KEY=your-secret-key

# Stripe (production keys)
STRIPE_SECRET_KEY=sk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Collection address
COLLECTION_ADDRESS="Your address here"

# JWT keys (uploaded as secret files)
JWT_SIGNING_KEY_LOCATION=/etc/secrets/jwt-private.pem
JWT_VERIFY_PUBLIC_KEY_LOCATION=/etc/secrets/jwt-public.pem
```

### Secret Files

Upload these files in Render's secret files section:

1. `jwt-private.pem` - Your RSA private key for signing JWTs
2. `jwt-public.pem` - Your RSA public key for verifying JWTs

### Generate Production JWT Keys

```bash
openssl genpkey -algorithm RSA -out jwt-private.pem -pkeyopt rsa_keygen_bits:2048
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem
```

### Google OAuth Configuration

Add your production frontend URL to Google OAuth console:
- Authorized JavaScript origins: `https://your-site.pages.dev`
- Authorized redirect URIs: `https://your-site.pages.dev`

### Stripe Configuration

1. Create a Stripe account and enable Polish Złoty (PLN)
2. Enable payment methods: Cards, BLIK, Przelewy24
3. Set webhook endpoint: `https://your-render-app.onrender.com/api/v1/payments/stripe/webhook`
4. Subscribe to events:
   - `checkout.session.completed`
   - `checkout.session.expired`
   - `checkout.session.async_payment_succeeded`
   - `checkout.session.async_payment_failed`

### Supabase Configuration

1. Create a private bucket for painting photos
2. Configure CORS policy to allow your frontend origin
3. Allow `GET` and `PUT` requests with `Content-Type` header

## API Endpoints

### Authentication
- `POST /api/v1/auth/google` - Exchange Google ID token for JWT
- `POST /api/v1/auth/refresh` - Refresh access token
- `POST /api/v1/auth/logout` - Logout

### Users
- `GET /api/v1/users/me` - Get current user profile
- `PATCH /api/v1/users/me` - Update user profile (phone)
- `GET /api/v1/contact` - Get admin contact info (authenticated only)

### Paintings
- `GET /api/v1/paintings` - List paintings (public, paginated)
- `GET /api/v1/paintings/{id}` - Get painting details (public)
- `POST /api/v1/admin/paintings` - Create painting (admin only)
- `PATCH /api/v1/admin/paintings/{id}` - Update painting (admin only)
- `DELETE /api/v1/admin/paintings/{id}` - Delete painting (admin only)
- `POST /api/v1/admin/paintings/{id}/photos/upload-intent` - Request photo upload (admin only)
- `POST /api/v1/admin/paintings/{id}/photos/{photoId}/complete` - Complete photo upload (admin only)

### Auctions
- `GET /api/v1/auctions` - List active auctions (public)
- `GET /api/v1/auctions/{id}` - Get auction details (public)
- `POST /api/v1/admin/paintings/{id}/auction` - Start auction (admin only)
- `POST /api/v1/auctions/{id}/bids` - Place bid (authenticated)
- `POST /api/v1/auctions/{id}/checkout` - Checkout winning bid (authenticated)

### Cart
- `GET /api/v1/cart` - Get cart (authenticated)
- `POST /api/v1/cart/items` - Add painting to cart (authenticated)
- `DELETE /api/v1/cart/items/{paintingId}` - Remove from cart (authenticated)
- `POST /api/v1/checkout/cart` - Checkout cart (authenticated)

### Orders
- `GET /api/v1/checkout/orders` - Get order history (authenticated)
- `GET /api/v1/checkout/orders/{id}` - Get order details (authenticated owner)
- `GET /api/v1/checkout/orders/{id}/collection-details` - Get collection address (paid orders)
- `GET /api/v1/admin/orders` - List all orders (admin only)

### Payments
- `POST /api/v1/payments/stripe/webhook` - Stripe webhook handler

## Testing

```bash
mvn test
```

## Project Structure

```
src/main/java/com/robert/gallery/
  api/             REST resources and DTOs
  api/error/       Exception mappers and error format
  auth/            Google OAuth, JWT, refresh tokens
  config/          Security and application configuration
  domain/          Entities, enums, domain logic
  i18n/            Accept-Language resolution
  mapper/          Entity-to-DTO mapping
  repository/      Panache repositories
  service/         Business logic and transactions
  storage/         S3 presigning and cleanup
src/main/resources/
  db/migration/    Flyway migrations
  i18n/            English and Polish messages
src/test/java/     Integration and unit tests
```

## Initial API

- `GET /api/v1/health` — public liveness check.
- `POST /api/v1/auth/google` — exchange a Google ID token for a short-lived access JWT and an HttpOnly refresh cookie.
- `POST /api/v1/auth/refresh` and `POST /api/v1/auth/logout` — rotate or revoke the refresh session.
- `GET/PATCH /api/v1/users/me` — read the authenticated user's profile or update their optional phone number.
- `GET /api/v1/contact` — authenticated users can get the configured gallery phone number and collection-only policy.
- `GET /api/v1/cart`, `POST /api/v1/cart/items`, and `DELETE /api/v1/cart/items/{paintingId}` — authenticated shopping-cart operations.
- `POST /api/v1/checkout/cart` — create a Stripe Checkout session for available cart items.
- `POST /api/v1/auctions/{id}/checkout` — winning bidder creates a Stripe Checkout session after the auction ends.
- `GET /api/v1/checkout/orders/{id}` — authenticated owner checks payment status.
- `GET /api/v1/checkout/orders` — authenticated buyer's paginated order history.
- `GET /api/v1/admin/orders` — admin-only paginated order list with buyer contact details.
- `GET /api/v1/checkout/orders/{id}/collection-details` — paid order owner or admin retrieves the collection address.
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

All orders are marked `fulfillmentMode: "PICKUP"`; shipping is not offered. Set `ADMIN_EMAIL` to your Google account and add your phone number to your own profile. Authenticated buyers can retrieve it from `GET /api/v1/contact` to arrange collection. Anonymous visitors cannot access the private contact number. Set `COLLECTION_ADDRESS` as a private environment variable. The address is returned only to an order owner after payment confirmation or to an admin; it is never included in public painting or checkout responses.

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

# FairShare

FairShare helps roommates organize shared expenses and household information.
This repository contains a Vue frontend, a Java API, database migrations, and
static HTML prototypes for CSCI-4440 Software Design and Documentation.

## Project structure

- `fairshare/`: Vue 3 frontend built with Vite.
- `fairshare-backend/`: Java 21 / Spring Boot 4.1.1 backend built with Maven.
- `supabase/migrations/`: versioned SQL for the database.
- `fairshare_site/`: standalone HTML/CSS prototypes, including dashboard,
  inventory, and expense pages.
- `docs/`: project documentation and team agreement.

The Vue frontend currently contains the landing page. Its login and signup links
are placeholders; frontend authentication and API integration are not yet wired
up. The backend supports Supabase signup, login, token refresh, logout, and
creating and listing households for the signed-in user. Inventory and expense API endpoints are not implemented.

## Prerequisites

- Node.js matching `^22.18.0 || >=24.12.0`, as specified in
  `fairshare/package.json`, and npm.
- Java 21 for the backend. The included Maven wrapper supplies Maven.
- A configured Supabase project for database access and authentication.
- Docker running locally only if you want to build and run the container yourself.

## Frontend setup

From the repository root:

```sh
cd fairshare
npm ci
npm run dev
```

Open the URL printed by Vite, usually http://localhost:5173. Edits reload
automatically. Stop the server with Ctrl+C.

Other commands, run from `fairshare/`:

```sh
npm run build
npm run preview
```

The production build is written to `fairshare/dist/`. Preview serves that build
locally; it is not the production hosting service.

Frontend files:

- `fairshare/src/App.vue`: main landing page.
- `fairshare/src/main.js`: Vue application entry point.
- `fairshare/src/assets/`: styles and assets.
- `fairshare/public/`: static files served by Vite.

## Backend setup

Supply these environment variables through your terminal or IDE before starting
the backend:

| Variable | Purpose |
| --- | --- |
| `DB_URL` | Supabase PostgreSQL JDBC URL, including SSL settings |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `SUPABASE_URL` | Supabase project URL for Auth |
| `SUPABASE_PUBLISHABLE_KEY` | Supabase publishable key for Auth |
| `PORT` | HTTP port; defaults to `8080` locally |
| `APP_ALLOWED_ORIGINS` | Comma-separated frontend origins allowed to call the API |

Local CORS defaults allow http://localhost:3000 and http://localhost:5173.
Configure `APP_ALLOWED_ORIGINS` with the actual frontend origin in production.

From the repository root:

```sh
cd fairshare-backend
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

Spring Boot does not automatically load a local `.env` file. Export the variables
or configure them in your IDE. The `--env-file` option below is for Docker.

### Database setup

Apply `supabase/migrations/20260929211542_create_households_and_members.sql`
to the intended Supabase project before using the household endpoints. It creates
the household and membership tables and links users to Supabase Auth accounts.
The backend does not automatically apply migrations or create tables.
Hibernate is configured to validate mapped schema.

Household table access goes through the authenticated Java API. The migration
enables RLS and revokes direct browser Data API access to those tables.

### Current API

Household endpoints require `Authorization: Bearer <Supabase access token>`.

| Method | Endpoint | Behavior |
| --- | --- | --- |
| `GET` | `/api/households` | Lists households the current user belongs to |
| `GET` | `/api/households/{id}` | Returns details for a household the current user belongs to |
| `POST` | `/api/households` | Creates a household and adds its creator as a member |

Household details include `id`, `name`, `ownerUserId`, `memberUserIds`, and `createdAt`.
The details endpoint returns `401` without a valid Supabase token and `404` for both
missing households and households the caller does not belong to. Invalid UUIDs return `400`.

Example create request body:

```json
{ "name": "Our apartment" }
```

### Build and tests

From `fairshare-backend/`:

```sh
./mvnw clean verify
```

This runs the existing tests and packages an executable JAR in `target/`.
Tests use an isolated H2 database and mocked Auth responses; they do not require
production database credentials or call Supabase.

## Deployment on Vercel

Use separate Vercel projects connected to this repository.

### Frontend project

- Root Directory: `fairshare`.
- Framework preset: Vite.
- Build command: `npm run build`.
- Output directory: `dist`.

Frontend API configuration still needs to be implemented when connecting the UI
to the backend. Never include database credentials in browser code.

### Backend project

1. Set Root Directory to `fairshare-backend` so Vercel detects
   `Dockerfile.vercel` and uses the correct build context.
2. Configure the backend environment variables listed above.
3. Set `PORT=8080` so Vercel routes traffic to Spring Boot's listening port.
4. Set `APP_ALLOWED_ORIGINS` to the deployed frontend origin.
5. Apply the required database migrations, deploy, and use the backend project's
   stable production URL for frontend API calls.

`Dockerfile.vercel` runs the Maven build and tests, then copies the executable
JAR into a Java 21 runtime image. The application runs without root privileges.
Database credentials are supplied at runtime, never baked into the image.
Use separate test database/Auth settings for preview deployments.

See [Vercel's container deployment guide](https://vercel.com/blog/dockerfile-on-vercel).

### Updates

Edit code, run the relevant build or tests, and commit and push to the connected
repository. Branch pushes create preview deployments; pushes or merges to the
configured production branch update the live application.

Vercel rebuilds the backend image using the same Dockerfile. Edit that file when
build or runtime requirements change, such as a Java upgrade or a new system
dependency. Apply database migrations separately before deploying code that
requires a new schema.

### Testing the backend container locally

From `fairshare-backend/`, with Docker running:

```sh
docker build -f Dockerfile.vercel -t fairshare-backend .
docker run --rm -p 8080:8080 --env-file .env fairshare-backend
```

Create an uncommitted `.env` file with the backend variables listed above and
`PORT=8080`. Docker ignore rules exclude it from the build context.


## Supabase authentication endpoints

The backend delegates account creation, password verification, and token lifecycle
management to Supabase Auth. It does not store passwords or create local sessions.
The existing `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` settings configure Auth;
no additional database migrations are needed. Never use a service-role or secret key.

| Method | Endpoint | Request | Success |
| --- | --- | --- | --- |
| POST | `/api/auth/signup` | `{ "email": "person@example.com", "password": "a strong password" }` | `201`, user and optional tokens |
| POST | `/api/auth/login` | Same as signup | `200`, user and tokens |
| POST | `/api/auth/refresh` | `{ "refresh_token": "..." }` | `200`, rotated tokens |
| GET | `/api/auth/me` | `Authorization: Bearer <access_token>` | `200`, user UUID and email |
| POST | `/api/auth/logout` | `Authorization: Bearer <access_token>` | `204`, revokes current session's refresh tokens |

Login and refresh return `user`, `access_token`, `refresh_token`, `token_type`,
`expires_in`, and `expires_at`. The user's `id` is their Supabase UUID. Signup returns
null token fields when email confirmation is required: show a check-your-email
message and let the user log in after confirming. FairShare currently requires email
confirmation. Configure Site URL and redirects in Supabase for confirmation emails.
Supabase controls password policy and duplicate-registration behavior.

Clients send access tokens in Authorization headers. They must manage token storage,
refresh before expiry, and replace both tokens after refresh. Serialize refresh
requests because refresh tokens rotate. Clear local tokens after logout. Existing
access tokens may remain valid until expiry after logout. No CSRF endpoint or
session cookie is used. `GET /api/auth/me` validates tokens using Supabase's `/user`
endpoint; existing household endpoints retain their own Supabase identity checks.

Invalid input returns `400`, authentication failure `401`, forbidden requests `403`,
rate limits `429`, and upstream service failures `503` (malformed responses `502`).
Responses exclude passwords and editable user metadata. The existing CORS
configuration also applies to these endpoints. Frontend forms are still unwired.

Auth tests mock Supabase REST responses; they do not create real accounts.
See [Supabase password authentication](https://supabase.com/docs/guides/auth/passwords).

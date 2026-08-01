# BrautCloud Frontend

BrautCloud is an Angular frontend for a private wedding gallery experience. The landing page uses a premium editorial layout while the authentication, setup, dashboard, upload, and gallery routes adapt the same design language to product work, including couple sign-in and sign-up and an authenticated app shell with a shared sign-out control backed by cookie-based refresh sessions.

`../DESIGN.md` is the source of truth for visual principles, tokens, shared patterns, responsive behavior, accessibility, and the current page-family inventory.

## Development

Install dependencies with pnpm, then start the local development server:

```bash
pnpm install --frozen-lockfile
pnpm start
```

The app serves at `http://localhost:4200/` and reloads when source files change.

## Available scripts

```bash
pnpm start                    # ng serve
pnpm run build
pnpm run format:check
pnpm run typecheck
pnpm run security:audit       # production dependencies; high/critical fail
pnpm test
pnpm run watch
```

## Build output

`pnpm run build` creates a production build in `dist/`.

## Validation

`pnpm run format:check` checks source formatting. `pnpm run typecheck` runs strict TypeScript checks. `pnpm test` runs the unit test suite with Angular's test runner and Vitest. `pnpm run build` creates the production bundle. `pnpm run security:audit` scans production dependencies and fails on high or critical advisories. Moderate advisories remain visible without blocking so they can be triaged without hiding production risk.

Angular's build allowlist contains only `qrcode`: `ng-qrcode@21` uses this transitive CommonJS generator and has no ESM replacement in its Angular 21-compatible release. QR rendering is confined to the authenticated home route, so this narrow exception avoids an optimization warning without allowing arbitrary CommonJS dependencies. Revisit when a compatible ESM QR package is available.

The test suite covers authentication, session lifecycle, and onboarding flows:

- **Auth service**: logout path with `POST /api/auth/logout`, immediate local session clearing, waiting for an in-flight refresh (with a bounded timeout) before revoking the session, ignoring stale refresh results once logout has started, and onboarding state tracking from registration and refresh responses.
- **Auth routing**: onboarding-aware routing with safe return-url validation, guard enforcement (auth, guest, onboarding required, onboarding page), and destination resolution for incomplete vs. completed sessions.
- **Registration and sign-in**: duplicate-submission suppression, structured error handling, and navigation to onboarding or the intended destination.
- **Onboarding submission**: validation before sending, visible error display after failure, and session completion marking before navigation.
- **Gallery**: empty state handling (no event, no images, image loading errors) and populated gallery rendering.
- **Home**: defensive rendering without event data, explicit loading and error states.

## Release and runtime boundaries

Frontend package identity is `1.0.0`, matching repository `VERSION` and backend
Maven metadata. The release is planned, not shipped; see [`../RELEASE.md`](../RELEASE.md).

Development uses localhost values from `src/environments/environment.development.ts`.
A production build must receive approved HTTPS API and app origins through the
release/deployment configuration. Never ship localhost URLs, HTTP API URLs, or
backend/storage credentials in the browser bundle. The selected deployment target
and its static-artifact configuration remain outside this repository until approved.

## Angular CLI

For Angular CLI usage and command references, see the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli).

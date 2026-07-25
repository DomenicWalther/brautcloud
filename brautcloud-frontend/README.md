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
pnpm start           # ng serve
pnpm run build
pnpm run format:check
pnpm test
pnpm run watch
```

## Build output

`pnpm run build` creates a production build in `dist/`.

## Validation

`pnpm run format:check` checks source formatting. `pnpm test` runs the unit test suite with Angular's test runner and Vitest. `pnpm run build` creates the production bundle; the existing `ng-qrcode` CommonJS optimization warning is accepted.

The test suite covers authentication, session lifecycle, and onboarding flows:

- **Auth service**: logout path with `POST /api/auth/logout`, immediate local session clearing, waiting for an in-flight refresh (with a bounded timeout) before revoking the session, ignoring stale refresh results once logout has started, and onboarding state tracking from registration and refresh responses.
- **Auth routing**: onboarding-aware routing with safe return-url validation, guard enforcement (auth, guest, onboarding required, onboarding page), and destination resolution for incomplete vs. completed sessions.
- **Registration and sign-in**: duplicate-submission suppression, structured error handling, and navigation to onboarding or the intended destination.
- **Onboarding submission**: validation before sending, visible error display after failure, and session completion marking before navigation.
- **Gallery**: empty state handling (no event, no images, image loading errors) and populated gallery rendering.
- **Home**: defensive rendering without event data, explicit loading and error states.

## Angular CLI

For Angular CLI usage and command references, see the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli).

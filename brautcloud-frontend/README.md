# BrautCloud Frontend

BrautCloud is an Angular frontend for a private wedding gallery experience. It includes the editorial landing page, couple sign-in and sign-up, and an authenticated app shell with a shared sign-out control backed by cookie-based refresh sessions.

## Development

Install dependencies with pnpm, then start the local development server:

```bash
pnpm install --frozen-lockfile
pnpm start
```

The app serves at `http://localhost:4200/` and reloads when source files change.

## Available scripts

```bash
pnpm start       # ng serve
pnpm run build
pnpm test
pnpm run watch
```

## Build output

`pnpm run build` creates a production build in `dist/`.

## Testing

`pnpm test` runs the unit test suite with Angular's test runner and Vitest.

The test suite covers authentication, session lifecycle, and onboarding flows:

- **Auth service**: logout path with `POST /api/auth/logout`, local session clearing, refresh cancellation, and onboarding state tracking from registration and refresh responses.
- **Auth routing**: onboarding-aware routing with safe return-url validation, guard enforcement (auth, guest, onboarding required, onboarding page), and destination resolution for incomplete vs. completed sessions.
- **Registration and sign-in**: duplicate-submission suppression, structured error handling, and navigation to onboarding or the intended destination.
- **Onboarding submission**: validation before sending, visible error display after failure, and session completion marking before navigation.
- **Home**: defensive rendering without event data, explicit loading and error states.

## Angular CLI

For Angular CLI usage and command references, see the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli).

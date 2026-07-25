# BrautCloud Frontend

BrautCloud is an Angular frontend for a private wedding gallery experience. It includes the editorial landing page, couple sign-in and sign-up, and an authenticated app shell with a shared sign-out control backed by cookie-based refresh sessions.

## Development

Install dependencies, then start the local development server:

```bash
npm install
npm start
```

The app serves at `http://localhost:4200/` and reloads when source files change.

## Available scripts

```bash
npm start   # ng serve
npm run build
npm test
npm run watch
```

## Build output

`npm run build` creates a production build in `dist/`.

## Testing

`npm test` runs the unit test suite with Angular's test runner and Vitest.

The auth service specs cover the logout path, including `POST /api/auth/logout` with credentials, immediate local session clearing, waiting for in-flight refresh before revoking the session (with timeout), and ensuring stale refresh results cannot restore a logged-out session.

## Angular CLI

For Angular CLI usage and command references, see the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli).

# Brautcloud Codebase Audit

Date: 2026-08-01  
Scope: read-only security, architecture, reliability, maintainability, dependency, test, and function-level review  
Repository state during audit: clean `dev` worktree; no source files modified

## Executive summary

Brautcloud has a good baseline in several important areas: owner checks are generally consistent, refresh tokens are hashed and revocable, access tokens stay in frontend memory, S3 object keys are server-generated, image types and signatures are checked after upload, and no obvious SQL injection, XSS, or IDOR issue was found in the reviewed paths.

The system is not production-ready without additional hardening. The highest-risk areas are:

1. Presigned uploads can be created without a signed content length, so the direct-to-storage path can accept oversized objects before confirmation.
2. Transient S3 verification failures are treated as invalid uploads and can permanently discard valid uploads.
3. Gallery passwords have no meaningful minimum-strength requirement or visible brute-force protection.
4. Login and registration have no visible application-level rate limiting.
5. The production frontend artifact contains `http://localhost:8080/api` and `http://localhost:4200`.
6. Upload confirmation, JSON input, ZIP export, cleanup, and storage reconciliation are not sufficiently bounded.
7. Storage deletion is not protected by a distributed job lease and can race with new uploads.
8. Several individual methods perform authorization, validation, persistence, storage I/O, state transitions, and user-facing error decisions at once.

The most valuable near-term refactor is to make uploads and deletion explicit state machines, then split the large orchestration methods around those state transitions. This reduces both security risk and code complexity at the same time.

## Verification performed

- Frontend typecheck: passed.
- Frontend production build: passed, but the generated artifact contains localhost URLs.
- Frontend tests: passed — 19 test files, 113 tests.
- Frontend production dependency audit: passed.
- Frontend formatting check: failed — 114 files reported as unformatted.
- Backend compilation and unit/service tests: passed.
- Full backend suite: blocked by the local Docker/Testcontainers prerequisite. Maven reported 118 tests, 0 assertion failures, and 40 environment-related errors.
- Maven runtime dependency tree: passed.
- Git working tree: clean.

This is a static audit plus available test execution, not a penetration test or production deployment review.

## Findings overview

| ID | Severity | Confidence | Area | Finding |
|---|---|---|---|---|
| S-01 | High | Confirmed | Upload security | Presigned uploads can omit signed content length |
| S-02 | High | Confirmed | Abuse prevention | Gallery passwords lack minimum strength and rate limiting |
| S-03 | High | High | Abuse prevention | Login and registration have no visible rate limiting |
| S-04 | High | Confirmed | Reliability/security | Transient S3 failures can delete valid upload records |
| S-05 | High | Confirmed | Deployment | Production frontend uses localhost HTTP URLs |
| S-06 | Medium | Confirmed | Resource exhaustion | Batch IDs, request bodies, passwords, and exports are insufficiently bounded |
| S-07 | Medium | Confirmed | Observability | Security and Flyway TRACE/DEBUG logging is enabled by default |
| S-08 | Medium | Needs verification | Transport security | S3 endpoint configuration does not enforce HTTPS |
| R-01 | High | High | Data integrity | Event deletion can race with new uploads |
| R-02 | Medium | Confirmed | Distributed processing | Deletion jobs have no lease or claim protocol |
| R-03 | Medium | Confirmed | Availability | ZIP export is unbounded and buffered by the browser |
| R-04 | Medium | Confirmed | Database performance | N+1 queries and unbounded list queries |
| R-05 | Medium | Confirmed | Persistence model | Lombok `@Data` on bidirectional JPA entities |
| R-06 | Low/Medium | Confirmed | Frontend memory | Object URLs and image preload state are not consistently released |
| R-07 | Medium | High | Concurrency | Concurrent login refresh-token creation can race |
| R-08 | Medium | Confirmed | CI quality | Formatting gate currently fails |
| R-09 | Low/Medium | Confirmed | Maintainability | Legacy and dead application surfaces remain |
| A-01 | Medium | Confirmed | Architecture | Large services combine too many responsibilities |
| A-02 | Medium | Confirmed | Architecture | Upload/deletion lifecycle lacks explicit state transitions |
| A-03 | Medium | Confirmed | Operations | Production deployment/security configuration is mostly externalized |
| C-01 | Medium | Confirmed | Dependencies | Compose uses mutable image tags and a hardcoded password |
| C-02 | Medium | High | Supply chain | Backend dependency scanning and SBOM generation are missing |
| C-03 | Low | Confirmed | Tooling | Angular declares npm while the project uses pnpm |

# 1. Security vulnerabilities

## S-01 — Presigned uploads can omit content length

Severity: High. Confidence: Confirmed.

Affected code:

- `brautcloud-backend/src/main/java/com/domenicwalther/brautcloud/controller/EventController.java`, around lines 63–84
- `brautcloud-backend/src/main/java/com/domenicwalther/brautcloud/service/ImageService.java`, around lines 97–128
- `brautcloud-backend/src/main/java/com/domenicwalther/brautcloud/service/S3Service.java`, around lines 79–95
- `brautcloud-frontend/src/app/services/image-service.ts`, around lines 56–85
- `ImageUploadPolicy.java`, around lines 9–22 and 59–83

The frontend normally sends filenames only. The backend then calls `getPresignedPutUrl(key)` without a content length. The 10 MB limit is enforced later during confirmation, after the object has already been uploaded to S3-compatible storage.

Impact:

- A public guest can upload objects much larger than intended.
- Storage and bandwidth costs can be abused.
- Oversized objects remain if later cleanup fails.
- Multipart limits do not protect direct-to-S3 uploads.

Recommendation: require file size for every presign request, sign the exact length, prefer an S3 POST policy with a strict range, reject missing metadata, and add object lifecycle rules for abandoned uploads.

Suggested tests: missing size rejected; signed size mismatch rejected; oversized direct upload cannot be confirmed or retained; owner and guest paths behave identically.

## S-02 — Gallery passwords are brute-forceable and may be weak

Severity: High. Confidence: Confirmed.

Affected code:

- `OnboardingRequest.java`, around lines 9–19
- `EventUpdateRequest.java`, around lines 9–18
- `EventService.java`, around lines 228–264
- `EventController.java`, around lines 56–100
- `SecurityConfig.java`, around lines 57–63

Gallery passwords have maximum-length validation in some DTOs, but no meaningful minimum-strength requirement. Public gallery access relies on repeated password checks without visible per-event, per-IP, or per-session throttling.

Recommendation: require a minimum length, add per-event and per-client throttling with backoff, record failed attempts, and consider issuing a short-lived guest access token after successful verification.

## S-03 — Authentication endpoints lack visible rate limiting

Severity: High. Confidence: High.

Affected code: `AuthController.java`, around lines 58–96, and `SecurityConfig.java`, around lines 53–65.

Registration and login are public, but no application-level or documented edge-level rate limiting is present in the repository.

Recommendation: add per-IP, per-account, and global limits, controlled backoff, and telemetry for failed-login bursts without exposing account existence.

## S-04 — Transient S3 failures can permanently discard valid uploads

Severity: High. Confidence: Confirmed.

Affected code: `S3Service.verifyUploadedImage`, around lines 97–123, and `ImageService.markImagesAsUploaded` / `discardUnverifiedImages`, around lines 164–192.

`verifyUploadedImage` catches every `RuntimeException` and returns `false`. The image service then treats the result as an invalid upload, attempts deletion, and deletes database records even if the underlying failure was a temporary storage outage.

Recommendation: return typed outcomes such as valid, invalid, missing, and transient failure. Preserve pending rows on transient failures, retry verification, and route failed deletion through the durable outbox.

## S-05 — Production frontend contains localhost HTTP URLs

Severity: High. Confidence: Confirmed.

Affected code: `brautcloud-frontend/src/environments/environment.ts`, around lines 1–5, and `angular.json`, around lines 40–68.

The production build output was verified to contain `http://localhost:8080/api` and `http://localhost:4200`.

Recommendation: make production API configuration a required build/deployment input, keep localhost values development-only, and fail CI when a production artifact contains localhost or insecure API URLs.

## S-06 — Several inputs and operations are insufficiently bounded

Severity: Medium. Confidence: Confirmed.

Affected code: `ImageController.java`, around lines 30–34; `EventController.java`, around lines 86–92; `ResourceOwnershipService.java`, around lines 44–59; `ImageService.java`, around lines 168–178 and 292–297; `AuthRequest.java`; `EventRequest.java`.

Examples include large UUID lists, missing maximum lengths on authentication fields, large JSON bodies, full-event ZIP exports, and confirmation requests that can cause many S3 and database operations.

Recommendation: enforce request body limits, cap ID lists, add password/email maxima, reject oversized exports, and process large operations in bounded batches.

## S-07 — Security and Flyway TRACE/DEBUG logging is enabled by default

Severity: Medium. Confidence: Confirmed.

Affected code: `brautcloud-backend/src/main/resources/application.properties`, lines 29–30.

The default configuration enables:

```properties
logging.level.org.springframework.security=TRACE
logging.level.org.flywaydb=DEBUG
```

Move verbose logging into local development profiles and use INFO/WARN in production.

## S-08 — S3 endpoint does not enforce HTTPS

Severity: Medium. Confidence: Needs verification.

Affected code: `S3Config.java`, around lines 18–49, and `application.properties`, around lines 22–25.

The endpoint is passed through `URI.create` without validating its scheme. Require HTTPS outside explicitly detected local development and test production bucket privacy, IAM, CORS, and TLS.

# 2. Reliability and correctness

## R-01 — Event deletion can race with new uploads

Affected code: `ImageService.java`, around lines 103–125, and `StorageDeletionService.java`, around lines 62–72 and 103–171.

Deletion scans existing images while presigning can create new image rows. There is no clear event-level state that prevents uploads once deletion begins.

Recommendation: model active, deletion-requested, deleting, and deleted states; reject new presigns after deletion begins; use transactionally consistent locking or conditional writes.

## R-02 — Deletion jobs have no distributed lease

Affected code: `StorageDeletionService.java`, around lines 178–213 and 229–284, and `StorageDeletionJobRepository.java`.

The scheduled worker processes all due jobs without a visible claim/lease protocol. Multiple application instances can process the same job concurrently.

Recommendation: use `SELECT ... FOR UPDATE SKIP LOCKED` or a lease column, make processing idempotent, and test with two workers.

## R-03 — ZIP export is unbounded and buffered in the browser

Affected code: `EventService.streamEventImagesAsZip`, around lines 197–218, and `Home.downloadAllPhotos`, around lines 145–178.

The backend streams all event images into a ZIP, while the frontend receives the complete response as a `Blob`.

Recommendation: set practical limits, use asynchronous exports for large galleries, stream to object storage, and return an expiring download URL.

## R-04 — N+1 queries and unbounded repository reads

Affected code: `EventService.getEventsByUserEmail` / `toEventResponse`, around lines 73–85; `UserService.getUserResponse`; `ImageRepository`; and `ImageService.cleanupUnuploadedImages`, around lines 321–337.

Use projections with counts, indexes, pagination, bounded cleanup batches, and query-count regression tests.

## R-05 — Lombok `@Data` on bidirectional JPA entities

Affected code: `User.java`, `Event.java`, and `Image.java`.

`@Data` generates equality and string methods across relationship graphs. Replace it with explicit accessors, identifier-based equality, and relationship exclusions.

## R-06 — Frontend object URLs and preloads are not consistently released

Affected code: `event-gallery.ts`, around lines 106–157; `image-upload.ts`, around lines 78–115 and 127–159; `gallery.ts`, around lines 365–417.

Centralize object URL lifecycle management, revoke URLs on replacement and destroy, and cancel subscriptions tied to destroyed views.

## R-07 — Concurrent login refresh-token creation can race

Affected code: `RefreshTokenService.createRefreshToken`, around lines 34–44.

The method deletes existing tokens and inserts a replacement, but initial concurrent logins do not obviously share the same locking path as refresh rotation. Serialize per user or handle conflicts transactionally and add a concurrency test.

## R-08 — Frontend formatting gate is failing

`.github/workflows/ci.yml` invokes `pnpm run format:check`, which currently reports 114 files requiring formatting. Fix this in a mechanical change and keep the gate mandatory.

## R-09 — Legacy and dead surfaces increase maintenance and attack surface

Resolved in the legacy-surface cleanup: repository-wide reference searches found no application, test, build, CI, frontend, or documentation consumers for `src/main/resources/static/upload.html`, `ImageRequest`, `ImageResponse`, `EventService.getEvents`, the `src/bruno` collection, or `brautcloud-roadmap.html`. The upload page targeted removed `/upload`; Bruno requests used obsolete routes, payloads, numeric IDs, a machine-local file path, and an expired bearer token. Recent history traces these files to the initial monorepo import, while current controller and integration-test mappings cover the supported upload and event flows. These files and method were removed; retain this inventory check when adding replacement tooling or API examples.

# 3. Architecture and module concerns

## A-01 — Large services combine too many responsibilities

`ImageService` is approximately 342 lines and handles authorization, validation, rate limiting, quotas, presigning, persistence, verification, cleanup, and deletion. `EventService` combines event CRUD, gallery access, image listing, password migration, and ZIP export. `StorageDeletionService` combines outbox creation, reconciliation, retry scheduling, object deletion, and database cleanup.

These are not merely large files; their methods have multiple independent reasons to change. Split by policy and side-effect boundary.

## A-02 — Upload and deletion need an explicit state machine

The current lifecycle is inferred from combinations of flags, row existence, object existence, and outbox jobs. Model it explicitly:

```text
PRESIGNED -> PENDING -> VERIFIED -> AVAILABLE
                    \-> INVALID
AVAILABLE -> DELETE_REQUESTED -> DELETE_RETRYING -> DELETED
```

Each transition should be atomic, observable, and retryable.

## A-03 — Production deployment responsibilities are not represented in the repository

There are no visible production manifests, reverse-proxy configuration, security headers, runtime URL injection, or deployment smoke tests. This does not prove the deployment is insecure, but the repository cannot verify those requirements.

# 4. Dependency and configuration risks

## C-01 — Compose configuration is unsafe by default

`brautcloud-backend/compose.yaml` uses mutable `latest` tags, a hardcoded `POSTGRES_PASSWORD=secret`, exposed database/object-storage ports, and local filesystem mounts. Pin versions, use ignored environment files or secrets, and bind services to localhost.

## C-02 — Backend dependency scanning and SBOM generation are missing

The frontend runs `pnpm audit`, but backend CI does not appear to run an equivalent Maven/OSV/OWASP scan or produce an SBOM. Centralize AWS SDK versions and add automated dependency and wrapper verification.

## C-03 — Package-manager declarations disagree

`package.json` declares pnpm, while `angular.json` declares npm. Align them so tooling cannot select different package managers.

# 5. Function-level review: lower-level simplification opportunities

This section focuses on functions rather than files. The central rule is not “make every method tiny.” The useful boundary is where a method changes abstraction level or has a second independent reason to change. A good function should usually do one of these things: validate a value, authorize an operation, calculate a plan, perform one side effect, or map a result. It should rarely do all five.

## Backend: `ImageService`

### `generatePresignedUploadUrls(Event, ImageUploadRequest, ...)`

Location: `ImageService.java`, around lines 97–138.

This is the most overloaded method in the backend. It currently:

1. Validates the request indirectly.
2. Applies a rate limit.
3. Applies event and guest quotas.
4. Checks deletion state.
5. Iterates every file.
6. Sanitizes the name.
7. Chooses one of two presigning modes.
8. Calls S3.
9. Builds the JPA entity.
10. Saves the entity.
11. Manually rolls back previously saved entities if a later item fails.

The method is concise in line count but dense in responsibility. The manual rollback is particularly revealing: a domain operation is being assembled from several side effects without a clear transactional model.

Recommended shape:

```text
prepareUploadPlan(request, event, guest)      // pure validation/calculation
authorizeUpload(plan, event, guest)           // rate limit, quota, lifecycle state
createPendingUploads(plan)                   // persistence transaction
createPresignedRequests(pendingUploads)      // storage adapter
return uploadResponses
```

Better still, make the plan contain one required `UploadSpec` per file with a non-null byte count. Avoid using `contentTypes == null && fileSizes == null` as a mode switch.

### `generatePublicPresignedUploadUrlsWithMetadata(...)`

Location: around lines 84–95.

This method constructs a second `ImageUploadRequest` by copying fields out of the incoming request, validates it, authenticates the guest, builds a rate-limit key, and delegates. That is a sign that the request type is carrying transport concerns instead of representing a validated domain command.

Recommended improvement: validate and normalize once at the controller boundary, then pass a `PublicUploadCommand` containing `eventId`, `galleryAccess`, `guestSessionHash`, `clientAddress`, and immutable file specifications. The service should not need to rebuild a request object to scope it.

### `markImagesAsUploaded(List<Image>)`

Location: around lines 164–179.

This method combines lifecycle checks, storage verification, invalid-object cleanup, error policy, and persistence state transition. It also has an ambiguous batch behavior: one invalid image causes invalid images to be discarded and the whole operation to fail, while valid images remain pending.

Recommended improvement:

- `verifyPendingImage(image)` returns a typed result.
- `discardInvalidImage(image)` is a durable cleanup command.
- `markVerifiedImages(images)` performs one persistence update.
- The batch method returns per-image outcomes or uses an explicit all-or-nothing contract.

The current name implies a simple flag update, but the method actually performs remote I/O and destructive cleanup. A more honest name would be `verifyAndPublishPendingImages`, or better, split the operations entirely.

### `discardUnverifiedImages(List<Image>)`

Location: around lines 181–192.

The method attempts object deletion, ignores every failure, then deletes the database rows. That is short but semantically dangerous. The function should not decide that losing the database reference is acceptable when storage deletion failed.

Recommended improvement: create deletion jobs for each object and keep the database row in a terminal `INVALID_PENDING_DELETE` state until storage deletion succeeds or an operator resolves it.

### `validateUploadRequest(ImageUploadRequest)`

Location: around lines 194–230.

This method is one of the better candidates for extraction because it is mostly pure, but it currently mixes collection-level validation, filename normalization, extension-based type derivation, client metadata comparison, size validation, and construction of `UploadSpec`.

Recommended decomposition:

- `validateFileCount(fileNames)`
- `validateMetadataShape(request, fileCount)`
- `parseUploadSpec(index, request)`
- `validateFileName(fileName)`
- `validateDeclaredMetadata(spec)`

Do not necessarily create five public classes. Private pure helpers or a dedicated `UploadRequestValidator` would make the behavior easier to test and keep error messages local. Replace nullable sizes with a required value once S-01 is fixed.

### `enforceQuota(Event, List<UploadSpec>, ...)`

Location: around lines 232–256.

This function calculates event count, event bytes, guest count, and guest bytes with multiple repository calls. It is readable, but it is not atomic and mixes quota policy with database query orchestration.

Recommended improvement:

- Introduce `UploadQuota` and `QuotaUsage` value types.
- Load one aggregate projection for the event and one for the guest.
- Let a quota policy compare usage plus requested totals.
- Enforce the result transactionally or accept/handle concurrent oversubscription explicitly.

The policy itself can become a pure function, which makes boundary tests straightforward.

### `enforceRateLimit(String)`

Location: around lines 258–271.

The method is small but its state lifecycle is incomplete: keys are created in an in-memory map and empty deques are never removed. It also quietly defines a single-instance rate limit.

Recommended improvement: extract a `PresignRateLimiter` adapter. The service should call `rateLimiter.check(key)`, while the adapter owns storage, expiry, cleanup, and distributed behavior. For multiple instances, use a shared store or an edge limiter.

### `cleanupUnuploadedImages()`

Location: around lines 321–337.

This scheduled method queries all old pending images, filters them in memory, and performs request/process calls one by one. It also mixes scheduling, selection, cleanup, retry behavior, and logging.

Recommended improvement: make the scheduler dispatch a bounded batch to `PendingUploadCleanupWorker`. The worker should claim rows, process them idempotently, and record outcomes. The scheduler itself should be no more than “find due work and dispatch it.”

## Backend: `EventService`

### `getEventImages(UUID, boolean, String)`

Location: around lines 186–195.

This method queries images, filters deletion state, signs every URL, derives delete permission, and maps entities to DTOs. It crosses persistence, storage, authorization, and presentation boundaries.

Recommended decomposition:

- Repository query returns only available image projections.
- `ImageUrlSigner` signs keys, potentially with controlled batching/caching.
- `ImagePermission` computes owner/guest permissions.
- A mapper creates `EventImageDTO`.

The current lambda hides three meaningful decisions inside one expression, making failure handling and query behavior difficult to change.

### `streamEventImagesAsZip(String, UUID)`

Location: around lines 197–218.

This method authorizes the owner, queries and filters images, decides whether to return `null`, creates the ZIP stream, fetches objects, creates entries, and handles resource closure.

Recommended improvement: return an explicit `Optional<ZipExportPlan>` or throw a typed empty-gallery result. Move ZIP generation into an `EventExportService`. The service should receive a bounded list/iterator and a cancellation-aware writer. Use a display filename for the ZIP entry rather than exposing the storage key structure.

### `matchesGalleryPassword(Event, String)`

Location: around lines 235–264.

This method verifies BCrypt passwords, detects malformed/legacy values, performs constant-time plaintext comparison, upgrades the stored value, and saves the event during an access check. It is security-sensitive and has too many branches for a method that reads like a predicate.

Recommended decomposition:

- `GalleryPasswordVerifier.verify(candidate, stored)` returns `MATCH`, `NO_MATCH`, or `LEGACY_MATCH`.
- `GalleryPasswordMigration.upgrade(event, candidate)` performs the write separately.
- The caller decides whether an upgrade should be persisted.

This also removes the surprising behavior where a read/access operation mutates the event.

### `registerView(Event, UUID)`

Location: around lines 101–112.

The method increments the event counter, checks existence, inserts a visit, and catches a uniqueness race. It is reasonably small, but the `exists` query followed by `save` is an avoidable two-step race.

Recommended improvement: use one insert with a unique constraint and interpret duplicate-key as “already counted,” or use a database-native upsert. Keep the count increment and distinct-visitor insert behavior explicit.

## Backend: `StorageDeletionService`

### `processEventDeletion(UUID)`

Location: around lines 82–172.

This is the most bloated backend function. It:

1. Loads the event job.
2. Loads the event.
3. Validates stale-job state.
4. Reconciles missing image jobs.
5. Loads all image jobs.
6. Processes every child job.
7. Tracks the first exception.
8. Rechecks image rows.
9. Deletes the event.
10. Deletes the event job.
11. Converts database/storage errors into lifecycle errors.

Recommended decomposition:

```text
loadEventDeletionContext(eventId)
ensureImageDeletionJobs(context)
processChildDeletionJobs(context)
assertNoRemainingImages(context)
finalizeEventDeletion(context)
```

Each step should have a clear transaction boundary. Do not keep the whole orchestration as one method with nested try/catch blocks; that makes the actual state machine invisible.

### `retryPendingDeletions()`

Location: around lines 178–201.

This method reconciles all requested images, reconciles all requested events, queries every due job, dynamically dispatches by enum, catches individual failures, and catches the entire worker failure. It is a scheduler, reconciler, router, and error reporter.

Recommended improvement:

- `DeletionScheduler`: triggers work at a fixed interval.
- `DeletionReconciler`: repairs missing jobs in bounded pages.
- `DeletionJobRepository.claimDueBatch`: atomically leases work.
- `DeletionJobWorker.process(job)`: processes exactly one job.

This produces smaller tests and makes multi-instance behavior explicit.

### `processImageJob(StorageDeletionJob)`

Location: around lines 229–270.

The function reads the image, handles stale references, deletes from S3, deletes database rows, deletes the job, and maps failures into lifecycle exceptions. The ordering is defensible, but the function combines two different transactions: remote object deletion and database cleanup.

Recommended improvement: represent “object deleted, database cleanup pending” as a normal state instead of relying on a retry after an exception. The method should be an idempotent transition worker with a small number of explicit outcomes.

### `ensureJob(...)`

Location: around lines 272–284.

The check-then-insert pattern is vulnerable to concurrent callers even with a unique constraint. The exception handling for the unique race is not visible here.

Recommended improvement: make insertion idempotent with a database upsert or catch a duplicate-key conflict and reload the existing job. Hide that behavior behind `ensureJob` so callers do not need to understand the race.

### `markFailure(...)`

Location: around lines 286–303.

The retry policy, error serialization, persistence, and fallback logging live together. The exponential delay is compact but opaque.

Recommended improvement: extract `RetrySchedule.next(attempts)` as a pure function and cap/ classify errors before storing them. Do not persist raw infrastructure exception messages without checking for secrets or excessive length.

## Backend: authentication and error handling

### `RefreshTokenService.createRefreshToken(User)`

Location: around lines 34–44.

This method is short, but deleting the old token and inserting the new one is a policy decision hidden inside token creation. It also relies on transaction behavior for concurrent initial logins.

Recommended improvement: make the one-session policy explicit in a `SessionManager`, lock the user or use a unique/upsert strategy, and name the operation around the policy rather than the implementation.

### `RefreshTokenService.deleteByToken(String)`

Location: around lines 69–78.

The `Optional.map` lambda performs several unrelated mutations: increment token version, save the user, delete all user tokens, and return a status. This is compact but less readable than a guarded imperative flow.

Recommended shape:

```text
refreshToken = findTokenForUpdateOrNull(token)
if (refreshToken == null) return false
revokeUser(refreshToken.user)
deleteSessions(refreshToken.user)
return true
```

Use `Optional` for absence, not as a container for a multi-step transaction.

### `findTokenForUpdateOrLegacyLookup(String)`

Location: around lines 94–101.

The method tries a locking lookup and then falls back to a non-locking lookup of the same token. That fallback weakens the method’s name and can undermine the caller’s concurrency assumptions. If the fallback supports a real legacy format, model it as an explicit migration path. Otherwise remove it.

### `GlobalExceptionHandler.handleValidation(...)`

Location: around lines 27–33.

The handler picks only the first validation error and assumes the list and default message are non-null. It also repeats response-map construction across handlers.

Recommended improvement: introduce one immutable `ApiError` response, collect field errors, and use safe fallbacks. Add handlers for malformed JSON and unexpected exceptions with stable public messages.

### `ImageUploadPolicy.hasValidSignature(...)`

Location: around lines 59–75.

The method is compact but allocates prefix byte arrays during each call and performs only magic-byte checks. That is acceptable as a first filter, not a complete image parser.

Recommended improvement: keep static prefix constants, document the check as “header validation,” and use a bounded image metadata parser if the application needs stronger content validation. Keep policy pure and independently tested.

## Frontend: upload service

### `uploadImagesWithEndpoints(...)`

Location: `brautcloud-frontend/src/app/services/image-service.ts`, around lines 88–125.

This method receives endpoint URLs, request variants, files, credentials, and headers as parallel primitive arguments. It then maps presigned responses to files by array position, creates one upload observable per file, uploads to S3, notifies the backend, converts most errors into result objects, and runs all work through `forkJoin`.

This is a classic data-clump and orchestration method. The positional mapping also assumes the backend always returns responses in exactly the request order.

Recommended shape:

```text
UploadBatch { presignUrl, confirmUrl, files, accessHeaders, credentials }
presign(batch)
uploadOne(uploadGrant, file)
confirmBatch(uploadedIds)
```

Return results keyed by image ID or client upload ID rather than relying on array position. Limit concurrency instead of starting up to 100 uploads at once. Use an abortable transport so unsubscribe actually cancels the upload.

### `uploadToS3(...)`

Location: around lines 127–147.

The custom `new Observable` wrapper around `fetch` has no teardown function, so unsubscribing does not abort the request. It also owns transport error translation inside a low-level adapter.

Recommended improvement: add an `AbortController` and return a teardown that calls `abort()`, or use a transport abstraction that supports cancellation and progress. Keep HTTP status normalization in one place.

### `notifyBackend(...)`

Location: around lines 149–158.

The method confirms one image ID per request even though the backend accepts a list. A batch of 100 files can therefore produce 100 confirmation requests.

Recommended improvement: confirm all successfully uploaded IDs in one bounded request, or use a controlled concurrency pool. The result should preserve per-image outcomes if partial confirmation is supported.

### `uploadImages(...)` and `uploadPublicImages(...)`

Location: around lines 56–85.

These methods differ mainly in endpoint construction, headers, and request shape. The public and owner upload flows are already close to a shared abstraction, but the abstraction currently exposes six parameters to the lower-level helper.

Recommended improvement: pass one `UploadContext` containing access mode, endpoint pair, headers, and credential policy. Build requests from files once, including content type and size metadata after S-01 is fixed.

## Frontend: page functions

### `EventGallery.onFilesSelected(...)`

Location: `brautcloud-frontend/src/app/pages/event/event-gallery.ts`, around lines 106–143.

The method reads DOM state, filters MIME types, creates object URLs, computes capacity, creates user-facing error text, revokes rejected previews, mutates signals, and resets the input.

Recommended improvement: extract a pure `selectFiles(current, incoming, policy)` function returning accepted files, rejected count, overflow count, and error state. The component should only apply the result and manage DOM input reset.

### `EventGallery.submitPassword(...)`

Location: around lines 75–100.

This method handles form submission, guard conditions, UI loading state, API access verification, session state, password error translation, and view registration.

Recommended improvement: a `GalleryAccessService.verify(eventId, password)` should return an access result. The component should translate that result into signals. This also makes it easier to add throttling, expiry, or a guest access token later.

### `ImageUpload.onFilesSelected(...)`

Location: `brautcloud-frontend/src/app/pages/app/image-upload/image-upload.ts`, around lines 78–102.

This duplicates the public picker logic and creates object URLs for all valid new files before slicing to the maximum. URLs for files discarded by `.slice(0, MAX_FILES)` are not revoked.

Recommended improvement: use the same pure selection helper as `EventGallery`; create previews only for files that will actually be retained, or revoke all overflow previews immediately.

### `ImageUpload.uploadSelected(...)`

Location: around lines 117–159.

This method invokes upload, maps results by array index, creates additional object URLs for successful local previews, formats pluralized messages, updates the uploaded list, clears selected files, and controls the loading state.

Recommended improvement: separate `uploadSelectedFiles()` from `applyUploadResults()`. Track preview URLs in one owned collection and revoke them in a destroy hook. Use a shared message formatter rather than embedding pluralization in the state transition.

### `Gallery.confirmDelete(...)`

Location: `brautcloud-frontend/src/app/pages/app/image-gallery/gallery/gallery.ts`, around lines 199–346.

This is the largest frontend command method. It closes the dialog, determines the event, snapshots selection state, creates pending state, builds one request per image, suppresses errors into `null`, calculates successful and failed images, updates indexes, closes the lightbox when necessary, creates messages, clears pending state, and restores focus.

Recommended improvement:

- `beginDeletion(selection)` captures a command.
- `deleteImages(command)` returns per-ID outcomes.
- `applyDeletion(outcomes)` updates the image collection and selected index.
- `finishDeletion(outcomes)` handles toast and focus.

Use IDs rather than object-reference comparison when updating collections. Keep the index adjustment in a small pure function with table-driven tests.

### `Gallery.loadAll(...)`

Location: around lines 394–417.

The method subscribes directly from an effect-driven component. If the event or refresh token changes quickly, older requests can complete after newer requests and overwrite state. The subscription is not explicitly cancelled at this method boundary.

Recommended improvement: use a signal/resource or an RxJS `switchMap` pipeline with `takeUntilDestroyed`. Make request identity part of the result so stale responses cannot win.

### `Home.downloadAllPhotos(...)`

Location: `brautcloud-frontend/src/app/pages/app/home/home.ts`, around lines 145–178.

The method handles command guards, UI loading state, API response interpretation, Blob URL creation, link creation, filename generation, cleanup, and toast messaging. It also buffers the full ZIP in the browser.

Recommended improvement: move download mechanics into a `FileDownloadService`; let the component respond to `empty`, `started`, `completed`, and `failed` results. Ensure the object URL is revoked after the browser has consumed the link and support cancellation for large exports.

### `Home` view-registration effect

Location: around lines 65–78.

The session-storage marker is written before the backend confirms view registration. A failed request therefore suppresses retries for the remainder of the browser session.

Recommended improvement: write the marker only after success, or store a short-lived pending state with retry semantics.

# 6. Lower-level style rules worth applying

These are the concrete refactoring rules I would apply throughout the codebase:

1. Do not pass the same cluster of primitive parameters repeatedly. Create a command/value object.
2. Do not use nullable fields as mode switches. Use separate commands or an explicit mode enum.
3. Do not hide multi-step transactions inside `Optional.map` lambdas.
4. Keep remote I/O out of methods named like predicates or mappers.
5. Keep persistence writes out of read/verification methods unless the name says migration or upgrade.
6. Replace positional correlation between two lists with an explicit client or server upload ID.
7. Prefer pure policy functions for validation, quotas, retry delays, filename safety, and index adjustment.
8. Let adapters own transport details such as S3, fetch, abort, status translation, and retry.
9. Make scheduled methods dispatch bounded work; do not make them perform the whole workflow.
10. Avoid “catch everything and convert to false/null.” Preserve the distinction between invalid input, missing data, and retryable infrastructure failure.
11. Use explicit result types when a batch can partially succeed.
12. Give functions names that reveal side effects: `verifyAndPublish`, `requestDeletion`, or `upgradeLegacyPassword` are clearer than `mark`, `matches`, or `process` when they mutate state.

# 7. Prioritized remediation plan

## Immediate

1. Require and sign file size for every presigned upload.
2. Distinguish invalid uploads from retryable S3 failures.
3. Add gallery-password and authentication rate limits.
4. Add minimum gallery-password strength requirements.
5. Replace production localhost URLs and assert against them in CI.
6. Cap request bodies, UUID batches, passwords, and synchronous ZIP exports.
7. Remove TRACE/DEBUG logging from production defaults.
8. Pin Compose images and remove the hardcoded database password.

## Next sprint

1. Introduce explicit upload and deletion states.
2. Prevent uploads after event deletion begins.
3. Add leased/claimed deletion-job processing.
4. Add projections, indexes, pagination, and bounded cleanup workers.
5. Refactor the specific methods called out in the function-level review.
6. Extract a shared frontend upload selection/orchestration layer.
7. Fix the formatting gate.
8. Add concurrency and failure-mode tests.

## Longer term

1. Move large exports to asynchronous object-storage jobs.
2. Add production deployment manifests and security-header configuration.
3. Add backend vulnerability scanning and SBOM generation.
4. Add metrics and alerts for failed uploads, deletion retries, storage errors, brute-force attempts, and export failures.
5. Establish a repository-level threat model and security regression suite.

## Top 10 refactoring candidates

1. `ImageService.java` — split upload authorization, policy, verification, guest sessions, and cleanup.
2. `StorageDeletionService.java` — convert to an explicit leased state-machine worker.
3. `EventService.java` — separate event management, gallery access, image queries, and export.
4. `event-gallery.ts` + `image-upload.ts` — extract shared file selection and upload orchestration.
5. `gallery.ts` — separate image state, selection, preloading, and deletion workflows.
6. `home.ts` — extract export/download and QR responsibilities.
7. `User.java` / `Event.java` / `Image.java` — replace `@Data` with safe entity equality and logging.
8. `AuthController.java` + `RefreshTokenService.java` — formalize session issuance and concurrency guarantees.
9. `S3Service.verifyUploadedImage` — return typed outcomes and integrate durable retry behavior.
10. Repository/query layer — add projections, indexes, pagination, and bounded worker queries.


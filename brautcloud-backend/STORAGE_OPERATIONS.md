# Storage operations contract

Application uses configurable S3-compatible APIs through `aws.s3.endpoint`; source does not select or assume production provider. Deployment owner must apply controls below to configured bucket.

## Privacy and presigned URLs

- Keep bucket private. Enable provider public-access blocking and deny public `GetObject`, public `PutObject`, and public ACL writes.
- Backend credentials/role need only bucket-scoped `GetObject`, `PutObject`, and `DeleteObject` for application objects. Add `ListBucket` only if deployment runs an external reconciliation job; application outbox reconciliation uses database keys and does not list the bucket.
- Presigned PUT URLs are valid for 15 minutes and presigned GET URLs for 2 hours. They are the only object URLs emitted by application. They do not make bucket public; deployment policy must not override this with anonymous access.
- Application generates UUID-prefixed keys and sanitizes client filenames. Clients never choose bucket names or arbitrary object keys. Do not grant clients direct IAM credentials.
- If deployment uses a custom S3-compatible endpoint, verify its signature, path-style, TLS, ACL, and bucket-policy behavior before production. `S3Config` uses path-style addressing and the configured endpoint.

## Deletion and retries

Image and event deletion first commits database deletion markers and `storage_deletion_jobs`. Object deletion happens before its database reference is removed. S3-compatible `DeleteObject` is expected to be idempotent, so a retry after a database failure is safe. The scheduled worker retries failed jobs with backoff and rebuilds missing jobs from deletion markers. Pending uploads use the same path after their retention period.

Deployment owner must:

- monitor application logs/metrics for jobs repeatedly retrying;
- configure lifecycle expiry for incomplete multipart uploads and the application's abandoned-upload namespace with a retention period longer than normal upload/confirmation time;
- use bucket versioning/retention and a documented restore procedure if business backup requirements need recovery of deleted objects;
- back up PostgreSQL, including deletion markers and outbox rows, with point-in-time recovery appropriate to the service. Restore database and bucket state together; restoring only one can recreate references to missing objects or objects without references;
- run an independent bucket inventory/reconciliation process if bucket access or operational policy permits it. Application cannot discover objects that have no database key without `ListBucket`/inventory access.

These IAM, lifecycle, bucket privacy, versioning, and backup settings are deployment-owned and cannot be enforced by this repository. No production storage vendor is prescribed.

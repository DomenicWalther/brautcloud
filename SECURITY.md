# Security and credential hygiene

## Local secrets

Keep development and production credentials outside Git. Use Doppler or another local secret manager for application credentials. Compose requires a locally chosen `POSTGRES_PASSWORD`; Bruno requests read local-only values from process environment variables. Test configuration uses placeholders and safe test doubles, not live AWS, database, or signing credentials.

Do not paste credentials or bearer tokens into commits, issues, pull requests, logs, or fixtures. If debugging authentication, use a locally generated account and short-lived token, then discard it.

## Repository guard

CI runs `python3 scripts/check-secrets.py` against every tracked file. It rejects JWTs, private-key blocks, known provider-token formats, and hardcoded secret-like values in configuration/request files. No test or fixture paths are excluded; only explicit environment expressions and clearly named placeholder values are accepted.

## Previously committed credential-shaped material

A bearer-token-shaped value was removed from the Bruno collection. Treat any previously used value as potentially exposed. Before release, maintainers must determine whether it was accepted by any environment; if so, revoke affected sessions and rotate the corresponding signing or service credential through the secret manager. Do not publish the old value while investigating.

Report suspected live credentials privately to repository maintainers. Remove them from the working tree and history as appropriate, then complete provider-specific revocation or rotation before treating the incident as closed.

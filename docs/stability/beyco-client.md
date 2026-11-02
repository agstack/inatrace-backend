# Beyco integration: transport failures

The OAuth exchange, refresh and offer submission use the configured Beyco URLs
and credentials. Each request has a 30-second connection timeout and a
30-second read timeout by default; `beyco.integration.connectTimeoutMs` and
`beyco.integration.readTimeoutMs` can override them independently. These are
per-phase deadlines, not an overall deadline for the complete operation.
Values must be positive; invalid values prevent initialization of the Beyco
service when it is first needed.

| Scenario | Local result | Remote effect |
| --- | --- | --- |
| Valid exchange, refresh or offer | Existing successful response | One provider POST |
| Provider 400/401/403/429/5xx | 503 `UPSTREAM_HTTP_ERROR`, safe message | One provider POST |
| Empty or malformed success response; token without access token | 503 `UPSTREAM_HTTP_ERROR` | Provider outcome may be unknown |
| Connection refused or read timeout | 503 `UPSTREAM_HTTP_ERROR` | Provider outcome may be unknown |
| Anonymous or foreign-company request | Existing 401/403 | No provider call |
| Offer accepted but response lost | 503 `UPSTREAM_HTTP_ERROR` | One POST; creation may have succeeded |

A provider authentication failure does not invalidate the INATrace session.
There is no automatic offer replay: an operator must check Beyco before retrying
an ambiguous submission. The response does not include provider bodies, URLs,
credentials or tokens. The Angular companion change supports both the new error
envelope and older plain-text errors, and propagates a Beyco 503 to the error
handler rather than treating it as success. Deploy the frontend change first so
it is in place before the backend starts returning the new 503 response.

The backend tests exercise a local HTTP server with synthetic data and the
shared MySQL fixture. The Angular error-message tests cover the envelope, legacy
text and fallback. These tests do not contact Beyco or prove its current
production behavior.

Token responses require a nonblank access token. If supplied, refresh tokens
must be nonblank and `expiresIn` must be positive; missing optional fields remain
accepted for compatibility. The existing frontend uses those optional fields for
refresh, so a response that omits them cannot support its full session lifecycle.
No public Beyco response schema was confirmed during this work: offer success
objects remain unconstrained beyond the existing nonempty-body check. A response
cannot establish whether an ambiguous offer was created; check Beyco before
retrying. Confirm the provider's current schema before tightening either rule.

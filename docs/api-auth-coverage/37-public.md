# Public endpoints — issue #37

`PublicLabelApiTest` exercises all 13 mappings in `PublicController` through
the real MockMvc filter chain and the shared MySQL Testcontainer. These routes
are intentionally anonymous: the control is a valid public capability, the
publication state of a label, or the request-log token, rather than a tenant
JWT. Fixtures use distinctive `PUBLIC-...` and `DRAFT-...` values, and the
tests check that an invalid capability or an unpublished landing page does
not return the marker. Refused feedback writes leave the feedback count
unchanged.

| Method | Route | Public boundary | Test evidence | Result |
|---|---|---|---|---|
| GET | `/api/public/product/label/{uid}` | Published label only | Published marker returns; draft marker is refused | Verified |
| GET | `/api/public/stock-order/{qrTag}` | QR tag is the public capability | Valid tag returns marker; unknown tag does not | Verified |
| GET | `/api/public/product/knowledgeBlog/{id}` | Currently resolved by blog ID only | Draft-label blog marker is returned anonymously | Characterized: publication gate bypassed |
| GET | `/api/public/product/label_batch/{uid}/{number}` | Currently resolved by label UID and batch number only | Draft batch, dates and origin marker are returned | Characterized: publication gate bypassed |
| GET | `/api/public/product/label/{uid}/verify_batch_authenticity` | Currently resolved by label UID and batch data only | Draft batch check returns its boolean result anonymously | Characterized: publication gate bypassed |
| GET | `/api/public/product/label/{uid}/verify_batch_origin` | Currently resolved by label UID and batch number only | Draft origin marker is returned anonymously | Characterized: publication gate bypassed |
| POST | `/api/public/logRequest` | Request-log token | Valid token succeeds; invalid token receives 403 | Verified |
| GET | `/api/public/document/{storageKey}` | Opaque, temporary public storage key | Valid public key streams bytes; unknown key is rejected without marker | Verified |
| GET | `/api/public/image/{storageKey}` | Opaque, temporary public storage key | Valid public key streams image bytes | Verified |
| GET | `/api/public/image/{storageKey}/{size}` | Opaque, temporary public storage key | Valid public key streams a resized image | Verified |
| GET | `/api/public/product/label/feedback/list/{labelUid}` | Currently resolved by label UID only | Draft feedback marker is returned anonymously | Characterized: publication gate bypassed |
| POST | `/api/public/product/label/feedback/{labelUid}` | Published label only | Published write succeeds; draft write is refused and count is unchanged | Verified |
| GET | `/api/public/globalSettings/{name}` | `isPublic` setting flag | Public value returns; private value is absent | Verified |

## Decision required before a production fix

The test deliberately records current behavior rather than asserting a new
policy for five draft-label paths: batch lookup, authenticity verification,
origin verification, knowledge-blog lookup, and feedback listing. The first
four are the publication-gap paths called out by issue #37; the feedback list
uses the same unguarded label-UID lookup. The team must decide whether
unpublishing a label withdraws all of those public artefacts or only the label
landing page and feedback submission. Once decided, change the corresponding
rows from characterization assertions to refusal assertions and implement the
service guard if needed.

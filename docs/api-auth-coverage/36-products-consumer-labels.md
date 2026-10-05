# Products and consumer labels — issue #36

The issue describes 36 endpoints. The current controllers expose **34** authenticated
mappings: 33 in `ProductController` and one in `FinalProductController`. This matrix
records every mapping present in this revision; the two-endpoint difference must be
reconciled with the issue history before declaring the campaign count complete.

`ProductAndLabelApiTest` uses the real MockMvc filter chain, a JWT access cookie and
the shared MySQL Testcontainer. It seeds an owner, an associated company and an
unrelated tenant. Refused responses are checked for the distinctive owner marker and
refused writes are checked against persisted state.

| Method | Route | Access rule | Test coverage |
|---|---|---|---|
| POST | `/api/product/create` | Enrolled in requested company | Foreign and anonymous creation refused; product count unchanged |
| GET | `/api/product/list` | Current user's visible products | Owner control and anonymous refusal |
| GET | `/api/product/admin/list` | System admin | System-admin control; company user and anonymous refusal |
| GET | `/api/product/{id}` | Owner or associated company | Associate control; foreign and anonymous refusal |
| PUT | `/api/product/` | Owner or associated company | Foreign and anonymous update refused; product marker unchanged |
| DELETE | `/api/product/{id}` | Owner only | Associate, foreign and anonymous refusal; product retained |
| GET | `/api/product/{id}/labels` | Owner or associated company | Associate control; foreign and anonymous refusal |
| POST | `/api/product/label/create` | Owner or associated company | Foreign and anonymous creation refused; label count unchanged |
| GET | `/api/product/label/{id}` | Owner or associated company | Associate control; foreign and anonymous refusal |
| PUT | `/api/product/label` | Owner or associated company | Foreign and anonymous update refused; label marker unchanged |
| DELETE | `/api/product/label/{id}` | Owner or associated company | Foreign and anonymous refusal; label retained |
| PUT | `/api/product/label/content` | Owner or associated company | Foreign and anonymous refusal |
| GET | `/api/product/label/content/{id}` | Owner or associated company | Associate control; foreign and anonymous refusal |
| GET | `/api/product/label/{id}/documents` | Owner or associated company | Associate control; foreign and anonymous refusal |
| PUT | `/api/product/label/{id}/documents` | Owner or associated company | Foreign and anonymous refusal |
| POST | `/api/product/label/execute/{action}` | Owner or associated company | Foreign and anonymous refusal |
| GET | `/api/product/label/analytics/{uid}` | Owner or associated company | Associate control; foreign and anonymous refusal |
| POST | `/api/product/label_batch/create` | Owner or associated company | Foreign and anonymous creation refused; batch count unchanged |
| GET | `/api/product/label_batch/{id}` | Owner or associated company | Associate control; foreign and anonymous refusal |
| PUT | `/api/product/label_batch` | Owner or associated company | Foreign and anonymous refusal; batch number unchanged |
| DELETE | `/api/product/label_batch/{id}` | Owner or associated company | Foreign and anonymous refusal; batch retained |
| GET | `/api/product/label/{id}/batches` | Owner only | Owner control; associate, foreign and anonymous refusal |
| GET | `/api/product/label/{id}/instructions` | Owner or associated company | Foreign refusal occurs before document generation; anonymous refusal |
| GET | `/api/product/knowledgeBlog/list/{productId}` | Owner or associated company | Associate control; foreign and anonymous refusal |
| GET | `/api/product/knowledgeBlog/{id}` | Owner or associated company | Associate control; foreign and anonymous refusal |
| PUT | `/api/product/knowledgeBlog` | Owner or associated company | Foreign and anonymous refusal |
| POST | `/api/product/knowledgeBlog/{productId}` | Owner or associated company | Foreign and anonymous refusal |
| DELETE | `/api/product/label/feedback/{id}` | Owner or associated company | Foreign and anonymous refusal; feedback retained |
| GET | `/api/product/{productId}/finalProduct/{finalProductId}` | Owner or associated company | Associate control; foreign and anonymous refusal |
| GET | `/api/product/{productId}/finalProduct/{finalProductId}/labels` | Owner or associated company | Associate control; foreign and anonymous refusal |
| GET | `/api/product/{productId}/finalProduct/list` | Owner or associated company | Associate control; foreign and anonymous refusal |
| PUT | `/api/product/{productId}/finalProduct` | Owner only | Associate, foreign and anonymous refusal; final product unchanged |
| DELETE | `/api/product/{productId}/finalProduct/{finalProductId}` | Owner only | Associate, foreign and anonymous refusal; final product retained |
| GET | `/api/final-product/company/{companyId}` | Enrolled in requested company | Owner control; foreign and anonymous refusal |

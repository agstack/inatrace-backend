# API authorization coverage — campaign #34

This directory is the versioned evidence for the authorization campaign. The
issue describes **203 operations in 27 controllers**. The repository at
`3519ffe` has 203 method mappings in **26** `@RestController` classes. Commit
`b7088c9` removed seven mappings and `CommonCsvController`; at `018a7f8`,
Spring MVC registers **196 operations in 25 controllers**. This explains all
seven route differences and one controller difference. The extra controller
in the issue's “27” count is not reproduced by the 203-operation snapshot;
its source is still unknown. No endpoint is counted as covered merely because
its controller has a test class.

## Inventory and scope

[`routes.tsv`](routes.tsv) records the effective HTTP method, `/api` path, and
controller method for every application controller registered by Spring MVC.
`ApiRouteInventoryTest` compares that file to `RequestMappingHandlerMapping`
and `/v3/api-docs` on every test run. Both expose the same **196** operations
in this commit. This catches additions, removals, and path changes before the
coverage matrix becomes stale. The TSV is an inventory, **not a claim of
authorization coverage**.

| Area | Controllers | Issue count | Registered at `018a7f8` | Matrix |
|---|---:|---:|---:|---|
| Authentication and Users | 1 | part of the historical 45 | 15 | [existing.md](existing.md) |
| Companies, farmers and plots | 1 | part of the historical 45 | 30 | [existing.md](existing.md) |
| Supply Chain #35 | 9 | 51 | 51 | pending #35 PR |
| Products and consumer labels #36 | 2 | 36 | 34 | pending #36 PR |
| Public #37 | 1 | 13 | 13 | [37-public.md](37-public.md) — policy decision pending |
| Codebooks #38 | 9 | 44 | 41 | pending #38 PR |
| Files, settings and reporting #39 | 2 | 14 | 12 | pending #39 PR |
| **Total** | **25 current** | **203 historical** | **196** | |

The 45 operations in the existing areas are inside `UserController` and
`CompanyController`. `FarmerAndPlotApiTest` exercises routes on
`CompanyController`; there is no separate farmer controller in this commit.
The five sub-issues total 158 historical operations and 151 current ones.

### Seven operations removed by `b7088c9`

| Area | Historical method and `/api` route | Current alternative |
|---|---|---|
| #36 | `PUT /api/product/label/values` | `PUT /api/product/label/content` |
| #36 | `GET /api/product/label/values/{id}` | `GET /api/product/label/content/{id}` |
| #38 | `GET /api/chain/currency-type/list` | `list/enabled` and `list/disabled` |
| #38 | `GET /api/chain/processing-evidence-type/list/value-chain/{id}` | `list/by-value-chains` |
| #38 | `GET /api/chain/processing-evidence-field/list/value-chain/{id}` | `list/by-value-chains` |
| #39 | `POST /api/chain/csv/payments/company/{id}` | `/api/chain/payment/export/company/{id}` |
| #39 | `POST /api/chain/csv/purchases/company/{id}` | `/api/chain/stock-order/export/deliveries/company/{companyId}` |

The two #39 historical routes belonged to `CommonCsvController`, which was
removed entirely. The #39 issue names only `CommonController` and
`DashboardController`; its count of 14 is consistent with also counting these
two removed CSV routes. The commit message title says “six” endpoints, but
the diff and its own route list show seven.

Each future area matrix must contain one row per current method + route with
its expected policy, fixture, positive/denied/anonymous scenarios, leakage
check, persisted-state check for writes, test method, and finding/PR. Historical
routes no longer present are accounted for in the table above and should be
referenced by the relevant area matrix.
The parent issue closes only after those matrices and the existing-area gaps
are resolved.

## Test infrastructure decision and baseline

The maintainer chose to retain the shared `AbstractMySqlIntegrationTest`:
one static MySQL Testcontainer supplies all compatible test contexts through
`@DynamicPropertySource`. This meets the real-MySQL requirement but differs
from the issue's literal `@ServiceConnection` wording. This campaign will
preserve the observed performance instead of changing the container lifecycle
to match an annotation. New MockMvc tests extend that shared class and use the
real security filter chain and `TokenService` cookies.

The pre-change `mvn -q -DargLine=-Dapi.version=1.43 verify` run on
2026-09-29 passed in **36.944 s** wall time. After adding the two inventory
tests, `mvn -q -DargLine=-Dapi.version=1.43 clean verify` passed and cleared
stale Surefire reports. A subsequent run of the original `verify` command
passed in **37.933 s** wall time. Both wall times are single local runs, not
medians; the approximately 0.99 s difference is not a controlled performance
benchmark. These runs used Testcontainers 1.19, which needed
`-Dapi.version` to reach Docker 29; since the upgrade to 1.21.4 the plain
`mvn verify` works.

| Metric after clean verification | Result |
|---|---:|
| Test classes | 14 |
| Tests | 123 (121 existing + 2 inventory) |
| Failures / errors | 0 / 0 |
| Sum of Surefire class times | 32.855 s |
| Application contexts started | 3 (shared MockMvc, shared random port, isolated migration) |
| MySQL containers started | 2 (shared tests, isolated migration) |
| Runtime | OpenJDK 25.0.4.1, Maven 3.9.9, Linux x86_64 |

Testcontainers also starts Ryuk, which is not counted as a MySQL container.
Surefire class times are not wall time. Before `clean`, old Surefire XML files
were still present under `target/` and misleadingly summed to 176 tests; those
files did not represent this checkout's current suite. Subsequent PRs should
record the same metrics and investigate extra contexts, containers, or
external calls.

## Regenerating and reviewing the route inventory

```bash
mvn -q -Dtest=ApiRouteInventoryTest -Dapi.inventory.dump=true test
```

The command prints lines prefixed `API_AUTH_ROUTE`, with method, path, and
handler. Review any change against the controller, OpenAPI, and the area
matrix before updating `routes.tsv`. The ordinary test run compares the
registered mappings with the file and fails on drift. The dump mode exists
for review and is not the coverage check.

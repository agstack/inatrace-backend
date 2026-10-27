# AgStack integration behavior

The client logs in to AgStack and keeps the access token in memory. It refreshes
after more than three whole hours have elapsed, independently of the provider's
token expiration. Concurrent cache misses can perform more than one login; cache
publication and invalidation use atomic state so a late rejection cannot erase
a different token. A
boundary response of HTTP 400 may include matched GeoIDs and remains supported.
An empty boundary error response (including HTTP 400, 401, 429 or 500) is
reported as a classified failure rather than a null successful result.

Login and boundary calls each have a 30-second deadline, configurable with
`INATrace.agstack.requestTimeoutMs`. This changes failure timing from the
previously unbounded client. A plot operation may spend up to both deadlines
when it needs a login and a boundary call. A timeout does not prove that the
provider did not process a boundary POST; the client does not replay it.

Plot creation and refresh currently continue without a GeoID on an integration
failure; a later refresh can populate a missing GeoID. A boundary 401 invalidates
the rejected cached token, so a subsequent request performs a new login. The
failed boundary POST is never replayed automatically. Provider error messages
are not logged by the plot flow.

The local-server tests use synthetic credentials and no external AgStack service.
They cover the protocol, cache boundary, overlapping initial and expired logins,
401 recovery, selected provider errors, login and boundary timeouts, connection
refusal, and MySQL-backed plot creation, refresh and authorization flows. The
MySQL tests use MockMvc in the test transaction, then flush and reload the rows;
they do not model a separately committed HTTP request or every provider response.
Spring wiring tests verify the default clock, bean injection, and both the default
and configured timeout values.

Run `mvn -q -DargLine=-Dapi.version=1.43 -Dtest=AgStackClientContractTest,FarmerAndPlotApiTest test`
for the focused checks, followed by `mvn -q -DargLine=-Dapi.version=1.43 clean verify`.

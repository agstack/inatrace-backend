# Currency provider and recovery

The currency integration still uses the legacy exchangeratesapi.io protocol:
the provider key is the `access_key` query parameter and rates use EUR as the
base. `INAtrace.exchangerate.apiKey` remains unchanged. The optional
`INAtrace.exchangerate.baseURL` defaults to `http://api.exchangeratesapi.io/v1`;
`INAtrace.exchangerate.requestTimeoutMs` defaults to 30000 milliseconds per
request. Set a positive timeout and an appropriate endpoint for the deployment.
These defaults do not implement the provider/authentication migration in #3.
The historical default uses HTTP; the URL contains a credential, so deployments
must also review transport security and proxy/client debug logging. Do not log
full outbound URLs or enable HTTP wire logging with real keys.

Changing the deadline from unbounded to 30 seconds changes failure timing. A
scheduled refresh makes a symbols request and then a latest-rates request, so
its total provider wait can approach twice the configured timeout. Errors are
logged by exception type without the upstream payload or URL. Existing fallback
behavior remains: failed fetches produce no new rates; a historical conversion
with no stored rate fails rather than substituting a current or invented rate.
The authenticated conversion endpoint currently returns a generic 500 in that
case; it does not invalidate the local session.

Successful rates must include EUR as base, a date and a nonempty map containing
only positive values. Historical responses must also match the requested GMT
date. Invalid responses are ignored as a whole and no new rates are written.
Successful symbols and successful rates remain independent: a symbols update
can commit even when the following rates request fails. New symbols are disabled
by default, and repeated sequential refreshes do not add duplicate rates for the
same currency and date. This is not a guarantee against concurrent refreshes;
there is no new database uniqueness constraint in this change.

The tests use a synthetic loopback provider and the existing shared MySQL
container. Fixtures are committed separately and cleanup removes only fixture
IDs. They cover conversion and rounding, cache hits, historical and current
requests, repeated refresh, partial success, authorization, provider errors,
invalid data, timeout and recovery. The tests do not call an external provider
or prove that any actual deployment has a usable provider account.

Focused checks:

```bash
mvn -q -DargLine=-Dapi.version=1.43 -Dtest=CurrencyProviderContractTest,CurrencyProviderWiringTest,CurrencyUpdateSchedulerTest test
mvn -q -DargLine=-Dapi.version=1.43 clean verify
```

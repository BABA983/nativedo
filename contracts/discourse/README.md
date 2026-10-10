# Discourse contracts

Wire and model semantics for the linux.do client. These are the authoritative
protocol references, not a second design wiki or a log of API experiments.

- [Endpoints](endpoints.md): routes, request/response shapes and site-specific behavior.
- [Models](models.md): domain semantics, missing-field behavior and state identity.
- [Authentication](auth-flow.md): shared cookie jar, login, CSRF, invalidation and recovery.
- [Device authorization](device-authorization.md): experimental User API Key flow,
  distinct from the production cookie session.

[Fixtures](../../fixtures/discourse) provide sample payloads; they are not proof of
current live-site acceptance. Update a contract and relevant fixtures together when
wire/auth semantics change. Label dated observations as observations, not permanent
site configuration. Keep design rationale in the [design pages](../../docs/README.md)
and run evidence with the change that produced it.

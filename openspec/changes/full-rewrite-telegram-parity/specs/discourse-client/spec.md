## ADDED Requirements

### Requirement: Unified Discourse API client
The system SHALL provide a `DiscourseClient` class that encapsulates an OkHttpClient (shared singleton) and a Retrofit instance targeting `https://linux.do`. All Discourse API calls SHALL go through this client.

#### Scenario: Fetch latest topics
- **WHEN** `discourseClient.getLatestTopics(page = 0)` is called
- **THEN** a GET request to `https://linux.do/latest.json?page=0` is made with appropriate headers and cookies

#### Scenario: Network error handling
- **WHEN** a network request fails with IOException
- **THEN** the client wraps the error in an `AppError.Network` result

### Requirement: Automatic header injection
The system SHALL automatically inject `User-Agent`, `Accept: application/json`, and `X-CSRF-Token` headers into all API requests via OkHttp interceptors.

#### Scenario: CSRF token injection
- **WHEN** any mutating request (POST/PUT/DELETE) is made
- **THEN** the `X-CSRF-Token` header is set from `CsrfTokenProvider`

### Requirement: DiscourseApi Retrofit interface
The system SHALL define a `DiscourseApi` Retrofit interface with methods for all endpoints defined in `contracts/discourse/endpoints.md`: `getLatestTopics`, `getNewTopics`, `getTopTopics`, `getSiteInfo`, `getTopicDetail`, `getTopicPosts`, `search`, `getUserProfile`, `getCurrentSession`, `getCsrf`.

#### Scenario: Topic detail fetch
- **WHEN** `discourseApi.getTopicDetail(id = 12345)` is called
- **THEN** a GET request to `/t/12345.json` is made and the response is deserialized into the appropriate model

### Requirement: Error normalization
The system SHALL normalize all API errors (HTTP 4xx/5xx, network failures, JSON parse errors) into a sealed `AppError` hierarchy: `AppError.Network`, `AppError.Http(code, message)`, `AppError.Parse`, `AppError.SessionExpired`.

#### Scenario: HTTP 403 response
- **WHEN** an API request returns HTTP 403
- **THEN** the result contains `AppError.Http(403, "Forbidden")`

#### Scenario: HTTP 401 triggers session expired
- **WHEN** an API request returns HTTP 401
- **THEN** the result contains `AppError.SessionExpired` and `NotificationCenter.postNotificationName(sessionDidExpire)` is called

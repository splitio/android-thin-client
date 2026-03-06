# retryable-http-client

Coroutine-aware HTTP client with configurable per-category retry policies.

Wraps the `:android-client:http` `HttpClient` and adds automatic retries with exponential backoff, status-code-specific policy overrides, and cooperative cancellation.

## Key types

- `RetryableHttpClient` — suspend interface; single `execute(request, category)` entry point.
- `DefaultRetryableHttpClient` — production implementation; retries on non-success responses and non-SSL `HttpException`s according to the supplied policies.
- `HttpRequestDescriptor` — value type carrying URI, method, optional body, and headers.
- `RequestCategory` — enum (`AUTH`, `EVALUATIONS`, `EVENTS`, `TELEMETRY`) that selects which `CategoryRetryPolicies` to apply.
- `CategoryRetryPolicies` — holds a default `RetryPolicy` and an optional per-status override map. A `null` entry in the map means "do not retry for this status".
- `RetryPolicy` — `maxAttempts` (use `RetryPolicy.UNLIMITED` for infinite) and `backoffBaseSeconds` passed to the `BackoffCounter` factory.

## Usage

```kotlin
val client = DefaultRetryableHttpClient(
    httpClient = httpClient,
    policiesByCategory = mapOf(
        RequestCategory.EVALUATIONS to CategoryRetryPolicies(
            default = RetryPolicy(maxAttempts = 3, backoffBaseSeconds = 1),
            byStatus = mapOf(
                429 to RetryPolicy(maxAttempts = RetryPolicy.UNLIMITED, backoffBaseSeconds = 5),
                404 to null, // never retry
            ),
        ),
        RequestCategory.AUTH to CategoryRetryPolicies(
            default = RetryPolicy(maxAttempts = 3, backoffBaseSeconds = 2),
        ),
    ),
    backoffFactory = { base -> ExponentialBackoffCounter(base) },
)

val response = client.execute(
    request = HttpRequestDescriptor(
        uri = URI.create("https://sdk.split.io/api/v2/auth"),
        method = HttpMethod.GET,
        headers = mapOf("Authorization" to "Bearer $apiKey"),
    ),
    category = RequestCategory.AUTH,
)
```

### Retry behaviour

| Situation | Behaviour |
|-----------|-----------|
| Successful response (`isSuccess == true`) | Return immediately, no retry |
| Non-success response, no policy for status | Return immediately |
| Non-success response, status mapped to `null` | Return immediately |
| Non-success response, policy present | Retry up to `maxAttempts` with backoff |
| `HttpException` with status 9009 (SSL) | Rethrow immediately, no retry |
| `HttpException`, status mapped to `null` | Rethrow immediately |
| `HttpException`, policy present | Retry up to `maxAttempts` with backoff, then rethrow |
| Any other exception | Propagates immediately, no retry |
| Coroutine cancelled | Stops before the next attempt via `ensureActive()` / `delay` cancellation |

Backoff counters are scoped per `execute()` call and keyed by `backoffBaseSeconds`, so policies that share the same base share a counter within a single request execution.

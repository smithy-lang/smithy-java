# Retries and Waiters

> **Last updated:** April 29, 2026

Smithy-Java provides two complementary systems for handling transient failures and polling for resource state: the retry
strategy (for automatic retries within a single API call) and the waiter framework (for polling across multiple API
calls until a resource reaches a desired state).

**Source:**
- [`retries-api/`](https://github.com/smithy-lang/smithy-java/tree/main/retries-api) — Retry strategy interfaces
- [`retries/`](https://github.com/smithy-lang/smithy-java/tree/main/retries) — Concrete retry implementations
- [`client/client-waiters/`](https://github.com/smithy-lang/smithy-java/tree/main/client/client-waiters) — Waiter
  framework

## Retry Strategy

### Token-Based Protocol

The retry system uses an explicit token-passing protocol rather than simple counters:

```java
public interface RetryStrategy {
    AcquireInitialTokenResponse acquireInitialToken(AcquireInitialTokenRequest request);
    RefreshRetryTokenResponse refreshRetryToken(RefreshRetryTokenRequest request);
    RecordSuccessResponse recordSuccess(RecordSuccessRequest request);
    int maxAttempts();
}
```

Flow:
1. **Before first attempt**: `acquireInitialToken(scope)` → get a `RetryToken` + initial delay
2. **On failure**: `refreshRetryToken(token, failure, suggestedDelay)` → new token + backoff delay. Throws
   `TokenAcquisitionFailedException` if retries exhausted.
3. **On success**: `recordSuccess(token)` → releases capacity back to the token bucket

### RetryInfo

Exceptions can implement `RetryInfo` to provide retry metadata:

```java
public interface RetryInfo {
    RetrySafety isRetrySafe();  // YES, NO, MAYBE
    boolean isThrottle();
    Duration retryAfter();
}
```

Only `RetrySafety.YES` allows retries.

### StandardRetryStrategy

The recommended strategy. Defaults: 3 max attempts (1 initial + 2 retries), 50ms base delay, 20s max backoff.

```java
StandardRetryStrategy strategy = StandardRetryStrategy.builder()
    .maxAttempts(5)
    .backoffBaseDelay(Duration.ofMillis(100))
    .backoffMaxBackoff(Duration.ofSeconds(30))
    .build();
```

Uses `ExponentialDelayWithJitter`: `random(0, min(maxDelay, baseDelay * 2^(attempt-2)))`.

### AdaptiveRetryStrategy

Extends the standard strategy with client-side rate limiting using a CUBIC-based algorithm. Adds a
`RateLimiterTokenBucketStore` that tracks per-scope send rates:

- On throttling errors: reduces estimated safe send rate (`rate * 0.7`)
- On success: gradually increases send rate using CUBIC recovery
- May add delay even for the first attempt if the rate limiter is throttling

### Token Bucket System

- **`TokenBucket`** — Lock-free (`AtomicInteger`) token bucket. Long-polling tokens bypass the capacity check.
- **`TokenBucketStore`** — Per-scope store using `LinkedHashMap` with LRU eviction (128 max scopes). Thread-safe via
  `ReentrantLock`.
- Default capacity: 500 tokens. Normal retry cost: 14 tokens. Throttling retry cost: 5 tokens.

### Claimable

`RetryStrategy` implements `Claimable` — `claim(owner)` succeeds once, preventing accidental sharing across unrelated
clients.

## Pipeline Integration

The retry loop is embedded in `ClientPipeline`:

1. `retryStrategy.acquireInitialToken(scope)` — before first attempt
2. Execute attempt (auth → endpoint → sign → transport → deserialize)
3. On retryable error: extract `suggestedDelay` from `RetryInfo`, call `refreshRetryToken()`, sleep, loop
4. On `TokenAcquisitionFailedException`: no more retries
5. On success: `recordSuccess(token)`

`ClientCall.isRetryDisallowed()` prevents retries when the input has a non-replayable data stream.

## Waiter Framework

### Waiter

```java
public final class Waiter<I extends SerializableStruct, O extends SerializableStruct> {
    // Built via Waiter.builder(pollingFunction)
}
```

### Polling Loop

```java
waiter.wait(input, maxWaitTimeMillis);
```

1. `attemptNumber++`
2. Call `pollingFunction.poll(input, overrideConfig)` — catch `ModeledException`
3. `resolveState(input, output, exception)` — iterate acceptors, first match wins
4. Switch on state:
   - `SUCCESS` → return
   - `FAILURE` → throw `WaiterFailureException`
   - `RETRY` → `waitToRetry(attempt, maxWaitTime, startTime)` → `Thread.sleep(delay)`

### Acceptors and Matchers

```java
record Acceptor<I, O>(WaiterState state, Matcher<I, O> matcher) {}
```

`Matcher<I, O>` is a sealed interface with four implementations:

| Matcher | Factory | Matches When |
|---|---|---|
| `OutputMatcher` | `Matcher.output(Predicate<O>)` | Operation succeeds and predicate matches output |
| `InputOutputMatcher` | `Matcher.inputOutput(BiPredicate<I, O>)` | Operation succeeds and bi-predicate matches |
| `SuccessMatcher` | `Matcher.success(boolean)` | `true` → success; `false` → any error |
| `ErrorTypeMatcher` | `Matcher.errorType(String)` | Exception's schema ID name matches |

### Backoff Strategy (Waiter-specific)

Separate from retry backoff:

```java
public interface BackoffStrategy {
    long computeNextDelayInMills(int attempt, long remainingTime);
    static BackoffStrategy getDefault(Long minDelayMillis, Long maxDelayMillis);
}
```

`DefaultBackoffStrategy`: exponential with jitter, 2ms min, 120s max by default.

### JMESPath Integration

For evaluating Smithy waiter acceptor expressions at runtime:

- `JMESPathPredicate<O>` — Parses a JMESPath expression, evaluates against the output `Document`, compares using a
  `Comparator`
- `JMESPathBiPredicate<I, O>` — For input+output JMESPath expressions
- `Comparator` — Enum: `STRING_EQUALS`, `BOOLEAN_EQUALS`, `ALL_STRING_EQUALS`, `ANY_STRING_EQUALS`

## Waiter Code Generation

`WaiterContainerGenerator` generates a record class (e.g., `CoffeeShopWaiter`) that wraps the client and exposes a method per waiter defined in the model's `@waitable` trait:

```java
@SmithyGenerated
public record CoffeeShopWaiter(CoffeeShopClient client) {
    public Waiter<GetFooInput, GetFooOutput> fooReady() {
        return Waiter.<GetFooInput, GetFooOutput>builder(client::getFoo)
            .backoffStrategy(BackoffStrategy.getDefault(minDelay, maxDelay))
            .success(Matcher.output(new JMESPathPredicate("status", "DONE", Comparator.STRING_EQUALS)))
            .failure(Matcher.errorType("ResourceNotFound"))
            .build();
    }
}
```

The client interface gets a `waiter()` method:

```java
CoffeeShopWaiter waiter();
```

## Key Design Patterns

1. **Token-based retry protocol** — Enables circuit breaking via token buckets, scoped throttling, and long-polling
   exemption.
2. **Separation of concerns** — `retries-api` (interfaces), `retries` (implementations), `client-core` (pipeline
   orchestration), `client-waiters` (independent polling).
3. **Retries vs Waiters** — Retries handle transient failures within a single call (50ms-20s backoff). Waiters poll
   across multiple calls until a resource state is reached (2ms-120s backoff).

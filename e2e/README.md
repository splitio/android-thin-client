# e2e — Consumer Tests

This module verifies the published `android-thin-client` AAR from a consumer's perspective. Tests run as Android instrumented tests against the **built artifact**, not the source modules, so they catch packaging, API visibility, and Java interoperability issues that unit tests cannot.

## How it works

The module has no production source. Its `androidTest` sources import `io.split.client:android-thin-client` as an external dependency (resolved from Maven Local), then exercise the public API exactly as an SDK consumer would.

```
e2e/
└── src/
    └── androidTest/
        └── java/io/split/client/thin/consumer/
            ├── ConsumerAndroidTest.kt       # Kotlin consumer tests
            └── ConsumerJavaAndroidTest.java # Java consumer tests
```

## Prerequisites

The library must be published to Maven Local before running these tests:

```bash
./gradlew publishToMavenLocal
```

`settings.gradle.kts` already lists `mavenLocal()` as a repository, so the `e2e` module will resolve the artifact from there.

## Running the tests

Connect an emulator or physical device, then run:

```bash
./gradlew :e2e:connectedAndroidTest
```

## What is tested

| Test class | Language | Coverage |
|---|---|---|
| `ConsumerAndroidTest` | Kotlin | Value types, factory builder, client access, evaluation API, manager, config DSL & builder, events, event listener, metadata types |
| `ConsumerJavaAndroidTest` | Java | Same scenarios via Java API surface: getters, callback interfaces (`SplitCallback`, `SplitVoidCallback`), builder pattern |

The Java tests specifically guard against Kotlin-only constructs leaking into the public API (e.g. `@JvmSynthetic` on suspend functions, data class `copy`/`component` methods that should not be exposed).

## Adding new tests

- Add new test files under `src/androidTest/java/io/split/client/thin/consumer/`.
- Tests must only import from `io.split.client.thin` — do not add a dependency on any internal source module.
- If you add a new public API surface, add corresponding Kotlin **and** Java test cases to ensure both consumers work correctly.

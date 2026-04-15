# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Split.io Android thin client SDK — a lightweight Android library exposing the Split feature-flag API. It is published as a single fused AAR (`io.split.client:android-thin-client`) that merges several modules together.

## Setup

After cloning, initialize the git submodule:

```bash
git submodule update --init --recursive
```

## Build Commands

```bash
./gradlew build                                        # Build all modules
./gradlew testDebugUnitTest                            # Run all unit tests
./gradlew :api:testDebugUnitTest                       # Run tests for a single module
./gradlew jacocoAggregateUnitTestReport                # Coverage report (runs tests first)
./gradlew lint                                         # Android lint
./gradlew publishToMavenLocal                          # Publish fused AAR to local Maven
./gradlew publishAndroidThinClientToMavenLocal         # Publish fused AAR (alias, publishing only)
./gradlew :e2e:connectedAndroidTest                    # Run e2e consumer tests (requires device/emulator)
```

## Module Structure

- **`android-thin-client/`** — Fused library module (AGP fused-library plugin). Merges `api`, `fallback`, and `logger` into a single AAR for publishing. No source code here.
- **`api/`** — Public API and internal implementations. All new thin-client code goes here.
  - Public interfaces: `io.split.client.thin` (`SplitClient`, `SplitFactory`, `SplitManager`, `SplitFactoryBuilder`, etc.)
  - Internal implementations: `io.split.client.thin.internal` (`DefaultSplitClient`, `DefaultSplitFactory`, `ClientManager`, `AsyncBridge`, etc.)
- **`auth/`** — Authentication module (token acquisition and refresh).
- **`retryable-http-client/`** — HTTP client wrapper with retry/backoff logic.
- **`e2e/`** — Consumer androidTest module. Imports the published AAR from Maven Local and exercises the public API as an external consumer would (both Kotlin and Java). Run `publishToMavenLocal` first. See `e2e/README.md`.
- **`sdk/`** — Placeholder module (currently commented out in `settings.gradle.kts`).
- **`android-client/`** — Git submodule containing shared Android SDK modules. Only these are included:
  - `fallback` — Fallback treatment logic
  - `logger` — Logging utilities
  - `http` / `http-api` — HTTP client and contracts
  - `backoff` — Retry backoff counter

## Architecture

**Layered design**: Public interfaces in `io.split.client.thin`, implementations in `io.split.client.thin.internal`. Internal classes use `internal` visibility.

**Async pattern**: Primary API uses Kotlin `suspend` functions. Java-compatible callback variants are provided via `AsyncBridge` and marked `@Deprecated` in favor of coroutine usage. Use `@JvmSynthetic` on suspend functions to hide them from Java callers.

**Dependency injection**: Manual constructor injection. `DefaultSplitFactory` is the composition root that creates and wires all dependencies. `ClientManager` manages per-target client lifecycle.

**Builder pattern**: `SplitClientConfig` uses both a Java-style `Builder` and a Kotlin DSL (`splitClientConfig { }`).

## Key Conventions

- Language: Kotlin for all new code; Java exists in `android-client/` submodule modules
- Java compatibility: Java 11 source/target
- Min SDK: 21 (Android 5.0)
- Test framework: JUnit 4 + Mockito + kotlinx-coroutines-test
- Version catalog: Dependencies managed via `android-client/gradle/libs.versions.toml`
- Gradle: 9.1.0, AGP 9.0.0, Kotlin 1.8.10

## Git Workflow

- **Branch naming**: `FME-XXXXX-short-description` (e.g., `FME-13516-retryable`)
- **Commit format**: Short description only (e.g., `Add retry logic`)
- **Default branch**: `main`

## DOs

- Follow existing code patterns in the codebase
- Write Kotlin for all new code
- Use `internal` visibility for implementation classes in `io.split.client.thin.internal`
- Use `@JvmSynthetic` on suspend functions to hide them from Java callers

## DON'Ts

- Never force push to main/master
- Never commit secrets, `.env` files, or credentials
- Never modify code inside the `android-client/` git submodule
- Never add source code to the `android-thin-client/` fused library module

## Commands to Never Run

- `git push --force origin main`
- `git push --force origin master`
- `git commit --no-verify` or `git push --no-verify`

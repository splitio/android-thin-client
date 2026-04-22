# sdk

Public SDK entry point and package-level documentation.

## Overview

This module provides a convenient public API entry point for consumers of the thin-client SDK. It re-exports key types from `:api` and `:models` for easy discovery.

## Public Package

- `io.split.android.thin` — Primary public package for SDK consumers.

## Dependencies

- `:api` — Core SDK implementation
- `:models` — Public data types

## Build

```bash
# Build this module
./gradlew :sdk:assembleDebug

# Run unit tests
./gradlew :sdk:testDebugUnitTest
```

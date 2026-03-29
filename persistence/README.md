# Persistence Module

Room-based persistent storage for the Android thin client SDK.

## Purpose

Provides local database storage for:
- **Evaluations**: Cached flag evaluation results per matching key
- **Events**: Queued tracking events awaiting upload

## Design Philosophy

This module is a **dumb storage layer** that accepts and returns pre-serialized JSON strings. Serialization/deserialization happens in the caller (API module), keeping this module focused purely on persistence.

## Schema

### Evaluations

**`evaluation_metadata` table** — Stores changeNumber once per matchingKey:
- `matchingKey` (PK) — User key
- `changeNumber` — Server's `till` value for this key
- `updatedAt` — Last update timestamp

**`evaluations` table** — Stores individual flag evaluations:
- `matchingKey`, `flagName` (composite PK) — Unique per flag per user
- `body` — Pre-serialized JSON string
- `updatedAt` — Last update timestamp

### Events

**`events` table** — FIFO queue of tracking events:
- `id` (PK, auto-increment)
- `body` — Pre-serialized JSON string
- `createdAt` — Timestamp for FIFO ordering

## Components

### Interfaces

- `PersistentEvaluationStorage` — Load/persist/clear cached evaluations by matching key (string-based API)
- `PersistentEventsStorage` — Push/pop/count queued tracker events (string-based API)
- `SerializedEvaluation` — Wrapper for (flagName, json) pairs

### Implementations

- `RoomEvaluationPersistence` — Room-backed evaluation storage with metadata table
- `RoomEventsPersistence` — Room-backed event queue storage

## Dependencies

- **AndroidX Room** (2.4.3) — Database layer
- **Kotlinx Coroutines** (1.8.1) — Async operations

**No domain dependencies** — This module does not depend on `models`, `evaluation`, or `tracker`.

## Build

```bash
./gradlew :persistence:build
./gradlew :persistence:testDebugUnitTest
```

## Usage

Module is consumed internally by the `api` module. The API module handles serialization before calling persistence and deserialization after loading from persistence.

**Example:**
```kotlin
// Caller serializes before persisting
val serialized = evaluations.map { eval ->
    SerializedEvaluation(eval.result.flag, json.encodeToString(eval))
}
persistence.persistForKey("user1", 12345L, serialized)

// Caller deserializes after loading
val data = persistence.loadForKey("user1")
val evaluations = data?.evaluations?.map { jsonString ->
    json.decodeFromString<StoredEvaluation>(jsonString)
}
```

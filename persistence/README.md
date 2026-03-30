# Persistence Module

Room-based persistent storage for the Android thin client SDK.

## Purpose

Provides local database storage for:
- **Evaluations**: Cached flag evaluation results per target
- **Events**: Queued tracking events awaiting upload

## Design Philosophy

This module is a **dumb storage layer** that accepts and returns pre-serialized strings. The consumer is responsible for:
- Computing a deterministic key string per target (e.g., a hash of Key + Attributes)
- Serializing evaluations to JSON strings
- Deserializing when loading from persistence

This keeps the module focused purely on persistence without domain knowledge.

## Schema

### Evaluations

**`general_info` table** — General-purpose key-value store; used to store serialized metadata once per key:
- `key` (PK) — Serialized Key (matchingKey + bucketingKey)
- `value` — JSON-encoded payload (e.g. `{"changeNumber":12345,"updatedAt":...}`)

**`evaluations` table** — Stores individual flag evaluations:
- `key`, `flagName` (composite PK) — Unique per flag per key
- `body` — Pre-serialized JSON string
- `updatedAt` — Last update timestamp

Different attribute combinations for the same user produce different key values, so they are stored as separate entries.

### Events

**`events` table** — FIFO queue of tracking events:
- `id` (PK, auto-increment)
- `body` — Pre-serialized JSON string
- `createdAt` — Timestamp for FIFO ordering

## Components

### Interfaces

- `PersistentEvaluationStorage` — Load/persist/clear cached evaluations by key (string-based API)
- `PersistentEventsStorage` — Push/pop/count queued tracker events (string-based API)
- `SerializedEvaluation` — Wrapper for (flagName, json) pairs

### Implementations

- `RoomEvaluationPersistence` — Room-backed evaluation storage; uses `GeneralInfoDao` for per-key metadata
- `RoomEventsPersistence` — Room-backed event queue storage

### Entities

- `EvaluationEntity` — Evaluation record with key, flagName, and body
- `GeneralInfoEntity` — General-purpose key-value record (used for per-key metadata such as changeNumber)
- `EventEntity` — Event queue record

## Dependencies

- **AndroidX Room** (2.4.3) — Database layer
- **Kotlinx Coroutines** (1.8.1) — Async operations

**No domain dependencies**

## Build

```bash
./gradlew :persistence:build
./gradlew :persistence:testDebugUnitTest
```

## Usage

Module is consumed internally by the `api` module. The API module handles serialization before calling persistence and deserialization after loading from persistence.

**Example:**
```kotlin
// Consumer serializes Key before persisting
val key = Key("user-123", "bucket-456")
val serializedKey = serializeKey(key)  // e.g., JSON: {"matchingKey":"user-123","bucketingKey":"bucket-456"}

// Consumer serializes evaluations
val serialized = evaluations.map { eval ->
    SerializedEvaluation(eval.result.flag, json.encodeToString(eval))
}

persistence.persistForKey(targetHash, 12345L, serialized)

// Consumer deserializes after loading
val data = persistence.loadForKey(targetHash)
val evaluations = data?.evaluations?.map { jsonString ->
    json.decodeFromString<StoredEvaluation>(jsonString)
}
```

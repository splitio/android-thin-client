# Persistence Domain Module

Domain layer over the `persistence` module, wiring serialization, hashing, and lifecycle callbacks for evaluation and event storage.

## Purpose

Bridges the thin-client domain model (`EvaluationKey`, `StoredEvaluation`, `TrackerEvent`) with the dumb string-based `persistence` storage layer. Responsibilities:

- Computing a deterministic hash key per `EvaluationKey` (Key + Attributes) via `TargetHasher`
- Serializing/deserializing `StoredEvaluation` objects
- Serializing/deserializing `TrackerEvent` objects
- Exposing lifecycle callbacks for load and write operations

## Key Design Decisions

### Target Hashing

Evaluations are stored **per Target** (Key + Attributes), not just per Key. Two users with the same `matchingKey` but different attributes will have different cached evaluations (since attributes influence evaluation results). `TargetHasher` produces a SHA-256 hash of the combined Key + sorted Attributes, used as the opaque DB key. This means:

- Attributes are **never stored in the DB** — they are always provided by the caller
- On load, the caller's `EvaluationKey` is used directly — no reconstruction needed
- Different attribute combinations produce different DB entries

## Components

### Evaluation Persistence

- **`EvaluationPersistenceManager`** — Interface: `loadLocal(evalKey)` and `persistAsync(evalKey, changeNumber, evaluations)`
- **`DefaultEvaluationPersistenceManager`** — Implementation using `TargetHasher` and `StoredEvaluationSerializer`
- **`TargetHasher`** — Produces a deterministic SHA-256 hash from `EvaluationKey` (Key + sorted Attributes)
- **`StoredEvaluationSerializer`** — JSON serialization for `StoredEvaluation` objects
- **`EvaluationPersistenceCallbacks`** — Load/write lifecycle callbacks

### Event Persistence

- **`PersistentEventsStorage`** — Async push/pop for `TrackerEvent` objects backed by Room
- **`TrackerEventSerializer`** — JSON serialization for `TrackerEvent` objects
- **`EventsPersistenceCallbacks`** — Event push/pop lifecycle callbacks

### Factory

- **`createPersistenceDomainComponents()`** — Top-level factory that builds and wires all components. Returns a `PersistenceDomainComponents` containing an `EvaluationPersistenceManager` and a `RecorderStorage<TrackerEvent>`. Returns no-op in-memory variants when `PersistenceConfig.enabled = false`.

## Dependencies

- **`persistence`** — Room-based storage layer
- **`evaluation`** — `EvaluationKey`, `EvaluationChange`, `StoredEvaluation`
- **`models`** — `Key`, `Target`
- **`events-tracking` / `submitter`** — `TrackerEvent`, `RecorderStorage`
- **Kotlinx Serialization** — JSON encoding/decoding

## Build

```bash
./gradlew :persistence-domain:build
./gradlew :persistence-domain:testDebugUnitTest
```

# observer

Base observer pattern used by internal SDK components to emit lifecycle events without coupling emitters to consumers.

## Key types

- `ObservableEvent` — value type carrying an event `type` string, optional `properties` map, and a `timestamp`.
- `Observer` — `fun interface`; single `notifyEvent(event)` entry point.
- `ObserverRegistry` — manages observer registration (`register`, `unregisterAll`).
- `CompositeObserver` — combines `Observer` + `ObserverRegistry`; fan-out dispatch to all registered observers.
- `DefaultCompositeObserver` — production implementation; thread-safe, with per-observer fault isolation (one observer throwing does not affect others).

## Usage

```kotlin
val composite = DefaultCompositeObserver()

composite.register(Observer { event ->
    if (event.type == "eval_storage_updated") {
        // react to event
    }
})

composite.notifyEvent(ObservableEvent(type = "eval_storage_updated"))

composite.unregisterAll()
```

## Design notes

- **Direct dispatch** — `notifyEvent` is synchronous; observers are responsible for their own threading.
- **Fault isolation** — exceptions thrown by individual observers are caught and swallowed so the remaining observers still receive the event.

## Building as a Maven JAR

Build the JARs:
```bash
mvn clean package
# produces:
#   target/observer-1.0.0.jar
#   target/observer-1.0.0-sources.jar
```

Install to local Maven repository:
```bash
mvn clean install
```

Install to a custom local repository path (e.g. for vendoring):
```bash
mvn install:install-file \
  -Dfile=target/observer-1.0.0.jar \
  -Dsources=target/observer-1.0.0-sources.jar \
  -DpomFile=pom.xml \
  -DgroupId=io.split.client.thin \
  -DartifactId=observer \
  -Dversion=1.0.0 \
  -Dpackaging=jar \
  -DlocalRepositoryPath=/path/to/your/libs/
```

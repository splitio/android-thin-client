package io.split.client.thin.internal.observer

class LoggerObserver(
    private val logger: Logger
) : Observer {

    override fun notifyEvent(event: ObservableEvent) {
        val template = MESSAGES[event.type] ?: event.type
        val message = interpolateProperties(template, event.properties)
        val finalMessage = if (LOG_LEVELS[event.type] == Level.DEBUG && event.properties.containsKey("rawData")) {
            "$message (raw: ${event.properties["rawData"]})"
        } else {
            message
        }
        when (LOG_LEVELS[event.type]) {
            Level.VERBOSE -> logger.verbose(finalMessage)
            Level.DEBUG -> logger.debug(finalMessage)
            Level.INFO -> logger.info(message)
            Level.WARN -> logger.warn(message)
            Level.ERROR -> logger.error(message)
            null -> logger.debug(finalMessage)
        }
    }

    private fun interpolateProperties(template: String, properties: Map<String, String>): String {
        var result = template
        properties.forEach { (key, value) ->
            result = result.replace("[$key]", value)
        }
        return result
    }

    private enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR }

    companion object {
        // Map event type → log level (from spec Appendix I)
        private val LOG_LEVELS = mapOf(
            // Lifecycle
            ObservableEventType.FACTORY_INIT_STARTED to Level.INFO,
            ObservableEventType.FACTORY_INIT_COMPLETED to Level.INFO,
            ObservableEventType.CLIENT_CREATED to Level.DEBUG,
            ObservableEventType.TARGET_SWITCH_STARTED to Level.INFO,
            ObservableEventType.TARGET_SWITCH_COMPLETED to Level.INFO,
            ObservableEventType.DESTROY_STARTED to Level.INFO,
            ObservableEventType.DESTROY_COMPLETE to Level.INFO,
            ObservableEventType.FLUSH_STARTED to Level.INFO,
            ObservableEventType.FLUSH_COMPLETED to Level.INFO,
            ObservableEventType.FLUSH_FAILED to Level.WARN,
            ObservableEventType.EVALUATION_REQUESTED to Level.DEBUG,
            ObservableEventType.SDK_READY_TIMEOUT_REACHED to Level.DEBUG,
            // Auth
            ObservableEventType.JWT_REQUEST_STARTED to Level.DEBUG,
            ObservableEventType.JWT_RETURNED_FROM_STORAGE to Level.DEBUG,
            ObservableEventType.JWT_FETCH_STARTED to Level.DEBUG,
            ObservableEventType.JWT_FETCH_SUCCEEDED to Level.INFO,
            ObservableEventType.JWT_FETCH_FAILED_RETRYABLE to Level.WARN,
            ObservableEventType.JWT_FETCH_FAILED_NON_RETRYABLE to Level.ERROR,
            ObservableEventType.JWT_STORED to Level.DEBUG,
            ObservableEventType.JWT_EXPIRED_OR_INVALID to Level.INFO,
            // HTTP
            ObservableEventType.HTTP_REQUEST_STARTED to Level.DEBUG,
            ObservableEventType.HTTP_REQUEST_SUCCEEDED to Level.DEBUG,
            ObservableEventType.HTTP_REQUEST_FAILED_RETRYABLE to Level.WARN,
            ObservableEventType.HTTP_REQUEST_FAILED_NON_RETRYABLE to Level.ERROR,
            ObservableEventType.HTTP_RETRY_EXHAUSTED to Level.ERROR,
            // Eval sync
            ObservableEventType.RUNTIME_SYNC_MODE_CHANGED to Level.WARN,
            ObservableEventType.EVAL_FETCH_REQUESTED to Level.INFO,
            ObservableEventType.EVAL_FETCH_DEDUPED to Level.DEBUG,
            ObservableEventType.EVAL_FETCH_STARTED to Level.INFO,
            ObservableEventType.EVAL_FETCH_SUCCEEDED to Level.INFO,
            ObservableEventType.EVAL_FETCH_FAILED to Level.WARN,
            ObservableEventType.EVAL_DESERIALIZE_FAILED to Level.ERROR,
            ObservableEventType.EVAL_STORAGE_UPDATED to Level.DEBUG,
            // Existing SDK events
            ObservableEventType.EVALUATIONS_UPDATED to Level.DEBUG,
            // Track events
            ObservableEventType.TRACK_CALLED to Level.DEBUG,
            ObservableEventType.TRACK_DROPPED to Level.DEBUG,
            ObservableEventType.EVENTS_FLUSH_TRIGGERED to Level.DEBUG,
            ObservableEventType.EVENTS_POST_SUCCEEDED to Level.INFO,
            ObservableEventType.EVENTS_POST_FAILED to Level.WARN,
            // App lifecycle
            ObservableEventType.SYNC_PAUSED to Level.INFO,
            ObservableEventType.SYNC_RESUMED to Level.INFO,
            // Polling
            ObservableEventType.POLL_TRIGGER to Level.DEBUG,
            // Streaming
            ObservableEventType.STREAMING_CONNECT_STARTED to Level.DEBUG,
            ObservableEventType.STREAMING_CONNECTED to Level.DEBUG,
            ObservableEventType.STREAMING_DISCONNECTED to Level.DEBUG,
            ObservableEventType.STREAMING_NOTIFICATION_RECEIVED to Level.VERBOSE,
            ObservableEventType.STREAMING_PAUSED to Level.DEBUG,
            ObservableEventType.STREAMING_RESUMED to Level.DEBUG,
        )

        // Map event type → human-readable message (from spec)
        private val MESSAGES = mapOf(
            // Lifecycle
            ObservableEventType.FACTORY_INIT_STARTED to "Init started",
            ObservableEventType.FACTORY_INIT_COMPLETED to "Init completed",
            ObservableEventType.CLIENT_CREATED to "Client created",
            ObservableEventType.TARGET_SWITCH_STARTED to "Target switch started",
            ObservableEventType.TARGET_SWITCH_COMPLETED to "Target switch complete",
            ObservableEventType.DESTROY_STARTED to "Destroy started",
            ObservableEventType.DESTROY_COMPLETE to "Destroy completed",
            ObservableEventType.FLUSH_STARTED to "Flush started for [entity]",
            ObservableEventType.FLUSH_COMPLETED to "Flush completed for [entity]",
            ObservableEventType.FLUSH_FAILED to "Flush failed [entity]",
            ObservableEventType.EVALUATION_REQUESTED to "Evaluation requested for [flagName]",
            ObservableEventType.SDK_READY_TIMEOUT_REACHED to "SDK timeout reached",
            // Auth
            ObservableEventType.JWT_REQUEST_STARTED to "JWT requested",
            ObservableEventType.JWT_RETURNED_FROM_STORAGE to "JWT returned from cache",
            ObservableEventType.JWT_FETCH_STARTED to "Fetching JWT",
            ObservableEventType.JWT_FETCH_SUCCEEDED to "JWT fetched (push enabled: [pushEnabled], connDelaySeconds: [connDelaySeconds])",
            ObservableEventType.JWT_FETCH_FAILED_RETRYABLE to "JWT fetch failed, will retry",
            ObservableEventType.JWT_FETCH_FAILED_NON_RETRYABLE to "JWT fetch failed",
            ObservableEventType.JWT_STORED to "JWT stored",
            ObservableEventType.JWT_EXPIRED_OR_INVALID to "JWT expired/invalid, refreshing",
            // HTTP
            ObservableEventType.HTTP_REQUEST_STARTED to "HTTP [category] [method] started",
            ObservableEventType.HTTP_REQUEST_SUCCEEDED to "HTTP success",
            ObservableEventType.HTTP_REQUEST_FAILED_RETRYABLE to "HTTP failed for [category] (status: [statusCode]), retrying",
            ObservableEventType.HTTP_REQUEST_FAILED_NON_RETRYABLE to "HTTP failed for [category] (status: [statusCode])",
            ObservableEventType.HTTP_RETRY_EXHAUSTED to "Retry attempts exhausted for [category]",
            // Eval sync
            ObservableEventType.RUNTIME_SYNC_MODE_CHANGED to "Runtime sync mode changed from [from] to [to] (reason: [reason])",
            ObservableEventType.EVAL_FETCH_REQUESTED to "Evaluations fetch requested (reason: [reason])[delayMs]",
            ObservableEventType.EVAL_FETCH_DEDUPED to "Evaluations fetch deduped (awaiting fetch in progress)",
            ObservableEventType.EVAL_FETCH_STARTED to "Evaluations fetch started",
            ObservableEventType.EVAL_FETCH_SUCCEEDED to "Evaluations fetch succeeded",
            ObservableEventType.EVAL_FETCH_FAILED to "Evaluations fetch failed",
            ObservableEventType.EVAL_DESERIALIZE_FAILED to "Failed to parse evaluations response",
            ObservableEventType.EVAL_STORAGE_UPDATED to "Evaluations applied to in-memory storage",
            // Existing SDK events
            ObservableEventType.EVALUATIONS_UPDATED to "Evaluations updated",
            // Track events
            ObservableEventType.TRACK_CALLED to "Track called",
            ObservableEventType.TRACK_DROPPED to "Track dropped",
            ObservableEventType.EVENTS_FLUSH_TRIGGERED to "Events flush triggered (reason: [reason])",
            ObservableEventType.EVENTS_POST_SUCCEEDED to "Events posted (count: [count])",
            ObservableEventType.EVENTS_POST_FAILED to "Events post failed",
            // App lifecycle
            ObservableEventType.SYNC_PAUSED to "Sync paused (reason: app background)",
            ObservableEventType.SYNC_RESUMED to "Sync resumed",
            // Polling
            ObservableEventType.POLL_TRIGGER to "Polling (rate: [rate])",
            // Streaming
            ObservableEventType.STREAMING_CONNECT_STARTED to "Streaming connection started",
            ObservableEventType.STREAMING_CONNECTED to "Streaming connected",
            ObservableEventType.STREAMING_DISCONNECTED to "Streaming disconnected",
            ObservableEventType.STREAMING_NOTIFICATION_RECEIVED to "Streaming notification received ([notificationType])",
            ObservableEventType.STREAMING_PAUSED to "Streaming paused",
            ObservableEventType.STREAMING_RESUMED to "Streaming resumed",
        )
    }
}

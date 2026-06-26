package io.split.client.thin.events

interface EventSubmissionCoordinator {
    fun triggerSubmission(reason: EventFlushReason)

    suspend fun flush()
}

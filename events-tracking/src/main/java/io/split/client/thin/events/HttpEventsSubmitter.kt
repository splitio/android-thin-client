package io.split.client.thin.events

import io.split.android.client.network.HttpResponse
import io.split.android.client.submitter.RecorderException
import io.split.android.client.submitter.RecorderSubmitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class HttpEventsSubmitter(
    private val postEvents: suspend (String) -> HttpResponse
) : RecorderSubmitter<String> {

    override fun execute(data: String) {
        val response = runBlocking(Dispatchers.IO) { postEvents(data) }

        if (!response.isSuccess) {
            throw RecorderException(
                /* message = */ "Events submission failed with status ${response.httpStatus}",
                /* httpStatus = */ response.httpStatus,
                /* retryable = */ false // Always false; to be managed by retryable http client policy.
            )
        }
    }
}

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
            val isRetryable = response.httpStatus >= 500
            throw RecorderException(
                "Events submission failed with status ${response.httpStatus}",
                response.httpStatus,
                isRetryable
            )
        }
    }
}

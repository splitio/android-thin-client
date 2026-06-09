package io.split.client.thin.events

import io.split.android.client.submitter.RecorderException
import io.split.android.client.submitter.RecorderSubmitter
import io.split.client.thin.http.contracts.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class HttpEventsSubmitter(
    private val postEvents: suspend (String) -> HttpResponse
) : RecorderSubmitter<String> {

    override fun execute(data: String) {
        val response = try {
            runBlocking(Dispatchers.IO) { postEvents(data) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RecorderException(
                /* message = */ "Events submission failed: ${e.message}",
                /* httpStatus = */ -1,
                /* retryable = */ false
            )
        }

        if (!response.isSuccess) {
            throw RecorderException(
                /* message = */ "Events submission failed with status ${response.httpStatus}",
                /* httpStatus = */ response.httpStatus,
                /* retryable = */ false // Always false; to be managed by retryable http client policy.
            )
        }
    }
}

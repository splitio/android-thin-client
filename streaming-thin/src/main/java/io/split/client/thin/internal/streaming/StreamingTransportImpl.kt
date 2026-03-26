package io.split.client.thin.internal.streaming

import io.split.android.client.network.HttpMethod
import io.split.android.client.network.HttpResponse
import io.split.android.client.service.sseclient.spi.StreamingTransport
import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI

internal class StreamingTransportImpl(
    private val httpClient: RetryableHttpClient
) : StreamingTransport {

    override fun connect(uri: URI): StreamingTransport.StreamingConnection {
        return StreamingConnectionImpl(uri)
    }

    private inner class StreamingConnectionImpl(
        private val uri: URI
    ) : StreamingTransport.StreamingConnection {

        private var response: HttpResponse? = null

        override fun execute(): StreamingTransport.StreamingResponse {
            val request = HttpRequestDescriptor(
                uri = uri,
                method = HttpMethod.GET,
                body = null,
                headers = mapOf("Accept" to "text/event-stream")
            )

            response = runBlocking {
                httpClient.execute(request, RequestCategory.SSE)
            }
            return StreamingResponseImpl(response!!)
        }

        override fun close() {
            // HttpResponse doesn't have close method, cleanup handled by response itself
        }
    }

    private class StreamingResponseImpl(
        private val httpResponse: HttpResponse
    ) : StreamingTransport.StreamingResponse {

        override fun isSuccess(): Boolean = httpResponse.isSuccess()

        override fun getHttpStatus(): Int = httpResponse.getHttpStatus()

        override fun isClientRelatedError(): Boolean = httpResponse.isClientRelatedError()

        override fun getBufferedReader(): BufferedReader? {
            val data = httpResponse.getData() ?: return null
            return BufferedReader(InputStreamReader(data.byteInputStream()))
        }

        override fun close() {
            // HttpResponse doesn't have close method, cleanup handled internally
        }
    }
}

package io.split.client.thin.internal.streaming

import io.split.android.client.network.HttpClient
import io.split.android.client.network.HttpStreamRequest
import io.split.android.client.network.HttpStreamResponse
import io.split.android.client.service.sseclient.spi.StreamingTransport
import java.io.BufferedReader
import java.net.URI

internal class StreamingTransportImpl(
    private val httpClient: HttpClient
) : StreamingTransport {

    override fun connect(uri: URI): StreamingTransport.StreamingConnection {
        return StreamingConnectionImpl(uri)
    }

    private inner class StreamingConnectionImpl(
        private val uri: URI
    ) : StreamingTransport.StreamingConnection {

        private var streamRequest: HttpStreamRequest? = null

        override fun execute(): StreamingTransport.StreamingResponse {
            val request = httpClient.streamRequest(uri)
            request.addHeader("Accept", "text/event-stream")
            streamRequest = request
            val response = request.execute()
            return StreamingResponseImpl(response)
        }

        override fun close() {
            streamRequest?.close()
        }
    }

    private class StreamingResponseImpl(
        private val httpStreamResponse: HttpStreamResponse
    ) : StreamingTransport.StreamingResponse {

        override fun isSuccess(): Boolean = httpStreamResponse.isSuccess()

        override fun getHttpStatus(): Int = httpStreamResponse.getHttpStatus()

        override fun isClientRelatedError(): Boolean = httpStreamResponse.isClientRelatedError()

        override fun getBufferedReader(): BufferedReader? = httpStreamResponse.getBufferedReader()

        override fun close() {
            httpStreamResponse.close()
        }
    }
}

package io.split.client.thin.internal.streaming

import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedReader
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.InputStreamReader
import java.net.URI

class StreamingTransportImplTest {

    @Test
    fun `connect creates StreamingConnection with correct URI`() {
        val client = FakeHttpClient()
        val transport = StreamingTransportImpl(client)
        val uri = URI("https://streaming.split.io/sse?token=abc")

        val connection = transport.connect(uri)

        assertNotNull(connection)
    }

    @Test
    fun `execute calls streamRequest with correct URI`() {
        val client = FakeHttpClient()
        val transport = StreamingTransportImpl(client)
        val uri = URI("https://streaming.split.io/sse?token=xyz")

        val connection = transport.connect(uri)
        connection.execute()

        assertEquals(uri, client.lastStreamRequestUri)
    }

    @Test
    fun `execute adds Accept header for SSE`() {
        val fakeRequest = FakeHttpStreamRequest(FakeHttpStreamResponse(200))
        val client = FakeHttpClient { _ -> fakeRequest }
        val transport = StreamingTransportImpl(client)

        val connection = transport.connect(URI("https://test.io/sse"))
        connection.execute()

        assertEquals("text/event-stream", fakeRequest.headers["Accept"])
    }

    @Test
    fun `execute returns StreamingResponse with success status`() {
        val client = FakeHttpClient { _ ->
            FakeHttpStreamRequest(FakeHttpStreamResponse(200))
        }
        val transport = StreamingTransportImpl(client)

        val connection = transport.connect(URI("https://test.io/sse"))
        val response = connection.execute()

        assertTrue(response.isSuccess())
        assertEquals(200, response.getHttpStatus())
    }

    @Test
    fun `execute returns StreamingResponse with error status`() {
        val client = FakeHttpClient { _ ->
            FakeHttpStreamRequest(FakeHttpStreamResponse(500))
        }
        val transport = StreamingTransportImpl(client)

        val connection = transport.connect(URI("https://test.io/sse"))
        val response = connection.execute()

        assertFalse(response.isSuccess())
        assertEquals(500, response.getHttpStatus())
    }

    @Test
    fun `StreamingResponse isClientRelatedError delegates to HttpStreamResponse`() {
        val client = FakeHttpClient { _ ->
            FakeHttpStreamRequest(FakeHttpStreamResponse(404, isClientError = true))
        }
        val transport = StreamingTransportImpl(client)

        val connection = transport.connect(URI("https://test.io/sse"))
        val response = connection.execute()

        assertTrue(response.isClientRelatedError())
    }

    @Test
    fun `StreamingResponse getBufferedReader returns live reader from stream`() {
        // Use piped streams to simulate a live SSE connection
        val pipedOut = PipedOutputStream()
        val pipedIn = PipedInputStream(pipedOut)
        val liveReader = BufferedReader(InputStreamReader(pipedIn))

        val client = FakeHttpClient { _ ->
            FakeHttpStreamRequest(FakeHttpStreamResponse(200, bufferedReader = liveReader))
        }
        val transport = StreamingTransportImpl(client)

        val connection = transport.connect(URI("https://test.io/sse"))
        val response = connection.execute()
        val reader = response.getBufferedReader()

        assertNotNull(reader)

        // Write data to the pipe AFTER getting the reader — proves it's a live stream
        pipedOut.write("data: hello\n".toByteArray())
        pipedOut.flush()
        assertEquals("data: hello", reader?.readLine())

        pipedOut.write("data: world\n".toByteArray())
        pipedOut.flush()
        assertEquals("data: world", reader?.readLine())

        pipedOut.close()
    }

    @Test
    fun `StreamingResponse getBufferedReader returns null when stream has no reader`() {
        val client = FakeHttpClient { _ ->
            FakeHttpStreamRequest(FakeHttpStreamResponse(200, bufferedReader = null))
        }
        val transport = StreamingTransportImpl(client)

        val connection = transport.connect(URI("https://test.io/sse"))
        val response = connection.execute()

        assertNull(response.getBufferedReader())
    }

    @Test
    fun `connection close closes the underlying stream request`() {
        val fakeRequest = FakeHttpStreamRequest(FakeHttpStreamResponse(200))
        val client = FakeHttpClient { _ -> fakeRequest }
        val transport = StreamingTransportImpl(client)

        val connection = transport.connect(URI("https://test.io/sse"))
        connection.execute()
        connection.close()

        assertTrue(fakeRequest.closeCalled)
    }

    @Test
    fun `response close closes the underlying stream response`() {
        val fakeResponse = FakeHttpStreamResponse(200)
        val client = FakeHttpClient { _ ->
            FakeHttpStreamRequest(fakeResponse)
        }
        val transport = StreamingTransportImpl(client)

        val connection = transport.connect(URI("https://test.io/sse"))
        val response = connection.execute()
        response.close()

        assertTrue(fakeResponse.closeCalled)
    }
}

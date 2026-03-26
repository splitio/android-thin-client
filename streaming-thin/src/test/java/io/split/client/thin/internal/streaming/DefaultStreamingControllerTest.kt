package io.split.client.thin.internal.streaming

import io.split.client.thin.internal.auth.AuthProvider
import io.split.client.thin.internal.auth.JwtCredential
import io.split.client.thin.internal.secure.EvaluationTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultStreamingControllerTest {

    @Test
    fun `delegates to connection manager without error`() = runTest {
        val fetchCoordinator = FakeEvaluationFetchCoordinator()
        val manager = StreamingConnectionManager(
            streamingUrl = "https://test.io",
            target = EvaluationTarget("test", null, null),
            fetchCoordinator = fetchCoordinator,
            eventSourceClientProvider = { FakeEventSourceClient() },
            authProvider = FakeAuthProvider(),
            backoffCounter = FakeBackoffCounter(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            onOccupancyZero = {},
        )
        val controller = DefaultStreamingController(manager)

        // Verify all methods delegate without error
        controller.start()
        controller.pause()
        controller.resume()
        controller.stop()
    }
}

private class FakeAuthProvider : AuthProvider<EvaluationTarget> {
    override suspend fun credential(target: EvaluationTarget) =
        JwtCredential("fake-token", Long.MAX_VALUE, true)

    override suspend fun invalidate(target: EvaluationTarget) = Unit
}

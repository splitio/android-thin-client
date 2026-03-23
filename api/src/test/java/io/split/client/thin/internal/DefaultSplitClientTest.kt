package io.split.client.thin.internal

import io.split.android.client.tracker.Tracker
import io.split.client.thin.Key
import io.split.client.thin.Target
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class DefaultSplitClientTest {

    private lateinit var tracker: Tracker
    private lateinit var target: Target
    private lateinit var client: DefaultSplitClient

    @Before
    fun setUp() {
        tracker = mock(Tracker::class.java)
        target = Target(Key("user-1"))
        client = DefaultSplitClient(target, tracker)
    }

    @Test
    fun `track delegates to tracker with matching key`() {
        client.track("user", "purchase", 9.99, null)

        verify(tracker).track(
            eq("user-1"),
            eq("user"),
            eq("purchase"),
            eq(9.99),
            eq(null),
            eq(true),
        )
    }

    @Test
    fun `track uses 0 dot 0 when value is null`() {
        client.track("user", "purchase", null, null)

        verify(tracker).track(
            eq("user-1"),
            eq("user"),
            eq("purchase"),
            eq(0.0),
            eq(null),
            eq(true),
        )
    }

    @Test
    fun `flush is no-op`() = runTest {
        client.flush()
        verify(tracker, never()).enableTracking(false)
        verify(tracker, never()).enableTracking(true)
    }

    @Test
    fun `destroy disables tracking`() = runTest {
        client.destroy()
        verify(tracker).enableTracking(false)
    }

    @Test
    fun `setTarget updates key used for subsequent track calls`() = runTest {
        val newTarget = Target(Key("user-2"))
        client.setTarget(newTarget)

        client.track("user", "purchase", 0.0, null)

        verify(tracker).track(
            eq("user-2"),
            eq("user"),
            eq("purchase"),
            eq(0.0),
            eq(null),
            eq(true),
        )
    }
}

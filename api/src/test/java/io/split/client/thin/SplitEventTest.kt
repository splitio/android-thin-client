package io.split.client.thin

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitEventTest {

    @Test
    fun `split event values are stable`() {
        assertEquals(
            listOf(
                SplitEvent.SDK_READY,
                SplitEvent.SDK_READY_FROM_CACHE,
                SplitEvent.SDK_READY_TIMEOUT,
                SplitEvent.SDK_UPDATE,
            ),
            SplitEvent.entries,
        )
    }

    @Test
    fun `split event can be resolved from name`() {
        assertEquals(SplitEvent.SDK_READY, SplitEvent.valueOf("SDK_READY"))
        assertEquals(SplitEvent.SDK_UPDATE, SplitEvent.valueOf("SDK_UPDATE"))
    }
}

package io.split.client.thin.internal

import io.split.client.thin.Key
import io.split.client.thin.Target
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NoOpSplitFactoryTest {

    private val target = Target(Key("user-1"), trafficType = "user")

    @Test
    fun `getClient returns NoOpSplitClient`() {
        assertSame(NoOpSplitClient, NoOpSplitFactory.getClient(null))
        assertSame(NoOpSplitClient, NoOpSplitFactory.getClient(target))
    }

    @Test
    fun `getManager returns NoOpSplitManager with empty flagNames`() {
        val manager = NoOpSplitFactory.getManager()
        assertSame(NoOpSplitManager, manager)
        assertEquals(emptyList<String>(), manager.flagNames)
    }

    @Test
    fun `destroy is a no-op`() = runBlocking {
        NoOpSplitFactory.destroy()
    }

    @Test
    @Suppress("DEPRECATION_ERROR")
    fun `destroyAsync invokes callback with null error`() {
        var captured: Throwable? = Throwable("sentinel")
        var called = false
        NoOpSplitFactory.destroyAsync { error ->
            captured = error
            called = true
        }
        assertTrue(called)
        assertEquals(null, captured)
    }

    @Test
    fun `client getTreatment returns control`() {
        val result = NoOpSplitClient.getTreatment("flag_a")
        assertEquals("flag_a", result.flag)
        assertEquals("control", result.treatment)
    }

    @Test
    fun `client getTreatments returns control for each flag`() {
        val results = NoOpSplitClient.getTreatments(listOf("a", "b"))
        assertEquals(2, results.size)
        assertEquals("a", results[0].flag)
        assertEquals("control", results[0].treatment)
        assertEquals("b", results[1].flag)
        assertEquals("control", results[1].treatment)
    }

    @Test
    fun `client getTreatmentsByFlagSets returns empty`() {
        assertEquals(emptyList<Any>(), NoOpSplitClient.getTreatmentsByFlagSets(listOf("set_a")))
    }

    @Test
    fun `client setTarget track addEventListener flush destroy do not throw`() = runBlocking {
        NoOpSplitClient.setTarget(target)
        NoOpSplitClient.track("event")
        NoOpSplitClient.track("event", 1.0, mapOf("k" to "v"))
        NoOpSplitClient.addEventListener(io.split.client.thin.SplitEventListener())
        NoOpSplitClient.flush()
        NoOpSplitClient.destroy()
        assertNotNull(NoOpSplitClient)
    }

    @Test
    @Suppress("DEPRECATION_ERROR")
    fun `client async callbacks invoke with null error`() {
        var destroyErr: Throwable? = Throwable()
        var flushErr: Throwable? = Throwable()
        NoOpSplitClient.destroyAsync { destroyErr = it }
        NoOpSplitClient.flushAsync { flushErr = it }
        assertEquals(null, destroyErr)
        assertEquals(null, flushErr)
    }
}

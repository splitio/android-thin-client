package io.split.client.thin.internal.sdkevents

import io.harness.events.EventsManager
import io.split.client.thin.SdkReadyMetadata
import io.split.client.thin.SdkUpdateMetadata
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitEvent
import io.split.client.thin.SplitEventListener
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import io.harness.events.EventHandler
import org.mockito.Mockito.atLeastOnce

@Suppress("UNCHECKED_CAST")
class SplitEventListenerAdapterTest {

    private lateinit var eventsManager: EventsManager<SplitEvent, SdkInternalEvent, Any?>
    private lateinit var client: SplitClient
    private lateinit var listener: SplitEventListener

    @Before
    fun setUp() {
        eventsManager = mock(EventsManager::class.java) as EventsManager<SplitEvent, SdkInternalEvent, Any?>
        client = mock(SplitClient::class.java)
        listener = mock(SplitEventListener::class.java)
    }

    @Test
    fun `registerAll registers handler for SDK_READY`() {
        SplitEventListenerAdapter(listener, client).registerAll(eventsManager)

        verify(eventsManager).register(
            org.mockito.ArgumentMatchers.eq(SplitEvent.SDK_READY),
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun `registerAll registers handler for SDK_READY_FROM_CACHE`() {
        SplitEventListenerAdapter(listener, client).registerAll(eventsManager)

        verify(eventsManager).register(
            org.mockito.ArgumentMatchers.eq(SplitEvent.SDK_READY_FROM_CACHE),
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun `registerAll registers handler for SDK_READY_TIMEOUT`() {
        SplitEventListenerAdapter(listener, client).registerAll(eventsManager)

        verify(eventsManager).register(
            org.mockito.ArgumentMatchers.eq(SplitEvent.SDK_READY_TIMEOUT),
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun `registerAll registers handler for SDK_UPDATE`() {
        SplitEventListenerAdapter(listener, client).registerAll(eventsManager)

        verify(eventsManager).register(
            org.mockito.ArgumentMatchers.eq(SplitEvent.SDK_UPDATE),
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun `SDK_READY handler calls onReady on listener`() {
        val handlerCaptor = ArgumentCaptor.forClass(EventHandler::class.java) as ArgumentCaptor<EventHandler<SplitEvent, Any?>>
        SplitEventListenerAdapter(listener, client).registerAll(eventsManager)

        verify(eventsManager, atLeastOnce()).register(
            org.mockito.ArgumentMatchers.eq(SplitEvent.SDK_READY),
            handlerCaptor.capture()
        )

        val metadata = SdkReadyMetadata()
        handlerCaptor.value.handle(SplitEvent.SDK_READY, metadata)

        verify(listener).onReady(client, metadata)
    }

    @Test
    fun `SDK_UPDATE handler calls onUpdate on listener`() {
        val handlerCaptor = ArgumentCaptor.forClass(EventHandler::class.java) as ArgumentCaptor<EventHandler<SplitEvent, Any?>>
        SplitEventListenerAdapter(listener, client).registerAll(eventsManager)

        verify(eventsManager, atLeastOnce()).register(
            org.mockito.ArgumentMatchers.eq(SplitEvent.SDK_UPDATE),
            handlerCaptor.capture()
        )

        val metadata = SdkUpdateMetadata()
        handlerCaptor.value.handle(SplitEvent.SDK_UPDATE, metadata)

        verify(listener).onUpdate(client, metadata)
    }

    @Test
    fun `SDK_READY_FROM_CACHE handler calls onReadyFromCache on listener`() {
        val handlerCaptor = ArgumentCaptor.forClass(EventHandler::class.java) as ArgumentCaptor<EventHandler<SplitEvent, Any?>>
        SplitEventListenerAdapter(listener, client).registerAll(eventsManager)

        verify(eventsManager, atLeastOnce()).register(
            org.mockito.ArgumentMatchers.eq(SplitEvent.SDK_READY_FROM_CACHE),
            handlerCaptor.capture()
        )

        val metadata = SdkReadyMetadata()
        handlerCaptor.value.handle(SplitEvent.SDK_READY_FROM_CACHE, metadata)

        verify(listener).onReadyFromCache(client, metadata)
    }
}

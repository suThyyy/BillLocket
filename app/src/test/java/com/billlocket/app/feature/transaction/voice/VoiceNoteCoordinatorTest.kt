package com.billlocket.app.feature.transaction.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceNoteCoordinatorTest {

    private class FakeVoiceNoteTranscriber(
        var available: Boolean = true,
    ) : VoiceNoteTranscriber {
        private val _events = MutableSharedFlow<VoiceTranscriberEvent>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        override val events: Flow<VoiceTranscriberEvent> = _events.asSharedFlow()

        var startedCount = 0
        var stoppedCount = 0
        var cancelledCount = 0
        var closedCount = 0
        var lastLocaleTag: String? = null

        override fun isAvailable(): Boolean = available

        override fun start(localeTag: String) {
            startedCount++
            lastLocaleTag = localeTag
        }

        override fun stop() {
            stoppedCount++
        }

        override fun cancel() {
            cancelledCount++
        }

        override fun close() {
            closedCount++
        }

        fun emitEvent(event: VoiceTranscriberEvent) {
            _events.tryEmit(event)
        }
    }

    @Test
    fun `initial state is Idle when transcriber is available`() = runTest {
        val fake = FakeVoiceNoteTranscriber(available = true)
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)
        runCurrent()
        assertEquals(VoiceNoteState.Idle, coordinator.state.value)
    }

    @Test
    fun `initial state is Unavailable when transcriber is not available`() = runTest {
        val fake = FakeVoiceNoteTranscriber(available = false)
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)
        runCurrent()
        assertEquals(VoiceNoteState.Unavailable, coordinator.state.value)
    }

    @Test
    fun `mic tapped without permission transitions to RequestingPermission`() = runTest {
        val fake = FakeVoiceNoteTranscriber()
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)
        var permissionRequested = false

        coordinator.onMicTapped(
            hasPermission = false,
            permanentlyDenied = false,
            localeTag = "vi-VN",
            onRequestPermission = { permissionRequested = true },
        )

        runCurrent()
        assertTrue(permissionRequested)
        assertEquals(VoiceNoteState.RequestingPermission, coordinator.state.value)
        assertEquals(0, fake.startedCount)
    }

    @Test
    fun `permission granted starts listening session with correct locale tag`() = runTest {
        val fake = FakeVoiceNoteTranscriber()
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)

        coordinator.onMicTapped(
            hasPermission = false,
            permanentlyDenied = false,
            localeTag = "vi-VN",
            onRequestPermission = {},
        )

        coordinator.onPermissionResult(granted = true, permanentlyDenied = false, localeTag = "vi-VN")

        runCurrent()
        assertEquals(VoiceNoteState.Listening(partialText = ""), coordinator.state.value)
        assertEquals(1, fake.startedCount)
        assertEquals("vi-VN", fake.lastLocaleTag)
    }

    @Test
    fun `permission denied transitions to Error PermissionDenied`() = runTest {
        val fake = FakeVoiceNoteTranscriber()
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)

        coordinator.onMicTapped(
            hasPermission = false,
            permanentlyDenied = false,
            localeTag = "en-US",
            onRequestPermission = {},
        )

        coordinator.onPermissionResult(granted = false, permanentlyDenied = false, localeTag = "en-US")

        runCurrent()
        assertEquals(VoiceNoteState.Error(VoiceRecognitionError.PermissionDenied), coordinator.state.value)
        assertEquals(0, fake.startedCount)
    }

    @Test
    fun `permanently denied transitions to Error PermissionPermanentlyDenied`() = runTest {
        val fake = FakeVoiceNoteTranscriber()
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)

        coordinator.onMicTapped(
            hasPermission = false,
            permanentlyDenied = true,
            localeTag = "en-US",
            onRequestPermission = {},
        )

        runCurrent()
        assertEquals(VoiceNoteState.Error(VoiceRecognitionError.PermissionPermanentlyDenied), coordinator.state.value)
        assertEquals(0, fake.startedCount)
    }

    @Test
    fun `partial results update Listening state text`() = runTest {
        val fake = FakeVoiceNoteTranscriber()
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)

        coordinator.onMicTapped(hasPermission = true, permanentlyDenied = false, localeTag = "vi-VN", onRequestPermission = {})
        runCurrent()
        assertEquals(VoiceNoteState.Listening(""), coordinator.state.value)

        fake.emitEvent(VoiceTranscriberEvent.PartialResult("mua"))
        runCurrent()
        assertEquals(VoiceNoteState.Listening("mua"), coordinator.state.value)

        fake.emitEvent(VoiceTranscriberEvent.PartialResult("mua cà phê"))
        runCurrent()
        assertEquals(VoiceNoteState.Listening("mua cà phê"), coordinator.state.value)
    }

    @Test
    fun `stop transitions to Processing and calls transcriber stop`() = runTest {
        val fake = FakeVoiceNoteTranscriber()
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)

        coordinator.onMicTapped(hasPermission = true, permanentlyDenied = false, localeTag = "vi-VN", onRequestPermission = {})
        coordinator.stop()

        runCurrent()
        assertEquals(VoiceNoteState.Processing, coordinator.state.value)
        assertEquals(1, fake.stoppedCount)
    }

    @Test
    fun `late listening and partial callbacks cannot regress Processing state`() = runTest {
        val fake = FakeVoiceNoteTranscriber()
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)

        coordinator.onMicTapped(hasPermission = true, permanentlyDenied = false, localeTag = "vi-VN", onRequestPermission = {})
        coordinator.stop()
        runCurrent()
        assertEquals(VoiceNoteState.Processing, coordinator.state.value)

        fake.emitEvent(VoiceTranscriberEvent.Ready)
        fake.emitEvent(VoiceTranscriberEvent.Listening)
        fake.emitEvent(VoiceTranscriberEvent.PartialResult("late partial"))
        runCurrent()

        assertEquals(VoiceNoteState.Processing, coordinator.state.value)
    }

    @Test
    fun `final result transitions to Completed state`() = runTest {
        val fake = FakeVoiceNoteTranscriber()
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)

        coordinator.onMicTapped(hasPermission = true, permanentlyDenied = false, localeTag = "vi-VN", onRequestPermission = {})
        runCurrent()
        fake.emitEvent(VoiceTranscriberEvent.FinalResult("bánh mì pate"))
        runCurrent()

        assertEquals(VoiceNoteState.Completed("bánh mì pate"), coordinator.state.value)

        coordinator.dismissCompleted()
        runCurrent()
        assertEquals(VoiceNoteState.Idle, coordinator.state.value)
    }

    @Test
    fun `recognition error transitions to Error state`() = runTest {
        val fake = FakeVoiceNoteTranscriber()
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)

        coordinator.onMicTapped(hasPermission = true, permanentlyDenied = false, localeTag = "vi-VN", onRequestPermission = {})
        runCurrent()
        fake.emitEvent(VoiceTranscriberEvent.Error(VoiceRecognitionError.NoSpeechMatch))
        runCurrent()

        assertEquals(VoiceNoteState.Error(VoiceRecognitionError.NoSpeechMatch), coordinator.state.value)

        coordinator.dismissError()
        runCurrent()
        assertEquals(VoiceNoteState.Idle, coordinator.state.value)
    }

    @Test
    fun `cancel is idempotent and returns to Idle`() = runTest {
        val fake = FakeVoiceNoteTranscriber()
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)

        coordinator.onMicTapped(hasPermission = true, permanentlyDenied = false, localeTag = "vi-VN", onRequestPermission = {})
        coordinator.cancel()
        runCurrent()

        assertEquals(VoiceNoteState.Idle, coordinator.state.value)
        assertEquals(1, fake.cancelledCount)

        // Second cancel does not crash and is idempotent
        coordinator.cancel()
        runCurrent()
        assertEquals(1, fake.cancelledCount)
    }

    @Test
    fun `rapid repeated mic taps do not create multiple sessions`() = runTest {
        val fake = FakeVoiceNoteTranscriber()
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)

        coordinator.onMicTapped(hasPermission = true, permanentlyDenied = false, localeTag = "vi-VN", onRequestPermission = {})
        coordinator.onMicTapped(hasPermission = true, permanentlyDenied = false, localeTag = "vi-VN", onRequestPermission = {})
        coordinator.onMicTapped(hasPermission = true, permanentlyDenied = false, localeTag = "vi-VN", onRequestPermission = {})
        runCurrent()

        assertEquals(1, fake.startedCount)
    }

    @Test
    fun `late callbacks after cancel are safely ignored`() = runTest {
        val fake = FakeVoiceNoteTranscriber()
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)

        coordinator.onMicTapped(hasPermission = true, permanentlyDenied = false, localeTag = "vi-VN", onRequestPermission = {})
        coordinator.cancel()
        runCurrent()
        assertEquals(VoiceNoteState.Idle, coordinator.state.value)

        // Late callbacks arrive from background
        fake.emitEvent(VoiceTranscriberEvent.PartialResult("late text"))
        runCurrent()
        assertEquals(VoiceNoteState.Idle, coordinator.state.value)

        fake.emitEvent(VoiceTranscriberEvent.FinalResult("late final"))
        runCurrent()
        assertEquals(VoiceNoteState.Idle, coordinator.state.value)
    }

    @Test
    fun `late cancelled callback cannot terminate a newly started session`() = runTest {
        val fake = FakeVoiceNoteTranscriber()
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)

        coordinator.onMicTapped(hasPermission = true, permanentlyDenied = false, localeTag = "vi-VN", onRequestPermission = {})
        coordinator.cancel()
        coordinator.onMicTapped(hasPermission = true, permanentlyDenied = false, localeTag = "en-US", onRequestPermission = {})
        runCurrent()
        assertEquals(VoiceNoteState.Listening(""), coordinator.state.value)

        fake.emitEvent(VoiceTranscriberEvent.Cancelled)
        runCurrent()

        assertEquals(VoiceNoteState.Listening(""), coordinator.state.value)
    }

    @Test
    fun `close destroys transcriber and transitions to Unavailable`() = runTest {
        val fake = FakeVoiceNoteTranscriber()
        val coordinator = VoiceNoteCoordinator(fake, backgroundScope)

        coordinator.onMicTapped(hasPermission = true, permanentlyDenied = false, localeTag = "vi-VN", onRequestPermission = {})
        coordinator.close()
        runCurrent()

        assertEquals(VoiceNoteState.Unavailable, coordinator.state.value)
        assertEquals(1, fake.closedCount)

        // After close, mic taps do nothing
        coordinator.onMicTapped(hasPermission = true, permanentlyDenied = false, localeTag = "vi-VN", onRequestPermission = {})
        runCurrent()
        assertEquals(1, fake.startedCount)
    }
}

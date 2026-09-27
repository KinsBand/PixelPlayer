package com.theveloper.pixelplay.data.recognition

import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.recognition.ambient.AmbientSuggestionController
import com.theveloper.pixelplay.data.recognition.ambient.OnDeviceSpeechRecognition
import com.theveloper.pixelplay.data.repository.HeardSongsRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AmbientSuggestionControllerTest {
    @Test fun `listen starts before speech and restarts after silence but never after stop`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recognizer = mockk<SpeechRecognizer>(relaxed = true)
        val listeners = mutableListOf<RecognitionListener>()
        every { recognizer.setRecognitionListener(capture(listeners)) } just Runs
        val factory = mockk<OnDeviceSpeechRecognition> {
            every { isAvailable() } returns true
            every { create() } returns recognizer
        }
        val player = mockk<DualPlayerEngine>(relaxed = true)
        val prefs = mockk<UserPreferencesRepository> {
            every { ambientDuckingFactorFlow } returns flowOf(.15f)
            every { ambientAudioChimeEnabledFlow } returns flowOf(false)
        }
        val controller = AmbientSuggestionController(factory, player, HeardSongsRepository(), mockk(), mockk(), prefs)
        try {
            controller.start { fail<Unit>(it) }
            verify(exactly = 1) { recognizer.startListening(any()) }
            listeners.last().onBeginningOfSpeech()
            verify { player.duckForAmbientVoice(.15f, 150L) }
            listeners.last().onError(SpeechRecognizer.ERROR_NO_MATCH)
            runCurrent()
            advanceTimeBy(250)
            runCurrent()
            verify(exactly = 2) { recognizer.startListening(any()) }
            val oldListener = listeners.last()
            controller.stop()
            oldListener.onResults(null)
            oldListener.onReadyForSpeech(null)
            advanceTimeBy(31_000)
            runCurrent()
            verify(exactly = 2) { recognizer.startListening(any()) }
            assertEquals("Listen is off", controller.status.value)
        } finally { controller.stop(); Dispatchers.resetMain() }
    }

    @Test fun `missing on device recognizer does not silently send conversations online`() {
        val factory = mockk<OnDeviceSpeechRecognition> { every { isAvailable() } returns false }
        val controller = AmbientSuggestionController(factory, mockk(), HeardSongsRepository(), mockk(), mockk(), mockk())
        var error: String? = null
        controller.start { error = it }
        assertNotNull(error)
        verify(exactly = 0) { factory.create() }
    }
}

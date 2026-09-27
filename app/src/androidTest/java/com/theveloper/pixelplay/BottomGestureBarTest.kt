package com.theveloper.pixelplay

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import com.theveloper.pixelplay.presentation.components.BottomGestureBar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BottomGestureBarTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bottomGestureBar_leftSwipeUp_triggersAudioOutput() {
        var audioOutputTriggered = false
        var queueTriggered = false

        composeTestRule.setContent {
            BottomGestureBar(
                onOpenAudioOutput = { audioOutputTriggered = true },
                onOpenQueue = { queueTriggered = true }
            )
        }

        composeTestRule.onNodeWithContentDescription("Swipe up to open Audio Output settings")
            .performTouchInput {
                swipeUp()
            }

        assertTrue(audioOutputTriggered)
        assertFalse(queueTriggered)
    }

    @Test
    fun bottomGestureBar_rightSwipeUp_triggersQueue() {
        var audioOutputTriggered = false
        var queueTriggered = false

        composeTestRule.setContent {
            BottomGestureBar(
                onOpenAudioOutput = { audioOutputTriggered = true },
                onOpenQueue = { queueTriggered = true }
            )
        }

        composeTestRule.onNodeWithContentDescription("Swipe up to open Playback Queue")
            .performTouchInput {
                swipeUp()
            }

        assertTrue(queueTriggered)
        assertFalse(audioOutputTriggered)
    }

    @Test
    fun bottomGestureBar_diagonalSwipe_doesNotTrigger() {
        var audioOutputTriggered = false
        var queueTriggered = false

        composeTestRule.setContent {
            BottomGestureBar(
                onOpenAudioOutput = { audioOutputTriggered = true },
                onOpenQueue = { queueTriggered = true }
            )
        }

        composeTestRule.onNodeWithContentDescription("Swipe up to open Playback Queue")
            .performTouchInput {
                // Swipe heavily diagonally (dominance check: deltaY < 1.5 * deltaX)
                swipe(
                    start = Offset(0f, 200f),
                    end = Offset(200f, 100f),
                    durationMillis = 200
                )
            }

        assertFalse(queueTriggered)
        assertFalse(audioOutputTriggered)
    }
}

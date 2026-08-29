package com.jacob.pokemonscanner.domain

import com.jacob.pokemonscanner.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GiftWorkflowReducerTest {
    private val settings = GiftAssistantSettings(dryRun = false)
    private val genericTarget = RecognitionTarget(
        NormalizedRect(.1f, .1f, .2f, .2f), .95f, RecognitionSource.OCR, "test target",
    )

    @Test fun mapToFriendsAndConfiguredSortIsExplicit() {
        var decision = GiftWorkflowReducer.reduce(GiftWorkflowSnapshot(), GiftWorkflowEvent.Start, settings)
        assertEquals(GiftAutomationState.VERIFYING_GAME, decision.snapshot.state)

        decision = observe(decision.snapshot, GameScreen.HOME_MAP, targets = mapOf(AutomationAction.OPEN_PROFILE to genericTarget))
        assertEquals(GiftAutomationState.OPENING_PROFILE, decision.snapshot.state)
        assertEquals(AutomationAction.OPEN_PROFILE, decision.instruction?.action)

        decision = observe(decision.snapshot, GameScreen.TRAINER_PROFILE, targets = mapOf(AutomationAction.OPEN_FRIENDS to genericTarget))
        assertEquals(GiftAutomationState.OPENING_FRIENDS, decision.snapshot.state)

        decision = observe(decision.snapshot, GameScreen.FRIENDS_LIST, targets = mapOf(AutomationAction.OPEN_SORT to genericTarget))
        assertEquals(GiftAutomationState.APPLYING_SORT, decision.snapshot.state)
        assertEquals(AutomationAction.OPEN_SORT, decision.instruction?.action)

        decision = GiftWorkflowReducer.reduce(
            decision.snapshot,
            GiftWorkflowEvent.ScreenRecognized(ScreenObservation(
                GameScreen.SORT_MENU,
                .98f,
                RecognitionSource.OCR,
                targets = mapOf(AutomationAction.CLOSE_SORT to genericTarget),
                detectedSortMode = GiftSortMode.GIFT_STATUS,
                detectedSortDirection = GiftSortDirection.DESCENDING,
            )),
            settings,
        )
        assertTrue(decision.snapshot.sortVerified)
        assertEquals(AutomationAction.CLOSE_SORT, decision.instruction?.action)
    }

    @Test fun oneFriendIsOpenedSentAndSessionFingerprintIsProcessed() {
        val candidate = RecognizedFriendCandidate("session-hash", genericTarget)
        var state = GiftWorkflowSnapshot(
            state = GiftAutomationState.SCANNING_FRIENDS,
            sortVerified = true,
        )
        var decision = GiftWorkflowReducer.reduce(
            state,
            GiftWorkflowEvent.ScreenRecognized(ScreenObservation(
                GameScreen.FRIENDS_LIST, .98f, RecognitionSource.OCR, eligibleFriends = listOf(candidate),
            )), settings,
        )
        assertEquals(GiftAutomationState.OPENING_FRIEND, decision.snapshot.state)

        decision = observe(decision.snapshot, GameScreen.GIFT_POSTCARD, targets = mapOf(AutomationAction.OPEN_GIFT to genericTarget))
        decision = observe(decision.snapshot, GameScreen.GIFT_RESULTS, targets = mapOf(AutomationAction.DISMISS_GIFT_RESULTS to genericTarget))
        assertEquals(1, decision.snapshot.giftsOpened)

        decision = GiftWorkflowReducer.reduce(
            decision.snapshot,
            GiftWorkflowEvent.ScreenRecognized(ScreenObservation(
                GameScreen.FRIEND_DETAIL, .98f, RecognitionSource.OCR,
                canSendGift = true,
                targets = mapOf(AutomationAction.SEND_GIFT to genericTarget),
            )), settings,
        )
        assertEquals(GiftAutomationState.SENDING_GIFT, decision.snapshot.state)

        decision = GiftWorkflowReducer.reduce(
            decision.snapshot,
            GiftWorkflowEvent.ScreenRecognized(ScreenObservation(
                GameScreen.GIFT_PICKER, .98f, RecognitionSource.OCR,
                hasGiftToSend = true,
                targets = mapOf(AutomationAction.SELECT_GIFT to genericTarget),
            )), settings,
        )
        decision = observe(decision.snapshot, GameScreen.GIFT_COMPOSE, targets = mapOf(AutomationAction.CONFIRM_SEND to genericTarget))
        decision = GiftWorkflowReducer.reduce(
            decision.snapshot,
            GiftWorkflowEvent.ScreenRecognized(ScreenObservation(
                GameScreen.FRIEND_DETAIL, .98f, RecognitionSource.OCR, canSendGift = false,
            )),
            settings,
        )
        assertEquals(1, decision.snapshot.giftsSent)
        assertTrue("session-hash" in decision.snapshot.processedFriendFingerprints)
        assertEquals(GiftAutomationState.RETURNING_TO_LIST, decision.snapshot.state)
    }

    @Test fun limitDialogsStopCleanly() {
        val running = GiftWorkflowSnapshot(state = GiftAutomationState.OPENING_GIFT)
        val openLimit = observe(running, GameScreen.OPEN_LIMIT_DIALOG)
        assertEquals(GiftAutomationState.COMPLETED, openLimit.snapshot.state)
        assertEquals(GiftStopReason.OPEN_LIMIT_REACHED, openLimit.snapshot.stopReason)

        val sendLimit = observe(running, GameScreen.SEND_LIMIT_DIALOG)
        assertEquals(GiftStopReason.SEND_LIMIT_REACHED, sendLimit.snapshot.stopReason)
    }

    @Test fun bagFullCleanupRequiresVerifiedFullQuantityAndResumesSorting() {
        var decision = observe(
            GiftWorkflowSnapshot(
                state = GiftAutomationState.OPENING_GIFT,
                currentFriendFingerprint = "friend",
            ),
            GameScreen.BAG_FULL_DIALOG,
            targets = mapOf(AutomationAction.OPEN_ITEM_BAG to genericTarget),
        )
        assertEquals(GiftAutomationState.OPENING_ITEM_BAG, decision.snapshot.state)

        decision = GiftWorkflowReducer.reduce(
            decision.snapshot,
            GiftWorkflowEvent.ScreenRecognized(ScreenObservation(
                GameScreen.ITEM_BAG, .98f, RecognitionSource.OCR,
                cleanupCandidates = listOf(CleanupCandidate(CleanupItem.POTION, genericTarget, 19)),
            )), settings,
        )
        assertEquals(AutomationAction.OPEN_DISCARD, decision.instruction?.action)

        decision = GiftWorkflowReducer.reduce(
            decision.snapshot,
            GiftWorkflowEvent.ScreenRecognized(ScreenObservation(
                GameScreen.DISCARD_CONFIRMATION, .99f, RecognitionSource.OCR,
                confirmedItem = CleanupItem.POTION,
                selectedQuantity = 19,
                availableQuantity = 19,
                targets = mapOf(AutomationAction.CONFIRM_DISCARD to genericTarget),
            )), settings,
        )
        assertEquals(AutomationAction.CONFIRM_DISCARD, decision.instruction?.action)

        decision = observe(decision.snapshot, GameScreen.ITEM_BAG)
        assertEquals(19, decision.snapshot.itemsDiscarded[CleanupItem.POTION])
        assertEquals(GiftAutomationState.RETURNING_TO_FRIENDS, decision.snapshot.state)

        decision = observe(decision.snapshot, GameScreen.HOME_MAP, targets = mapOf(AutomationAction.OPEN_PROFILE to genericTarget))
        decision = observe(decision.snapshot, GameScreen.TRAINER_PROFILE, targets = mapOf(AutomationAction.OPEN_FRIENDS to genericTarget))
        decision = observe(decision.snapshot, GameScreen.FRIENDS_LIST, targets = mapOf(AutomationAction.OPEN_SORT to genericTarget))
        assertEquals(GiftAutomationState.APPLYING_SORT, decision.snapshot.state)
        assertEquals(AutomationAction.OPEN_SORT, decision.instruction?.action)
    }

    @Test fun lowConfidenceRetriesThenErrorsAndUnexpectedDialogPauses() {
        var state = GiftWorkflowSnapshot(state = GiftAutomationState.SCANNING_FRIENDS)
        repeat(settings.retryLimit + 1) {
            state = GiftWorkflowReducer.reduce(
                state,
                GiftWorkflowEvent.ScreenRecognized(ScreenObservation(GameScreen.UNKNOWN, .2f, RecognitionSource.OCR)),
                settings,
            ).snapshot
        }
        assertEquals(GiftAutomationState.ERROR, state.state)
        assertNotNull(state.errorMessage)

        val dialog = observe(
            GiftWorkflowSnapshot(state = GiftAutomationState.OPENING_FRIEND),
            GameScreen.SYSTEM_DIALOG,
        )
        assertEquals(GiftAutomationState.PAUSED, dialog.snapshot.state)
    }

    @Test fun recoveryResumesTheInterruptedStateWhenAnimationSettles() {
        val opening = GiftWorkflowSnapshot(state = GiftAutomationState.OPENING_GIFT)
        val low = GiftWorkflowReducer.reduce(
            opening,
            GiftWorkflowEvent.ScreenRecognized(ScreenObservation(GameScreen.UNKNOWN, .2f, RecognitionSource.OCR)),
            settings,
        )
        assertEquals(GiftAutomationState.RECOVERING, low.snapshot.state)
        val settled = observe(
            low.snapshot,
            GameScreen.GIFT_RESULTS,
            targets = mapOf(AutomationAction.DISMISS_GIFT_RESULTS to genericTarget),
        )
        assertEquals(GiftAutomationState.HANDLING_GIFT_RESULTS, settled.snapshot.state)
        assertEquals(1, settled.snapshot.giftsOpened)
    }

    @Test fun itemCleanupScansAVisiblePageBeforeDeclaringItComplete() {
        val cleaning = GiftWorkflowSnapshot(state = GiftAutomationState.CLEANING_ITEMS)
        val first = GiftWorkflowReducer.reduce(
            cleaning,
            GiftWorkflowEvent.ScreenRecognized(ScreenObservation(
                GameScreen.ITEM_BAG,
                .98f,
                RecognitionSource.OCR,
                pageFingerprint = "item-page",
            )),
            settings,
        )
        assertEquals(AutomationAction.SCROLL_ITEMS, first.instruction?.action)
        val repeated = GiftWorkflowReducer.reduce(
            first.snapshot,
            GiftWorkflowEvent.ScreenRecognized(ScreenObservation(
                GameScreen.ITEM_BAG,
                .98f,
                RecognitionSource.OCR,
                pageFingerprint = "item-page",
            )),
            settings,
        )
        assertEquals(GiftAutomationState.RETURNING_TO_FRIENDS, repeated.snapshot.state)
        assertEquals(AutomationAction.BACK, repeated.instruction?.action)
    }

    @Test fun fakeRecognitionAndGesturePortsSupportOfflineWorkflowTests() = runBlocking {
        val fakeRecognizer = ScreenRecognitionPort<String> {
            ScreenObservation(GameScreen.HOME_MAP, .99f, RecognitionSource.TEMPLATE)
        }
        val executed = mutableListOf<AutomationAction>()
        val fakeGestures = GestureExecutionPort { instruction, _ ->
            executed += instruction.action
            true
        }
        val observation = fakeRecognizer.recognize("fixture/home.png")
        val started = GiftWorkflowReducer.reduce(GiftWorkflowSnapshot(), GiftWorkflowEvent.Start, settings)
        val decision = GiftWorkflowReducer.reduce(started.snapshot, GiftWorkflowEvent.ScreenRecognized(observation), settings)
        assertTrue(fakeGestures.execute(requireNotNull(decision.instruction), settings))
        assertEquals(listOf(AutomationAction.OPEN_PROFILE), executed)
    }

    private fun observe(
        state: GiftWorkflowSnapshot,
        screen: GameScreen,
        targets: Map<AutomationAction, RecognitionTarget> = emptyMap(),
    ): WorkflowDecision = GiftWorkflowReducer.reduce(
        state,
        GiftWorkflowEvent.ScreenRecognized(ScreenObservation(screen, .98f, RecognitionSource.OCR, targets = targets)),
        settings,
    )
}

package com.jacob.pokemonscanner.domain

import com.jacob.pokemonscanner.model.*
import java.time.Instant

object GiftWorkflowReducer {
    fun reduce(
        state: GiftWorkflowSnapshot,
        event: GiftWorkflowEvent,
        settings: GiftAssistantSettings,
    ): WorkflowDecision = when (event) {
        GiftWorkflowEvent.Start -> WorkflowDecision(
            state.copy(
                state = GiftAutomationState.VERIFYING_GAME,
                resumeState = null,
                currentAction = "Confirming Pokémon GO is on the map",
                giftsOpened = 0,
                giftsSent = 0,
                friendsSkipped = 0,
                itemsDiscarded = emptyMap(),
                processedFriendFingerprints = emptySet(),
                currentFriendFingerprint = null,
                scannedEmptyPages = emptySet(),
                retryCount = 0,
                sortVerified = false,
                giftCountedForCurrentFriend = false,
                pendingDiscardItem = null,
                pendingDiscardQuantity = null,
                stopReason = null,
                errorMessage = null,
                recoveryAction = null,
                startedAt = Instant.now(),
            ),
        )

        is GiftWorkflowEvent.ScreenRecognized -> onObservation(state, event.observation, settings)
        is GiftWorkflowEvent.ActionFailed -> retryOrError(
            state,
            settings,
            "${event.action.name.lowercase().replace('_', ' ')} failed: ${event.reason}",
        )

        GiftWorkflowEvent.Pause -> pause(state, "Paused by user", state.state)
        GiftWorkflowEvent.Resume -> WorkflowDecision(
            state.copy(
                state = state.resumeState?.takeUnless { it == GiftAutomationState.PAUSED } ?: GiftAutomationState.RECOVERING,
                resumeState = null,
                currentAction = "Rechecking the current screen",
                errorMessage = null,
                recoveryAction = null,
                retryCount = 0,
            ),
        )

        GiftWorkflowEvent.UserStop -> completed(state, GiftStopReason.USER_STOPPED)
        GiftWorkflowEvent.ManualControlDetected -> pause(
            state,
            "Manual control detected. Automation stopped immediately.",
            state.state,
            GiftStopReason.MANUAL_CONTROL,
        )

        GiftWorkflowEvent.CaptureLost -> pause(
            state,
            "Screen capture ended.",
            state.state,
            GiftStopReason.CAPTURE_LOST,
        )

        is GiftWorkflowEvent.GuidedTargetShown -> WorkflowDecision(
            state.copy(
                state = GiftAutomationState.PAUSED,
                resumeState = state.state,
                currentAction = "Guided target highlighted: ${event.target.safeLabel}",
                lastAction = event.action,
                lastTarget = event.target,
                recoveryAction = "Make the highlighted selection manually, then press Resume to recheck the screen.",
            ),
        )
    }

    private fun onObservation(
        state: GiftWorkflowSnapshot,
        observation: ScreenObservation,
        settings: GiftAssistantSettings,
    ): WorkflowDecision {
        val observed = state.copy(
            currentScreen = observation.screen,
            recognitionConfidence = observation.confidence,
            recognitionSource = observation.source,
        )

        when (observation.screen) {
            GameScreen.OPEN_LIMIT_DIALOG -> return completed(observed, GiftStopReason.OPEN_LIMIT_REACHED)
            GameScreen.SEND_LIMIT_DIALOG -> return completed(observed, GiftStopReason.SEND_LIMIT_REACHED)
            GameScreen.NO_GIFTS_DIALOG -> return completed(observed, GiftStopReason.NO_GIFTS_TO_SEND)
            GameScreen.BAG_FULL_DIALOG -> return instruct(
                observed.copy(state = GiftAutomationState.BAG_FULL_DETECTED),
                AutomationAction.OPEN_ITEM_BAG,
                observation,
                "Opening the Item Bag from the verified bag-full dialog",
            ).copy(snapshot = observed.copy(state = GiftAutomationState.OPENING_ITEM_BAG, currentAction = "Opening Item Bag", lastAction = AutomationAction.OPEN_ITEM_BAG, lastTarget = observation.targets[AutomationAction.OPEN_ITEM_BAG]))

            GameScreen.NETWORK_ERROR -> return pause(
                observed,
                "Pokémon GO reported a network error.",
                state.state,
                recovery = "Resolve the connection in Pokémon GO, return to a stable screen, then press Resume.",
            )

            GameScreen.SYSTEM_DIALOG -> return pause(
                observed,
                "An unexpected system or permission dialog is visible.",
                state.state,
                recovery = "Review the dialog yourself. Resume only after Pokémon GO is visible again.",
            )

            GameScreen.GAME_NOT_FOREGROUND -> return pause(
                observed,
                "Pokémon GO is not in the foreground.",
                state.state,
                recovery = "Return to Pokémon GO and press Resume.",
            )

            else -> Unit
        }

        if (observation.confidence < settings.minimumRecognitionConfidence || observation.screen == GameScreen.UNKNOWN) {
            return retryOrError(observed, settings, "Screen recognition confidence was too low")
        }

        return when (state.state) {
            GiftAutomationState.VERIFYING_GAME -> when (observation.screen) {
                GameScreen.HOME_MAP -> instruct(
                    observed.copy(state = GiftAutomationState.OPENING_PROFILE, retryCount = 0),
                    AutomationAction.OPEN_PROFILE,
                    observation,
                    "Opening Trainer profile",
                )
                else -> unsafeScreen(observed, "Start from the Pokémon GO map/home screen")
            }

            GiftAutomationState.HOME_SCREEN,
            GiftAutomationState.RECOVERING,
            -> recoverFromKnownScreen(observed, observation)

            GiftAutomationState.OPENING_PROFILE -> when (observation.screen) {
                GameScreen.TRAINER_PROFILE -> instruct(
                    observed.copy(state = GiftAutomationState.OPENING_FRIENDS, retryCount = 0),
                    AutomationAction.OPEN_FRIENDS,
                    observation,
                    "Opening Friends",
                )
                GameScreen.HOME_MAP -> repeatLast(observed, observation, "Trainer profile has not opened yet")
                else -> retryOrError(observed, settings, "Expected the Trainer profile")
            }

            GiftAutomationState.OPENING_FRIENDS -> when (observation.screen) {
                GameScreen.FRIENDS_LIST -> instruct(
                    observed.copy(state = GiftAutomationState.APPLYING_SORT, retryCount = 0, sortVerified = false),
                    AutomationAction.OPEN_SORT,
                    observation,
                    "Opening friend sort options",
                )
                GameScreen.TRAINER_PROFILE -> repeatLast(observed, observation, "Friends list has not opened yet")
                else -> retryOrError(observed, settings, "Expected the Friends list")
            }

            GiftAutomationState.APPLYING_SORT -> applySort(observed, observation, settings)
            GiftAutomationState.SCANNING_FRIENDS -> scanFriends(observed, observation)
            GiftAutomationState.OPENING_FRIEND -> openFriend(observed, observation)
            GiftAutomationState.OPENING_GIFT -> openGift(observed, observation)
            GiftAutomationState.HANDLING_GIFT_RESULTS -> handleGiftResults(observed, observation)
            GiftAutomationState.CHECKING_SEND_ELIGIBILITY -> checkSendEligibility(observed, observation)
            GiftAutomationState.SENDING_GIFT -> sendGift(observed, observation)
            GiftAutomationState.RETURNING_TO_LIST -> returnToList(observed, observation)
            GiftAutomationState.BAG_FULL_DETECTED,
            GiftAutomationState.OPENING_ITEM_BAG,
            -> openItemBag(observed, observation)
            GiftAutomationState.CLEANING_ITEMS -> cleanItems(observed, observation, settings)
            GiftAutomationState.RETURNING_TO_FRIENDS -> returnToFriends(observed, observation)
            GiftAutomationState.IDLE,
            GiftAutomationState.PAUSED,
            GiftAutomationState.COMPLETED,
            GiftAutomationState.ERROR,
            -> WorkflowDecision(observed)
        }
    }

    private fun applySort(
        state: GiftWorkflowSnapshot,
        observation: ScreenObservation,
        settings: GiftAssistantSettings,
    ): WorkflowDecision = when (observation.screen) {
        GameScreen.FRIENDS_LIST -> {
            if (state.sortVerified && state.lastAction == AutomationAction.CLOSE_SORT) {
                scanFriends(state.copy(state = GiftAutomationState.SCANNING_FRIENDS), observation)
            } else {
                instruct(state, AutomationAction.OPEN_SORT, observation, "Verifying friend sort")
            }
        }

        GameScreen.SORT_MENU -> {
            val desiredAction = when (settings.sortMode) {
                GiftSortMode.GIFT_STATUS -> AutomationAction.SELECT_GIFT_SORT
                GiftSortMode.FRIENDSHIP_LEVEL -> AutomationAction.SELECT_FRIENDSHIP_SORT
            }
            when {
                observation.detectedSortMode != settings.sortMode -> instruct(
                    state.copy(sortVerified = false), desiredAction, observation, "Selecting ${settings.sortMode.label}",
                )
                observation.detectedSortDirection == null -> unsafeScreen(
                    state,
                    "Sort direction could not be verified; calibration is required before tapping",
                )
                observation.detectedSortDirection != settings.sortDirection -> instruct(
                    state.copy(sortVerified = false),
                    AutomationAction.TOGGLE_SORT_DIRECTION,
                    observation,
                    "Changing sort direction to ${settings.sortDirection.label}",
                )
                else -> instruct(
                    state.copy(sortVerified = true),
                    AutomationAction.CLOSE_SORT,
                    observation,
                    "Sort verified; closing sort options",
                )
            }
        }

        else -> retryOrError(state, settings, "Expected Friends or the sort menu")
    }

    private fun scanFriends(
        state: GiftWorkflowSnapshot,
        observation: ScreenObservation,
    ): WorkflowDecision {
        if (observation.screen != GameScreen.FRIENDS_LIST) return unsafeScreen(state, "Expected the Friends list")
        val candidate = observation.eligibleFriends.firstOrNull {
            it.sessionFingerprint !in state.processedFriendFingerprints
        }
        if (candidate != null) {
            return instruct(
                state.copy(
                    state = GiftAutomationState.OPENING_FRIEND,
                    currentFriendFingerprint = candidate.sessionFingerprint,
                    giftCountedForCurrentFriend = false,
                    retryCount = 0,
                ),
                AutomationAction.SELECT_FRIEND,
                observation,
                "Opening the first eligible friend",
                candidate.target,
            )
        }
        val page = observation.pageFingerprint
        if (page != null && page !in state.scannedEmptyPages) {
            return instruct(
                state.copy(scannedEmptyPages = state.scannedEmptyPages + page),
                AutomationAction.SCROLL_FRIENDS,
                observation,
                "Checking the next page of friends",
            )
        }
        return completed(state, GiftStopReason.NO_ELIGIBLE_FRIENDS)
    }

    private fun openFriend(
        state: GiftWorkflowSnapshot,
        observation: ScreenObservation,
    ): WorkflowDecision = when (observation.screen) {
        GameScreen.GIFT_POSTCARD -> instruct(
            state.copy(state = GiftAutomationState.OPENING_GIFT),
            AutomationAction.OPEN_GIFT,
            observation,
            "Opening received gift",
        )
        GameScreen.FRIEND_DETAIL -> when (observation.hasReceivedGift) {
            true -> instruct(
                state.copy(state = GiftAutomationState.OPENING_GIFT),
                AutomationAction.OPEN_RECEIVED_GIFT,
                observation,
                "Opening received gift",
            )
            false -> returnToListDecision(state.copy(friendsSkipped = state.friendsSkipped + 1), observation, "Friend has no received gift")
            null -> unsafeScreen(state, "Received-gift status could not be verified")
        }
        GameScreen.FRIENDS_LIST -> repeatLast(state, observation, "Friend has not opened yet")
        else -> unsafeScreen(state, "Expected a friend or gift screen")
    }

    private fun openGift(
        state: GiftWorkflowSnapshot,
        observation: ScreenObservation,
    ): WorkflowDecision = when (observation.screen) {
        GameScreen.GIFT_POSTCARD -> instruct(state, AutomationAction.OPEN_GIFT, observation, "Opening received gift")
        GameScreen.GIFT_RESULTS -> {
            val counted = if (state.giftCountedForCurrentFriend) state else state.copy(
                giftsOpened = state.giftsOpened + 1,
                giftCountedForCurrentFriend = true,
            )
            instruct(
                counted.copy(state = GiftAutomationState.HANDLING_GIFT_RESULTS),
                AutomationAction.DISMISS_GIFT_RESULTS,
                observation,
                "Dismissing gift rewards",
            )
        }
        GameScreen.FRIENDSHIP_MESSAGE,
        GameScreen.POSTCARD_PROMPT,
        GameScreen.STICKER_PICKER,
        -> instruct(state, AutomationAction.DISMISS_KNOWN_DIALOG, observation, "Dismissing a recognized gift dialog")
        else -> unsafeScreen(state, "Expected the received gift or its results")
    }

    private fun handleGiftResults(
        state: GiftWorkflowSnapshot,
        observation: ScreenObservation,
    ): WorkflowDecision = when (observation.screen) {
        GameScreen.GIFT_RESULTS -> instruct(state, AutomationAction.DISMISS_GIFT_RESULTS, observation, "Finishing gift rewards")
        GameScreen.FRIENDSHIP_MESSAGE,
        GameScreen.POSTCARD_PROMPT,
        GameScreen.STICKER_PICKER,
        -> instruct(state, AutomationAction.DISMISS_KNOWN_DIALOG, observation, "Dismissing a recognized follow-up")
        GameScreen.FRIEND_DETAIL -> checkSendEligibility(
            state.copy(state = GiftAutomationState.CHECKING_SEND_ELIGIBILITY), observation,
        )
        else -> unsafeScreen(state, "Gift result flow changed unexpectedly")
    }

    private fun checkSendEligibility(
        state: GiftWorkflowSnapshot,
        observation: ScreenObservation,
    ): WorkflowDecision {
        if (observation.screen != GameScreen.FRIEND_DETAIL) return unsafeScreen(state, "Expected friend details")
        return when (observation.canSendGift) {
            true -> instruct(
                state.copy(state = GiftAutomationState.SENDING_GIFT),
                AutomationAction.SEND_GIFT,
                observation,
                "Opening gift selection",
            )
            false -> returnToListDecision(state, observation, "Friend cannot receive a gift")
            null -> unsafeScreen(state, "Send eligibility could not be verified")
        }
    }

    private fun sendGift(
        state: GiftWorkflowSnapshot,
        observation: ScreenObservation,
    ): WorkflowDecision = when (observation.screen) {
        GameScreen.GIFT_PICKER -> when (observation.hasGiftToSend) {
            true -> instruct(state, AutomationAction.SELECT_GIFT, observation, "Selecting the first available gift")
            false -> completed(state, GiftStopReason.NO_GIFTS_TO_SEND)
            null -> unsafeScreen(state, "Gift availability could not be verified")
        }
        GameScreen.GIFT_COMPOSE -> instruct(state, AutomationAction.CONFIRM_SEND, observation, "Sending the selected gift")
        GameScreen.FRIEND_DETAIL -> {
            val sent = if (state.lastAction == AutomationAction.CONFIRM_SEND) state.copy(giftsSent = state.giftsSent + 1) else state
            returnToListDecision(sent, observation, "Gift flow complete")
        }
        else -> unsafeScreen(state, "Gift sending flow changed unexpectedly")
    }

    private fun returnToList(
        state: GiftWorkflowSnapshot,
        observation: ScreenObservation,
    ): WorkflowDecision = when (observation.screen) {
        GameScreen.FRIENDS_LIST -> scanFriends(markCurrentProcessed(state).copy(state = GiftAutomationState.SCANNING_FRIENDS), observation)
        GameScreen.FRIEND_DETAIL,
        GameScreen.GIFT_POSTCARD,
        -> instruct(state, AutomationAction.BACK, observation, "Returning to the Friends list")
        else -> unsafeScreen(state, "Could not safely return to Friends")
    }

    private fun openItemBag(
        state: GiftWorkflowSnapshot,
        observation: ScreenObservation,
    ): WorkflowDecision = when (observation.screen) {
        GameScreen.BAG_FULL_DIALOG -> instruct(
            state.copy(state = GiftAutomationState.OPENING_ITEM_BAG),
            AutomationAction.OPEN_ITEM_BAG,
            observation,
            "Opening Item Bag",
        )
        GameScreen.ITEM_BAG -> cleanItems(state.copy(state = GiftAutomationState.CLEANING_ITEMS), observation, GiftAssistantSettings())
        else -> unsafeScreen(state, "Expected the bag-full dialog or Item Bag")
    }

    private fun cleanItems(
        state: GiftWorkflowSnapshot,
        observation: ScreenObservation,
        settings: GiftAssistantSettings,
    ): WorkflowDecision = when (observation.screen) {
        GameScreen.ITEM_BAG -> {
            var updated = state
            if (state.lastAction == AutomationAction.CONFIRM_DISCARD && state.pendingDiscardItem != null && state.pendingDiscardQuantity != null) {
                val current = state.itemsDiscarded[state.pendingDiscardItem] ?: 0
                updated = state.copy(
                    itemsDiscarded = state.itemsDiscarded + (state.pendingDiscardItem to current + state.pendingDiscardQuantity),
                    pendingDiscardItem = null,
                    pendingDiscardQuantity = null,
                )
            }
            val candidate = observation.cleanupCandidates.firstOrNull {
                it.item.mayEverBeDiscarded && it.item in settings.cleanupItems
            }
            if (candidate == null) {
                instruct(
                    updated.copy(state = GiftAutomationState.RETURNING_TO_FRIENDS),
                    AutomationAction.BACK,
                    observation,
                    "Cleanup complete; returning to Friends",
                )
            } else {
                instruct(
                    updated.copy(pendingDiscardItem = candidate.item),
                    AutomationAction.OPEN_DISCARD,
                    observation,
                    "Opening discard controls for verified ${candidate.item.displayName}",
                    candidate.target,
                )
            }
        }
        GameScreen.DISCARD_CONFIRMATION -> {
            if (observation.confirmedItem != state.pendingDiscardItem) {
                unsafeScreen(state, "Discard dialog item does not match the verified selected item")
            } else when (val validation = CleanupPolicy.validate(
                observation.confirmedItem,
                settings.cleanupItems,
                observation.selectedQuantity,
                observation.availableQuantity,
            )) {
                is CleanupValidation.Allowed -> instruct(
                    state.copy(pendingDiscardQuantity = validation.quantity),
                    AutomationAction.CONFIRM_DISCARD,
                    observation,
                    "Discarding all ${validation.quantity} verified ${observation.confirmedItem.displayName}",
                )
                is CleanupValidation.Rejected -> {
                    if (observation.confirmedItem?.mayEverBeDiscarded == true &&
                        observation.confirmedItem in settings.cleanupItems &&
                        observation.availableQuantity != null
                    ) {
                        instruct(state, AutomationAction.SELECT_ALL_QUANTITY, observation, "Selecting the full verified item quantity")
                    } else unsafeScreen(state, validation.reason)
                }
            }
        }
        else -> unsafeScreen(state, "Unexpected screen during item cleanup")
    }

    private fun returnToFriends(
        state: GiftWorkflowSnapshot,
        observation: ScreenObservation,
    ): WorkflowDecision = when (observation.screen) {
        GameScreen.ITEM_BAG,
        GameScreen.FRIEND_DETAIL,
        -> instruct(state, AutomationAction.BACK, observation, "Returning toward Friends")
        GameScreen.HOME_MAP -> instruct(
            state.copy(state = GiftAutomationState.RETURNING_TO_FRIENDS), AutomationAction.OPEN_PROFILE, observation, "Reopening Trainer profile",
        )
        GameScreen.TRAINER_PROFILE -> instruct(
            state.copy(state = GiftAutomationState.RETURNING_TO_FRIENDS), AutomationAction.OPEN_FRIENDS, observation, "Reopening Friends",
        )
        GameScreen.FRIENDS_LIST -> instruct(
            state.copy(state = GiftAutomationState.APPLYING_SORT, sortVerified = false), AutomationAction.OPEN_SORT, observation, "Restoring configured sort",
        )
        else -> unsafeScreen(state, "Could not verify the return path to Friends")
    }

    private fun recoverFromKnownScreen(
        state: GiftWorkflowSnapshot,
        observation: ScreenObservation,
    ): WorkflowDecision = when (observation.screen) {
        GameScreen.HOME_MAP -> instruct(state.copy(state = GiftAutomationState.OPENING_PROFILE), AutomationAction.OPEN_PROFILE, observation, "Recovering from the map")
        GameScreen.TRAINER_PROFILE -> instruct(state.copy(state = GiftAutomationState.OPENING_FRIENDS), AutomationAction.OPEN_FRIENDS, observation, "Recovering from Trainer profile")
        GameScreen.FRIENDS_LIST -> instruct(state.copy(state = GiftAutomationState.APPLYING_SORT, sortVerified = false), AutomationAction.OPEN_SORT, observation, "Restoring friend sort")
        GameScreen.FRIEND_DETAIL -> returnToListDecision(state, observation, "Recovering to Friends")
        else -> unsafeScreen(state, "No safe recovery route is known for this screen")
    }

    private fun returnToListDecision(
        state: GiftWorkflowSnapshot,
        observation: ScreenObservation,
        reason: String,
    ): WorkflowDecision = instruct(
        markCurrentProcessed(state).copy(state = GiftAutomationState.RETURNING_TO_LIST),
        AutomationAction.BACK,
        observation,
        "$reason; returning to Friends",
    )

    private fun markCurrentProcessed(state: GiftWorkflowSnapshot): GiftWorkflowSnapshot {
        val fingerprint = state.currentFriendFingerprint ?: return state
        return state.copy(processedFriendFingerprints = state.processedFriendFingerprints + fingerprint)
    }

    private fun repeatLast(
        state: GiftWorkflowSnapshot,
        observation: ScreenObservation,
        message: String,
    ): WorkflowDecision {
        if (state.lastAction == AutomationAction.NONE) return unsafeScreen(state, message)
        return instruct(state, state.lastAction, observation, message, state.lastTarget)
    }

    private fun instruct(
        state: GiftWorkflowSnapshot,
        action: AutomationAction,
        observation: ScreenObservation,
        description: String,
        explicitTarget: RecognitionTarget? = null,
    ): WorkflowDecision {
        val target = explicitTarget ?: observation.targets[action]
        return WorkflowDecision(
            snapshot = state.copy(
                currentAction = description,
                lastAction = action,
                lastTarget = target,
                errorMessage = null,
                recoveryAction = null,
            ),
            instruction = WorkflowInstruction(action, target, description),
        )
    }

    private fun retryOrError(
        state: GiftWorkflowSnapshot,
        settings: GiftAssistantSettings,
        message: String,
    ): WorkflowDecision {
        val attempts = state.retryCount + 1
        return if (attempts <= settings.retryLimit) {
            WorkflowDecision(state.copy(
                state = GiftAutomationState.RECOVERING,
                resumeState = state.state,
                retryCount = attempts,
                currentAction = "$message. Rechecking ($attempts/${settings.retryLimit})",
                errorMessage = message,
            ))
        } else unsafeScreen(state, "$message after ${settings.retryLimit} retries")
    }

    private fun unsafeScreen(state: GiftWorkflowSnapshot, message: String): WorkflowDecision = WorkflowDecision(
        state.copy(
            state = GiftAutomationState.ERROR,
            resumeState = state.state.takeUnless { it == GiftAutomationState.ERROR },
            currentAction = "Paused for safety",
            stopReason = GiftStopReason.UNSAFE_TO_CONTINUE,
            errorMessage = message,
            recoveryAction = "Put Pokémon GO on a supported stable screen, verify Diagnostics, then press Resume.",
        ),
    )

    private fun pause(
        state: GiftWorkflowSnapshot,
        message: String,
        resumeState: GiftAutomationState,
        reason: GiftStopReason? = null,
        recovery: String = "Press Resume when ready.",
    ): WorkflowDecision = WorkflowDecision(
        state.copy(
            state = GiftAutomationState.PAUSED,
            resumeState = resumeState.takeUnless { it == GiftAutomationState.PAUSED },
            currentAction = message,
            stopReason = reason,
            recoveryAction = recovery,
        ),
    )

    private fun completed(state: GiftWorkflowSnapshot, reason: GiftStopReason): WorkflowDecision = WorkflowDecision(
        state.copy(
            state = GiftAutomationState.COMPLETED,
            currentAction = reason.label,
            stopReason = reason,
            currentFriendFingerprint = null,
            pendingDiscardItem = null,
            pendingDiscardQuantity = null,
        ),
    )
}

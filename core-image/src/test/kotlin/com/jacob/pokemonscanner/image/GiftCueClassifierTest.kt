package com.jacob.pokemonscanner.image

import com.jacob.pokemonscanner.model.GameScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GiftCueClassifierTest {
    @Test fun classifiesSuppliedFlowCues() {
        assertScreen(GameScreen.FRIENDS_LIST, "FRIENDS 237 ADD FRIEND SEARCH INVITE sent you a Gift!")
        assertScreen(GameScreen.SORT_MENU, "RECENT ONLINE NAME FRIENDSHIP LEVEL GIFT CAN RECEIVE A GIFT")
        assertScreen(GameScreen.GIFT_POSTCARD, "Greetings from The Retreat Fountain OPEN")
        assertScreen(GameScreen.FRIEND_DETAIL, "SEND GIFT TRADE BATTLE TOTAL ACTIVITY")
        assertScreen(GameScreen.GIFT_PICKER, "Which Gift do you want to send?")
        assertScreen(GameScreen.GIFT_COMPOSE, "ADD STICKER SEND")
        assertScreen(GameScreen.GIFT_RESULTS, "Ultra Ball +1 Stardust +300")
    }

    @Test fun limitAndErrorCuesTakePriority() {
        assertScreen(GameScreen.BAG_FULL_DIALOG, "Your Item Bag is full. Manage Items")
        assertScreen(GameScreen.OPEN_LIMIT_DIALOG, "You have reached your daily Gift open limit")
        assertScreen(GameScreen.SEND_LIMIT_DIALOG, "You have reached your daily Gift send limit")
        assertScreen(GameScreen.NETWORK_ERROR, "Network Error. Please retry.")
    }

    private fun assertScreen(expected: GameScreen, text: String) {
        val result = GiftCueClassifier.classify(text)
        assertEquals(expected, result.screen)
        assertTrue(result.confidence >= .82f)
    }
}

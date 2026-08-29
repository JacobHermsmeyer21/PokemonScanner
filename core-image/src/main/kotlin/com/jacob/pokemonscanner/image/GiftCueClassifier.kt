package com.jacob.pokemonscanner.image

import com.jacob.pokemonscanner.model.GameScreen

data class CueClassification(
    val screen: GameScreen,
    val confidence: Float,
    val safeCues: Set<String>,
)

object GiftCueClassifier {
    fun classify(rawText: String): CueClassification {
        val text = rawText.uppercase().replace(Regex("\\s+"), " ").trim()
        fun has(vararg values: String) = values.all(text::contains)
        fun result(screen: GameScreen, confidence: Float, vararg cues: String) =
            CueClassification(screen, confidence, cues.toSet())

        return when {
            has("GIFT", "OPEN", "DAILY", "LIMIT") || has("OPENED", "MAXIMUM", "GIFTS") ->
                result(GameScreen.OPEN_LIMIT_DIALOG, .98f, "gift open limit")
            has("GIFT", "SEND", "DAILY", "LIMIT") || has("SENT", "MAXIMUM", "GIFTS") ->
                result(GameScreen.SEND_LIMIT_DIALOG, .98f, "gift send limit")
            has("NO GIFTS") || has("DON'T HAVE ANY GIFTS") ->
                result(GameScreen.NO_GIFTS_DIALOG, .98f, "no gifts")
            has("ITEM BAG", "FULL") || has("BAG IS FULL") ->
                result(GameScreen.BAG_FULL_DIALOG, .99f, "bag full")
            has("NETWORK", "ERROR") || has("UNABLE", "CONNECT") || has("RETRY") ->
                result(GameScreen.NETWORK_ERROR, .96f, "network error")
            has("WHICH GIFT DO YOU WANT TO SEND") ->
                result(GameScreen.GIFT_PICKER, .99f, "gift picker")
            has("ADD STICKER", "SEND") ->
                result(GameScreen.GIFT_COMPOSE, .99f, "gift compose")
            has("GREETINGS FROM", "OPEN") ->
                result(GameScreen.GIFT_POSTCARD, .99f, "received postcard", "open")
            has("FRIENDSHIP LEVEL", "GIFT", "CAN RECEIVE A GIFT", "RECENT") ->
                result(GameScreen.SORT_MENU, .99f, "sort menu")
            has("ADD FRIEND", "SEARCH", "FRIENDS") ->
                result(GameScreen.FRIENDS_LIST, .98f, "friends list")
            has("SEND GIFT", "TRADE", "BATTLE") ->
                result(GameScreen.FRIEND_DETAIL, .98f, "friend details")
            has("FRIENDSHIP LEVEL INCREASED") || has("BECAME", "FRIENDS") ->
                result(GameScreen.FRIENDSHIP_MESSAGE, .96f, "friendship message")
            has("PIN", "POSTCARD") || has("POSTCARD BOOK") ->
                result(GameScreen.POSTCARD_PROMPT, .95f, "postcard prompt")
            has("CHOOSE A STICKER") || has("STICKERS") ->
                result(GameScreen.STICKER_PICKER, .93f, "sticker picker")
            has("DISCARD", "POTION") || has("DISCARD", "BERRY") || has("DISCARD", "REVIVE") ->
                result(GameScreen.DISCARD_CONFIRMATION, .96f, "discard confirmation")
            has("ITEM BAG") || has("ITEMS", "POTION") || has("ITEMS", "REVIVE") ->
                result(GameScreen.ITEM_BAG, .94f, "item bag")
            has("STARDUST") || has("ULTRA BALL") || has("GREAT BALL") || has("PINAP BERRY") ->
                result(GameScreen.GIFT_RESULTS, .90f, "gift rewards")
            has("ME", "FRIENDS", "SOCIAL", "LEVEL") ->
                result(GameScreen.TRAINER_PROFILE, .91f, "trainer profile")
            else -> result(GameScreen.UNKNOWN, 0f)
        }
    }
}

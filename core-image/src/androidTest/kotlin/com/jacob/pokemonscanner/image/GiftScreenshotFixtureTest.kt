package com.jacob.pokemonscanner.image

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.jacob.pokemonscanner.model.GameScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Runs the real on-device ML Kit/OCR pipeline over the supplied screenshots.
 * Add new screenshots to /screenshots and extend this expected-state map.
 */
class GiftScreenshotFixtureTest {
    @Test fun suppliedGiftFlowScreensAreRecognized() = runBlocking {
        val expected = linkedMapOf(
            "Screenshot_20260829_133319_Pokmon_GO.jpg" to GameScreen.HOME_MAP,
            "Screenshot_20260829_133325_Pokmon_GO.jpg" to GameScreen.FRIENDS_LIST,
            "Screenshot_20260829_133333_Pokmon_GO.jpg" to GameScreen.SORT_MENU,
            "Screenshot_20260822_211436_Pokmon_GO.jpg" to GameScreen.FRIENDS_LIST,
            "Screenshot_20260822_211443_Pokmon_GO.jpg" to GameScreen.GIFT_POSTCARD,
            "Screenshot_20260822_211454_Pokmon_GO.jpg" to GameScreen.FRIEND_DETAIL,
            "Screenshot_20260822_211459_Pokmon_GO.jpg" to GameScreen.GIFT_PICKER,
            "Screenshot_20260822_211503_Pokmon_GO.jpg" to GameScreen.GIFT_COMPOSE,
            "Screenshot_20260822_211525_Pokmon_GO.jpg" to GameScreen.GIFT_RESULTS,
            "Screenshot_20260822_211528_Pokmon_GO.jpg" to GameScreen.FRIEND_DETAIL,
        )
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val analyzer = GiftScreenAnalyzer()
        expected.forEach { (fileName, screen) ->
            val bitmap = assets.open(fileName).use(BitmapFactory::decodeStream)
            try {
                assertEquals(fileName, screen, analyzer.recognize(bitmap).screen)
            } finally {
                bitmap.recycle()
            }
        }
    }
}

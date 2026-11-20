package com.miuing.reader

import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.uiautomator.UiDevice
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReadingSleepTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private fun main(action: () -> Unit) = instrument.runOnMainSync(action)
    private fun awake(view: View): Boolean { var value = false; main { value = view.keepScreenOn }; return value }
    private fun expires(view: View) {
        val deadline = System.currentTimeMillis() + 5000
        while (awake(view) && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertFalse("Screen-on request did not expire", awake(view))
    }
    private fun timer(original: Boolean = false, test: (View, ReadingSleepGuard) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var view: View
            lateinit var guard: ReadingSleepGuard
            scenario.onActivity { view = View(it).apply { keepScreenOn = original }; guard = ReadingSleepGuard(view, 100) }
            try { test(view, guard) } finally { main { guard.stop() } }
        }
    }
    @Test fun expiryRearmingAndCancellationRestoreTheOwnedFlag() = timer { view, guard ->
        main { guard.start(); assertTrue(view.keepScreenOn) }
        expires(view)
        main { guard.touch(); assertTrue(view.keepScreenOn); guard.stop(); assertFalse(view.keepScreenOn); view.keepScreenOn = true }
        Thread.sleep(180) // An uncancelled expiry would overwrite the subsequent owner's request.
        assertTrue(awake(view))
        main { guard.stop(); assertTrue("Repeated release overwrote another owner",view.keepScreenOn) }
        main { view.keepScreenOn = false; guard.touch(); assertFalse(view.keepScreenOn) }
    }
    @Test fun interactionExtendsThePendingDeadline() = timer { view, guard ->
        val observed = CountDownLatch(1)
        var value = false
        main {
            val handler = Handler(Looper.getMainLooper())
            guard.start()
            handler.postDelayed({ guard.touch() }, 50)
            handler.postDelayed({ value = view.keepScreenOn; observed.countDown() }, 120)
        }
        assertTrue(observed.await(5, TimeUnit.SECONDS))
        assertTrue("Original deadline was not extended", value)
        expires(view)
    }
    @Test fun existingScreenOnRequestIsPreserved() = timer(true) { view, guard ->
        main { guard.start() }
        Thread.sleep(180)
        assertTrue(awake(view))
        main { guard.stop(); assertTrue(view.keepScreenOn) }
    }

    @Test fun nativeWebViewPressRearmsWithoutRecompositionResettingIdle() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var host: View
            lateinit var web: WebView
            val revision = mutableIntStateOf(0)
            val ready = CountDownLatch(1)
            scenario.onActivity { activity -> activity.setContent {
                val current = LocalView.current
                SideEffect { host = current; ready.countDown() }
                // Read state to force recomposition without changing the interaction key.
                revision.intValue
                Box(Modifier.fillMaxSize().readingSleep(true, Unit, 2000)) {
                    AndroidView(modifier=Modifier.fillMaxSize(),factory = { WebView(it).also { reader -> web = reader; reader.loadData("<p>Reading touch test</p>","text/html","UTF-8") } })
                }
            } }
            assertTrue("Reader composition was not attached",ready.await(5,TimeUnit.SECONDS))
            expires(host)
            main { revision.intValue++ }
            instrument.waitForIdleSync()
            assertFalse("Ordinary recomposition restarted the timer", awake(host))
            val point = IntArray(2)
            main { web.getLocationOnScreen(point); point[0] += web.width / 2; point[1] += web.height / 2 }
            UiDevice.getInstance(instrument).click(point[0],point[1])
            val deadline = System.currentTimeMillis() + 1000
            while (!awake(host) && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertTrue("Native WebView touch did not re-arm screen-on", awake(host))
            expires(host)
        }
    }
}

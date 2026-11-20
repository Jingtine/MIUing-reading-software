package com.miuing.reader

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal class ReadingSleepGuard(private val view: View, private val durationMillis: Long = 600_000L) {
    private val handler = Handler(Looper.getMainLooper())
    private var original = false
    private var owned = false
    private var active = false
    private val expire = Runnable { release() }
    private fun release() { if (owned) { view.keepScreenOn = original; owned = false } }

    fun start() { active = true; touch() }
    fun touch() {
        if (!active) return
        if (!owned) { original = view.keepScreenOn; owned = true }
        handler.removeCallbacks(expire)
        view.keepScreenOn = true
        handler.postDelayed(expire, durationMillis)
    }
    fun stop() { active = false; handler.removeCallbacks(expire); release() }
}

@Composable internal fun Modifier.readingSleep(enabled: Boolean, interaction: Any?, durationMillis: Long = 600_000L): Modifier {
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val guard = remember(view, durationMillis) { ReadingSleepGuard(view, durationMillis) }
    DisposableEffect(owner, guard, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (enabled && event == Lifecycle.Event.ON_RESUME) guard.start()
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) guard.stop()
        }
        owner.lifecycle.addObserver(observer)
        if (enabled && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) guard.start()
        onDispose { owner.lifecycle.removeObserver(observer); guard.stop() }
    }
    LaunchedEffect(guard, interaction) { guard.touch() }
    return this.pointerInput(guard) {
        awaitPointerEventScope {
            while (true) if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) guard.touch()
        }
    }
}

@Composable internal fun ReadingSleepPanel(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("阅读时延长亮屏", Modifier.weight(1f))
        Switch(enabled, onChange, Modifier.semantics { contentDescription = "阅读时延长亮屏" })
    }
    Text("阅读操作后保持亮屏 10 分钟，随后遵循系统设置。离开阅读或切到后台时恢复系统熄屏策略。", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
}

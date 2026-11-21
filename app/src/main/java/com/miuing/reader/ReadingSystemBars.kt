package com.miuing.reader

import android.os.Build
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat

/** Own each window's system-bar appearance only while its reader surface exists. */
@Suppress("DEPRECATION")
@Composable internal fun ReadingSystemBars(window: Window?, statusPaper: Color, navigationPaper: Color = statusPaper) {
    DisposableEffect(window,statusPaper,navigationPaper) {
        if(window==null) return@DisposableEffect onDispose {}
        val controller=WindowCompat.getInsetsController(window,window.decorView)
        val status=controller.isAppearanceLightStatusBars
        val navigation=controller.isAppearanceLightNavigationBars
        val legacy=Build.VERSION.SDK_INT<35
        val statusColor=if(legacy) window.statusBarColor else 0
        val navigationColor=if(legacy) window.navigationBarColor else 0
        controller.isAppearanceLightStatusBars=statusPaper.luminance()>.5f
        controller.isAppearanceLightNavigationBars=navigationPaper.luminance()>.5f
        // Android15+ draws enforced edge-to-edge bars over the reader itself.
        if(legacy) { window.statusBarColor=statusPaper.toArgb();window.navigationBarColor=navigationPaper.toArgb() }
        onDispose {
            controller.isAppearanceLightStatusBars=status
            controller.isAppearanceLightNavigationBars=navigation
            if(legacy) { window.statusBarColor=statusColor;window.navigationBarColor=navigationColor }
        }
    }
}

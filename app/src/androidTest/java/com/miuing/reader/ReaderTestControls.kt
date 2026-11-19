package com.miuing.reader

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

fun UiDevice.readerAction(description: String): UiObject2 {
    findObject(By.desc(description))?.let { return it }
    wait(Until.findObject(By.desc("阅读菜单")), 5000)?.click()
    return wait(Until.findObject(By.desc(description)), 5000)
        ?: throw AssertionError("Missing reader action: $description")
}

fun UiDevice.openReaderThemes() {
    findObject(By.text("aA"))?.let { it.click(); return } // Existing PDF controls.
    (wait(Until.findObject(By.desc("阅读菜单")), 5000)
        ?: throw AssertionError("Missing reading menu")).click()
    (wait(Until.findObject(By.desc("主题与设置")), 5000)
        ?: throw AssertionError("Missing Themes & Settings menu action")).click()
}

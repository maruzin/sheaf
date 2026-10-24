package com.sheaf.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.sheaf.app"
private const val TIMEOUT_MS = 5_000L

/** Gets past first-run onboarding if it is showing, then waits for the library. */
internal fun MacrobenchmarkScope.reachLibrary() {
    device.wait(Until.hasObject(By.text("Get started")), 1_500)
    device.findObject(By.text("Get started"))?.click()
    device.wait(Until.hasObject(By.desc("Open PDF")), TIMEOUT_MS)
}

/** Scrolls the library list if it has content (fresh installs show the empty state). */
internal fun MacrobenchmarkScope.scrollLibrary() {
    val list = device.findObject(By.scrollable(true)) ?: return
    list.setGestureMargin(device.displayWidth / 5)
    list.fling(androidx.test.uiautomator.Direction.DOWN)
    device.waitForIdle()
    list.fling(androidx.test.uiautomator.Direction.UP)
    device.waitForIdle()
}

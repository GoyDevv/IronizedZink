/*
 * Ironized Zink — real display refresh-rate detection.
 *
 * Used to show the user what "cap to refresh rate" actually caps to on their device
 * (e.g. 60 Hz, 90 Hz, 120 Hz), backed by the real Android Display API rather than a
 * guess. There is no Mesa/Zink environment variable for an arbitrary numeric FPS
 * target — the real mechanism is VSync (vblank_mode=1 / FORCE_VSYNC=true), which syncs
 * to whatever the display's current mode actually is. This module only reads that value
 * for display purposes; it does not invent a fake throttle.
 *
 * Licensed under GPL-3.0 (see LICENSE).
 */
package com.goydevv.ironizedzink

import android.content.Context
import android.os.Build
import android.view.Display
import android.view.WindowManager
import kotlin.math.roundToInt

object RefreshRate {
    /** The current display's refresh rate in Hz, rounded to the nearest whole number. */
    fun currentHz(context: Context): Int {
        val display = defaultDisplay(context) ?: return 60
        return display.refreshRate.roundToInt().coerceAtLeast(1)
    }

    /** The highest refresh rate any mode of the current display supports, in Hz. */
    fun maxSupportedHz(context: Context): Int {
        val display = defaultDisplay(context) ?: return currentHz(context)
        val modes = runCatching { display.supportedModes }.getOrNull().orEmpty()
        val max = modes.maxOfOrNull { it.refreshRate } ?: display.refreshRate
        return max.roundToInt().coerceAtLeast(currentHz(context))
    }

    private fun defaultDisplay(context: Context): Display? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        }
    }.getOrNull()
}

package com.pallu.tryon.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Turns the app's "home screen" entry on or off. In mirror mode Android will then offer Pallu as
 * the home app (choose "Always"), so the kiosk boots straight into the mirror and the home
 * button can't take customers out of it. Staff phones never see this.
 */
object KioskLock {
    fun setHomeScreen(context: Context, enabled: Boolean) {
        val component = ComponentName(context.packageName, "com.pallu.tryon.KioskHome")
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        runCatching {
            context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
}

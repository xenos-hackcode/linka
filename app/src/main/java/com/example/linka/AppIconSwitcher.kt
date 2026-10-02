package com.example.linka

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Android has no API to change a launcher icon at runtime by itself - toggling which
 * of three dormant activity-alias entries (declared in AndroidManifest.xml, all
 * targeting MainActivity) is enabled via PackageManager is the standard mechanism.
 * See AndroidManifest.xml's comment on the aliases for why MainActivity itself has no
 * launcher intent-filter of its own. */
enum class IconStyle(private val aliasSimpleName: String, val drawableRes: Int, val label: String) {
    CYBER("IconCyber", R.drawable.ic_launcher_cyber, "Cyber"),
    GLASSY("IconGlassy", R.drawable.ic_launcher_glassy, "Glassy"),
    HAZY("IconHazy", R.drawable.ic_launcher_hazy, "Hazy");

    fun componentName(context: Context) = ComponentName(context, "com.example.linka.$aliasSimpleName")
}

object AppIconSwitcher {
    /** Reads the actual PackageManager component-enabled state rather than a separate
     * preference flag, so the two can never drift out of sync with what's really
     * enabled. Falls back to CYBER (the manifest default) if somehow none report as
     * explicitly enabled. */
    fun current(context: Context): IconStyle {
        val pm = context.packageManager
        return IconStyle.entries.firstOrNull { style ->
            val state = pm.getComponentEnabledSetting(style.componentName(context))
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && style == IconStyle.CYBER)
        } ?: IconStyle.CYBER
    }

    fun switchTo(context: Context, target: IconStyle) {
        val pm = context.packageManager
        IconStyle.entries.forEach { style ->
            val desired = if (style == target) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            // DONT_KILL_APP: switching from within a running Settings screen shouldn't
            // abruptly kill this process - the launcher icon still updates, just
            // without the jarring restart.
            pm.setComponentEnabledSetting(style.componentName(context), desired, PackageManager.DONT_KILL_APP)
        }
    }
}

package dev.breathgate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** A gate that dies at reboot is a gate you stop trusting. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs(context).enabled) return
        if (!Settings.canDrawOverlays(context)) return
        GateService.start(context)
    }
}

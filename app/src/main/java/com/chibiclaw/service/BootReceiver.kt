package com.chibiclaw.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chibiclaw.data.prefs.SecurePreferences
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * BootReceiver — auto-restart ChibiService setelah reboot.
 *
 * Phase 0: kalau setup wizard sudah selesai, restart service.
 * Phase 2+: catatan Android 14+ membatasi FGS start dari BOOT_COMPLETED — hanya
 * `specialUse` yang boleh. Microphone FGS akan upgrade nanti via user-tap notif.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var securePreferences: SecurePreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        if (!securePreferences.isSetupComplete()) {
            Timber.i("BootReceiver: setup belum complete, skip auto-start")
            return
        }

        Timber.i("BootReceiver: re-start ChibiService after boot")
        ChibiService.start(context)
    }
}

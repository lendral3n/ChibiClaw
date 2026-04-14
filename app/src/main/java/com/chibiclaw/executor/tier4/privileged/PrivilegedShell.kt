package com.chibiclaw.executor.tier4.privileged

import android.content.Context
import android.util.Log
import com.chibiclaw.service.ShizukuHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 10 — unified privileged-shell facade.
 *
 * Abstracts the "how do we run a shell command with shell UID (2000)
 * without root?" decision away from the tier-4 executors. Tries
 * providers in priority order and returns the first one that's live:
 *
 *   1. **Shizuku** (external app, best-known) — still works if the
 *      user has the Shizuku Manager installed and has started the
 *      service via Wireless Debugging or ADB.
 *
 *   2. **EmbeddedAdb** (ChibiClaw-internal) — NEW in Phase 10. Uses
 *      Android 11+ Wireless Debugging to start an ADB client from
 *      inside the app itself, so the user doesn't need to install
 *      any external helper app. Pairing is one-time, then ChibiClaw
 *      holds an RSA keypair and auto-reconnects on every boot.
 *
 *   3. **None** — no privileged path available. Executors that need
 *      this facade return "privileged_not_available" (equivalent to
 *      the old "shizuku_not_available" sentinel) and the user sees
 *      a clean error instead of a crash.
 *
 * The facade is a drop-in replacement for direct [ShizukuHandler]
 * calls — [com.chibiclaw.executor.tier4.ShizukuExecutor] can be
 * progressively migrated to route through here without changing its
 * public API.
 */
@Singleton
class PrivilegedShell @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuHandler: ShizukuHandler,
    private val embeddedAdb: EmbeddedAdbManager
) {

    enum class Provider { NONE, SHIZUKU, EMBEDDED_ADB }

    private val _activeProvider = MutableStateFlow(Provider.NONE)
    val activeProvider: StateFlow<Provider> = _activeProvider.asStateFlow()

    init {
        refresh()
    }

    /**
     * Re-evaluates which provider is live. Call this after the user
     * grants Shizuku access or completes ADB pairing so the tier-4
     * executors pick up the new capability without restarting.
     */
    fun refresh(): Provider {
        val provider = when {
            Shizuku.pingBinder() && shizukuHandler.isAvailable() -> Provider.SHIZUKU
            embeddedAdb.isPaired() && embeddedAdb.isConnected() -> Provider.EMBEDDED_ADB
            else -> Provider.NONE
        }
        if (_activeProvider.value != provider) {
            Log.d(TAG, "provider changed: ${_activeProvider.value} → $provider")
        }
        _activeProvider.value = provider
        return provider
    }

    fun isAvailable(): Boolean = refresh() != Provider.NONE

    /**
     * Runs [command] on whichever provider is currently live. Returns
     * the captured stdout on success, or the sentinel string
     * "privileged_not_available" when neither Shizuku nor EmbeddedAdb
     * can take the call.
     *
     * The executor tier is responsible for shell-escaping the
     * command before handing it here — this layer does NOT re-escape.
     */
    suspend fun exec(command: String): String {
        return when (refresh()) {
            Provider.SHIZUKU -> shizukuHandler.executeShell(command)
            Provider.EMBEDDED_ADB -> embeddedAdb.exec(command)
            Provider.NONE -> "privileged_not_available"
        }
    }

    /**
     * Human-readable status string used by the Permission Wizard /
     * AI Settings "Privileged mode" card. Kept short on purpose.
     */
    fun statusLabel(): String = when (refresh()) {
        Provider.SHIZUKU -> "Shizuku Manager aktif"
        Provider.EMBEDDED_ADB -> "Embedded ADB aktif (Wireless Debugging)"
        Provider.NONE -> "Tidak ada akses privileged"
    }

    companion object {
        private const val TAG = "PrivilegedShell"
    }
}

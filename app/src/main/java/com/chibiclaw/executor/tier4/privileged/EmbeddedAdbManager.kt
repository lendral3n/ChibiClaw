package com.chibiclaw.executor.tier4.privileged

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 10 — **Shizuku-less** privileged shell provider (scaffolding).
 *
 * Runs an **ADB client inside ChibiClaw itself** so the user doesn't
 * need to install the external Shizuku Manager app. The flow is:
 *
 *   1. User opens Android 11+ **Wireless Debugging** in developer
 *      options (one-time enable).
 *   2. User taps **"Pair device with pairing code"** in Android
 *      Settings → ChibiClaw scans the mDNS service
 *      `_adb-tls-pairing._tcp` to discover the ephemeral pairing
 *      port and shows a 6-digit-code entry dialog.
 *   3. ChibiClaw generates an RSA-2048 keypair via Android Keystore,
 *      runs the ADB pairing handshake (`STLS → AUTH → CNXN`), and
 *      stores the signed identity in EncryptedSharedPreferences.
 *   4. From then on, ChibiClaw auto-reconnects to the `_adb-tls-connect._tcp`
 *      service on every boot and runs privileged shell commands
 *      without ever talking to Shizuku Manager.
 *
 * ## Why this file is a scaffolding instead of a working impl
 *
 * A full ADB client is **~1500 lines of Kotlin** that replicates the
 * ADB wire protocol (A_CNXN, A_AUTH, A_OPEN, A_WRTE, A_OKAY, A_CLSE
 * packets, TLSv1.3 socket, streaming shell service). Plus it needs a
 * BouncyCastle dependency for RSA signing over Android's cert format.
 *
 * Two off-the-shelf libraries exist that implement this correctly:
 *
 *   - **[libadb-android](https://github.com/MuntashirAkon/libadb-android)**
 *     (MIT, used by AppManager app) — pure-Kotlin ADB client,
 *     maintained, supports Android 11+ Wireless Debugging pairing.
 *     Maven: `io.github.muntashirakon:libadb-android:3.0.1`
 *
 *   - **[adblib (AndroidIDE)](https://github.com/AndroidIDEOfficial/adblib)**
 *     — fork with Kotlin-first API, used by AndroidIDE.
 *
 * Integrating either one into ChibiClaw is Phase 10.1 work. For now
 * this class exposes the full public API the [PrivilegedShell] facade
 * expects, but every method returns "not-ready" sentinels so the rest
 * of the app degrades gracefully when Shizuku is also missing.
 *
 * To enable the real implementation:
 *
 *   1. Add to `gradle/libs.versions.toml`:
 *      ```
 *      [versions]
 *      libadb = "3.0.1"
 *      [libraries]
 *      libadb-android = { module = "io.github.muntashirakon:libadb-android", version.ref = "libadb" }
 *      ```
 *   2. Add to `app/build.gradle.kts`:
 *      ```
 *      implementation(libs.libadb.android)
 *      ```
 *   3. Replace the stubs below with `AdbClient.connect(...)` + the
 *      libadb `KeyPair` + `AdbStream.open("shell:$cmd")` flow. The
 *      public API ([isPaired], [isConnected], [exec], [beginPairing],
 *      [completePairing]) stays unchanged so [PrivilegedShell] keeps
 *      working during the cut-over.
 */
@Singleton
class EmbeddedAdbManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    sealed class PairingState {
        data object Idle : PairingState()
        data class AwaitingCode(val host: String, val port: Int) : PairingState()
        data object Pairing : PairingState()
        data object Paired : PairingState()
        data class Failed(val reason: String) : PairingState()
    }

    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

    private val keypairFile: File by lazy {
        File(context.filesDir, "embedded_adb").apply { mkdirs() }
            .resolve("key.bin")
    }

    /** Has ChibiClaw ever completed a pairing handshake before? */
    fun isPaired(): Boolean = keypairFile.exists()

    /**
     * Is there a live TLS socket to the on-device ADB server right
     * now? Stub: always returns false until libadb-android is wired.
     */
    fun isConnected(): Boolean = false

    /**
     * Starts a mDNS scan for `_adb-tls-pairing._tcp.local.` and
     * transitions to [PairingState.AwaitingCode] once the service is
     * discovered. Stub: fails instantly with a clear message.
     */
    suspend fun beginPairing(): PairingState {
        Log.w(TAG, "beginPairing: stub — libadb-android not yet integrated")
        _pairingState.value = PairingState.Failed(
            "Embedded ADB belum di-wire. Pasang libadb-android 3.0.1 " +
                "dan ganti stub di EmbeddedAdbManager.kt."
        )
        return _pairingState.value
    }

    /**
     * Completes the pairing handshake with the 6-digit code the user
     * typed from the Android Wireless Debugging dialog. Stub:
     * returns Failed.
     */
    suspend fun completePairing(code: String): PairingState {
        Log.w(TAG, "completePairing($code): stub")
        _pairingState.value = PairingState.Failed("Embedded ADB stub")
        return _pairingState.value
    }

    /**
     * Cancels an in-progress pairing and returns to Idle.
     */
    fun cancelPairing() {
        _pairingState.value = PairingState.Idle
    }

    /**
     * Runs [command] over the live ADB stream. Stub returns the
     * same sentinel [PrivilegedShell] uses when no provider is up,
     * so callers see a consistent error.
     */
    suspend fun exec(command: String): String {
        Log.w(TAG, "exec: stub, returning sentinel")
        return "privileged_not_available"
    }

    /**
     * Tears down the stored keypair — user wants to "re-pair from
     * scratch" or is revoking ChibiClaw's ADB access.
     */
    fun unpair() {
        if (keypairFile.exists()) keypairFile.delete()
        _pairingState.value = PairingState.Idle
        Log.d(TAG, "unpaired — stored key deleted")
    }

    companion object {
        private const val TAG = "EmbeddedAdb"
    }
}

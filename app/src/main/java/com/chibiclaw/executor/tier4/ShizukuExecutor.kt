package com.chibiclaw.executor.tier4

import android.util.Log
import com.chibiclaw.executor.ShizukuAction
import com.chibiclaw.service.ShizukuHandler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 5.1 — privileged command executor driven by Shizuku ADB shell.
 *
 * Gemma never gets to send arbitrary shell strings — every call goes
 * through a [ShizukuAction] with a *kind* that is matched against a
 * whitelist. The whitelist is intentionally narrow:
 *
 *  - put_secure       → settings put secure <key> <value>
 *  - put_global       → settings put global <key> <value>
 *  - put_system       → settings put system <key> <value>
 *  - get_secure       → settings get secure <key>
 *  - get_global       → settings get global <key>
 *  - get_system       → settings get system <key>
 *  - force_stop       → am force-stop <pkg>                (whitelisted pkg prefix)
 *  - grant_permission → pm grant <pkg> <permission>
 *  - revoke_permission→ pm revoke <pkg> <permission>
 *  - uninstall_user   → pm uninstall --user 0 <pkg>        (user-installed pkgs only)
 *
 * Any other kind (or a malformed payload) is rejected *before* the
 * command reaches the ShizukuUserService, so a compromised LLM prompt
 * can't exfiltrate an arbitrary pipeline into `/system/bin/sh`.
 */
@Singleton
class ShizukuExecutor @Inject constructor(
    private val shizukuHandler: ShizukuHandler
) {

    suspend fun execute(command: String): String {
        if (!shizukuHandler.isAvailable()) return "shizuku_not_available"
        return shizukuHandler.executeShell(command)
    }

    suspend fun executeAction(action: ShizukuAction): String {
        if (!shizukuHandler.isAvailable()) return "shizuku_not_available"
        val command = buildCommand(action) ?: return "shizuku_rejected: kind=${action.kind}"
        Log.d(TAG, "Running via Shizuku: ${command.take(120)}")
        return shizukuHandler.executeShell(command)
    }

    private fun buildCommand(action: ShizukuAction): String? {
        val payload = action.payload.trim()
        return when (action.kind.lowercase()) {
            "put_secure" -> parseKeyValue(payload)?.let { (k, v) ->
                "settings put secure ${shellEscape(k)} ${shellEscape(v)}"
            }
            "put_global" -> parseKeyValue(payload)?.let { (k, v) ->
                "settings put global ${shellEscape(k)} ${shellEscape(v)}"
            }
            "put_system" -> parseKeyValue(payload)?.let { (k, v) ->
                "settings put system ${shellEscape(k)} ${shellEscape(v)}"
            }
            "get_secure" -> if (isSafeKey(payload)) "settings get secure $payload" else null
            "get_global" -> if (isSafeKey(payload)) "settings get global $payload" else null
            "get_system" -> if (isSafeKey(payload)) "settings get system $payload" else null
            "force_stop" -> if (isSafePackage(payload)) "am force-stop $payload" else null
            "grant_permission" -> parseKeyValue(payload)?.let { (pkg, perm) ->
                if (isSafePackage(pkg) && isSafePermission(perm)) "pm grant $pkg $perm" else null
            }
            "revoke_permission" -> parseKeyValue(payload)?.let { (pkg, perm) ->
                if (isSafePackage(pkg) && isSafePermission(perm)) "pm revoke $pkg $perm" else null
            }
            "uninstall_user" -> if (isSafePackage(payload) && !isSystemPackage(payload)) {
                "pm uninstall --user 0 $payload"
            } else null
            else -> null
        }
    }

    private fun parseKeyValue(payload: String): Pair<String, String>? {
        val idx = payload.indexOf(' ')
        if (idx <= 0) return null
        val k = payload.substring(0, idx).trim()
        val v = payload.substring(idx + 1).trim()
        if (k.isEmpty() || v.isEmpty()) return null
        if (!isSafeKey(k)) return null
        return k to v
    }

    private fun isSafeKey(s: String): Boolean =
        s.matches(Regex("[a-zA-Z0-9_.]+"))

    private fun isSafePackage(s: String): Boolean =
        s.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+"))

    private fun isSafePermission(s: String): Boolean =
        s.matches(Regex("[a-zA-Z0-9_.]+"))

    /** Blocks uninstall of anything in the Android or Google system package tree. */
    private fun isSystemPackage(pkg: String): Boolean {
        val blocked = listOf(
            "android",
            "com.android.",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.packageinstaller",
            "com.chibiclaw"
        )
        return blocked.any { pkg == it || pkg.startsWith(it) }
    }

    /**
     * Extremely conservative shell escape — we only allow alnum, dot,
     * underscore, dash and slash in unquoted form; everything else is
     * single-quoted with embedded single-quotes replaced by `'\''`.
     */
    private fun shellEscape(v: String): String {
        if (v.matches(Regex("[a-zA-Z0-9_./\\-]+"))) return v
        val escaped = v.replace("'", "'\\''")
        return "'$escaped'"
    }

    companion object {
        private const val TAG = "ShizukuExecutor"
    }
}

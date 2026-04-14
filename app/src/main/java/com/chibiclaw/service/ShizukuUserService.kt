package com.chibiclaw.service

import android.util.Log
import com.chibiclaw.api.IChibiShizuku

class ShizukuUserService : IChibiShizuku.Stub() {

    override fun executeShell(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (error.isNotEmpty()) "ERROR: $error" else output.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Shell exec failed: ${e.message}")
            "ERROR: ${e.message}"
        }
    }

    override fun destroy() {
        Log.d(TAG, "ShizukuUserService destroyed")
    }

    companion object {
        private const val TAG = "ShizukuUserService"
    }
}

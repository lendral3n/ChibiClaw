package com.chibiclaw.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.chibiclaw.api.IChibiShizuku
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuHandler @Inject constructor() {

    private var userService: IChibiShizuku? = null
    private var isBound = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.chibiclaw", ShizukuUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("shizuku")
        .debuggable(false)
        .version(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            userService = IChibiShizuku.Stub.asInterface(service)
            isBound = true
            Log.d(TAG, "Shizuku service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
            isBound = false
            Log.w(TAG, "Shizuku service disconnected")
        }
    }

    fun bindService() {
        if (!isBound && Shizuku.pingBinder()) {
            Shizuku.bindUserService(userServiceArgs, connection)
        }
    }

    fun unbind() {
        if (isBound) {
            try {
                Shizuku.unbindUserService(userServiceArgs, connection, true)
            } catch (e: Exception) {
                Log.e(TAG, "Unbind failed: ${e.message}")
            }
            isBound = false
            userService = null
        }
    }

    suspend fun executeShell(command: String): String {
        val service = userService ?: return "ERROR: Shizuku not bound"
        return try {
            service.executeShell(command)
        } catch (e: Exception) {
            Log.e(TAG, "Shell exec error: ${e.message}")
            "ERROR: ${e.message}"
        }
    }

    fun isAvailable(): Boolean = isBound && userService != null

    companion object {
        private const val TAG = "ShizukuHandler"
    }
}

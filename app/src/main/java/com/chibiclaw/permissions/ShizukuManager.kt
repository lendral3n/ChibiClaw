package com.chibiclaw.permissions

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.chibiclaw.api.IChibiShizukuService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ShizukuManager — wrapper untuk Shizuku availability + permission check +
 * UserService bind.
 *
 * UserService di-bind sekali (lazy) saat tool pertama panggil. Subsequent
 * call reuse IBinder yang sama.
 *
 * Status check graceful: kalau Shizuku tidak running atau permission belum
 * granted, return state yang clear sehingga ToolDispatcher bisa fallback.
 */
@Singleton
class ShizukuManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val mutex = Mutex()
    @Volatile private var binder: IChibiShizukuService? = null
    @Volatile private var connection: ServiceConnection? = null

    /**
     * Cek apakah Shizuku service running (UID 2000 ADB atau UID 0 Sui).
     */
    fun isShizukuAvailable(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.getVersion() >= 10
    }.getOrDefault(false)

    /**
     * Cek apakah ChibiClaw sudah dapat permission grant dari Shizuku.
     */
    fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * Bind UserService — lazy + cached. Kalau Shizuku tidak available atau
     * permission belum granted, return null (ToolDispatcher harus handle).
     */
    suspend fun acquireService(): IChibiShizukuService? = mutex.withLock {
        binder?.let { return@withLock it }
        if (!isShizukuAvailable()) {
            Timber.w("Shizuku service tidak running")
            return@withLock null
        }
        if (!hasPermission()) {
            Timber.w("ChibiClaw belum dapat permission grant dari Shizuku")
            return@withLock null
        }
        return@withLock try {
            bindUserService()
        } catch (t: Throwable) {
            Timber.e(t, "Shizuku.bindUserService failed")
            null
        }
    }

    private suspend fun bindUserService(): IChibiShizukuService? {
        val deferred = CompletableDeferred<IBinder?>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                deferred.complete(service)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                Timber.w("UserService disconnected: $name")
                binder = null
            }
        }
        connection = conn

        val userServiceArgs = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ChibiShizukuService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("chibi-shizuku")
            .debuggable(com.chibiclaw.BuildConfig.DEBUG)
            .version(1)

        Shizuku.bindUserService(userServiceArgs, conn)
        val rawBinder = deferred.await() ?: return null
        val stub = IChibiShizukuService.Stub.asInterface(rawBinder)
        binder = stub
        return stub
    }

    suspend fun shutdown() = mutex.withLock {
        runCatching { binder?.destroy() }
        connection?.let {
            runCatching {
                val userServiceArgs = Shizuku.UserServiceArgs(
                    ComponentName(context.packageName, ChibiShizukuService::class.java.name)
                ).version(1)
                Shizuku.unbindUserService(userServiceArgs, it, true)
            }
        }
        binder = null
        connection = null
    }
}

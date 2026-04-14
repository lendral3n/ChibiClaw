package com.chibiclaw.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * Helper for MANAGE_EXTERNAL_STORAGE — required by LiteRT-LM which uses
 * native open() on absolute paths like /storage/emulated/0/Download/...
 * Android 11+ scoped storage blocks direct file access without this permission.
 */
object StoragePermissionHelper {

    /**
     * Returns true if the app is allowed to open arbitrary files in shared
     * external storage. On Android <11 this is always true (legacy permission
     * model). On Android 11+ it requires MANAGE_EXTERNAL_STORAGE.
     */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Opens the system settings page where the user can toggle
     * "Allow access to manage all files" for this app. Falls back to
     * the generic page if the app-specific intent is not available.
     */
    fun openAllFilesAccessSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }
}

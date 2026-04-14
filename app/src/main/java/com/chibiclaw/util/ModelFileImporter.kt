package com.chibiclaw.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.chibiclaw.debug.DevLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports a model file selected via Storage Access Framework into the app's
 * private external storage directory. This avoids MANAGE_EXTERNAL_STORAGE
 * entirely — the app-private dir is always readable without permissions.
 *
 * Destination: /storage/emulated/0/Android/data/<pkg>/files/models/<filename>
 */
@Singleton
class ModelFileImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val devLogger: DevLogger
) {
    sealed class Progress {
        data class Copying(val copiedBytes: Long, val totalBytes: Long, val percent: Int) : Progress()
        data class Done(val destinationPath: String) : Progress()
        data class Error(val message: String) : Progress()
    }

    /**
     * Returns the display filename of a content URI, falling back to "model.litertlm".
     */
    fun queryDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx) ?: "model.litertlm"
                }
            }
        return "model.litertlm"
    }

    fun querySizeBytes(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) return cursor.getLong(idx)
                }
            }
        return -1L
    }

    /**
     * Copies the content URI to app-private external files dir and emits progress.
     * Emits Copying updates every ~1% (max ~100 emits for a 4GB file).
     */
    fun importModel(uri: Uri): Flow<Progress> = flow {
        val filename = queryDisplayName(uri).sanitizeFilename()
        val totalBytes = querySizeBytes(uri)
        devLogger.i("IMPORT", "Starting import: $filename (${formatSize(totalBytes)})")

        val modelsDir = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }
        val destFile = File(modelsDir, filename)

        // If a partial copy exists from a previous failed attempt, clear it.
        if (destFile.exists()) {
            devLogger.w("IMPORT", "Overwriting existing file at ${destFile.absolutePath}")
            destFile.delete()
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(1024 * 1024) // 1 MiB chunks
                    var copied = 0L
                    var lastPercent = -1
                    emit(Progress.Copying(0L, totalBytes, 0))

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read

                        if (totalBytes > 0) {
                            val percent = ((copied * 100) / totalBytes).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                emit(Progress.Copying(copied, totalBytes, percent))
                            }
                        }
                    }
                    output.fd.sync() // Make sure bytes are flushed to disk
                }
            } ?: run {
                emit(Progress.Error("Tidak bisa membuka file dari URI"))
                return@flow
            }

            devLogger.i("IMPORT", "✓ Imported to ${destFile.absolutePath} (${formatSize(destFile.length())})")
            emit(Progress.Done(destFile.absolutePath))
        } catch (e: Exception) {
            Log.e(TAG, "Import failed: ${e.message}", e)
            devLogger.e("IMPORT", "✗ Failed: ${e::class.simpleName}: ${e.message}")
            // Clean up partial file
            if (destFile.exists()) destFile.delete()
            emit(Progress.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Returns true if the given path is located inside our app-private external
     * files dir — those paths don't need MANAGE_EXTERNAL_STORAGE.
     */
    fun isAppPrivatePath(path: String): Boolean {
        val privateDir = context.getExternalFilesDir(null)?.absolutePath ?: return false
        return path.startsWith(privateDir)
    }

    private fun String.sanitizeFilename(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun formatSize(bytes: Long): String = when {
        bytes < 0 -> "unknown size"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    companion object {
        private const val TAG = "ModelFileImporter"
    }
}

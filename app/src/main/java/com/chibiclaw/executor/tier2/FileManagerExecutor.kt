package com.chibiclaw.executor.tier2

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 11 — File management: list, info, copy, move, delete, share, zip, unzip, search, storage.
 */
@Singleton
class FileManagerExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun perform(operation: String, path: String, destination: String): String {
        val op = operation.trim().lowercase()
        return try {
            when (op) {
                "list", "ls" -> listFiles(path)
                "info", "detail" -> fileInfo(path)
                "copy", "cp" -> copyFile(path, destination)
                "move", "mv" -> moveFile(path, destination)
                "delete", "rm", "hapus" -> deleteFile(path)
                "share", "kirim" -> shareFile(path)
                "zip", "compress" -> zipFiles(path, destination)
                "search", "find", "cari" -> searchFiles(path, destination)
                "storage_info", "storage", "penyimpanan" -> storageInfo()
                "mkdir", "create_dir" -> createDir(path)
                "exists", "ada" -> {
                    val f = File(path)
                    if (f.exists()) "exists: true (${if (f.isDirectory) "directory" else "file"})" else "exists: false"
                }
                "read", "baca" -> readTextFile(path)
                else -> "file_error: unknown operation '$op'"
            }
        } catch (e: SecurityException) {
            "file_error: permission denied — ${e.message}"
        } catch (e: Exception) {
            "file_error: ${e.message}"
        }
    }

    private fun listFiles(path: String): String {
        val dir = if (path.isBlank()) Environment.getExternalStorageDirectory() else File(path)
        if (!dir.exists()) return "file_error: path not found '$path'"
        if (!dir.isDirectory) return "file_error: not a directory '$path'"
        val files = dir.listFiles() ?: return "file_error: cannot list '$path'"
        val dateFormat = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
        val sb = StringBuilder("[files] ${dir.absolutePath} (${files.size} items)\n")
        files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .take(50)
            .forEach { f ->
                val type = if (f.isDirectory) "DIR" else "FILE"
                val size = if (f.isFile) formatSize(f.length()) else ""
                val date = dateFormat.format(Date(f.lastModified()))
                sb.append("$type ${f.name} $size $date\n")
            }
        if (files.size > 50) sb.append("... dan ${files.size - 50} lainnya")
        return sb.toString()
    }

    private fun fileInfo(path: String): String {
        if (path.isBlank()) return "file_error: empty path"
        val f = File(path)
        if (!f.exists()) return "file_error: not found '$path'"
        return "Name: ${f.name} | Path: ${f.absolutePath} | " +
            "Type: ${if (f.isDirectory) "Directory" else "File"} | " +
            "Size: ${formatSize(if (f.isFile) f.length() else dirSize(f))} | " +
            "Modified: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(f.lastModified()))} | " +
            "Readable: ${f.canRead()} | Writable: ${f.canWrite()}"
    }

    private fun copyFile(src: String, dst: String): String {
        if (src.isBlank() || dst.isBlank()) return "file_error: source and destination required"
        val srcFile = File(src)
        if (!srcFile.exists()) return "file_error: source not found '$src'"
        val dstFile = File(dst)
        if (srcFile.isDirectory) {
            srcFile.copyRecursively(dstFile, overwrite = true)
        } else {
            dstFile.parentFile?.mkdirs()
            srcFile.copyTo(dstFile, overwrite = true)
        }
        return "file_copied: ${srcFile.name} → ${dstFile.absolutePath}"
    }

    private fun moveFile(src: String, dst: String): String {
        val result = copyFile(src, dst)
        if (result.startsWith("file_copied")) {
            val srcFile = File(src)
            if (srcFile.isDirectory) srcFile.deleteRecursively() else srcFile.delete()
            return result.replace("file_copied", "file_moved")
        }
        return result
    }

    private fun deleteFile(path: String): String {
        if (path.isBlank()) return "file_error: empty path"
        val f = File(path)
        if (!f.exists()) return "file_error: not found '$path'"
        // Safety: don't delete root-level directories
        val dangerous = listOf("/storage/emulated/0", "/sdcard", "/data", "/system")
        if (f.absolutePath in dangerous) return "file_error: refusing to delete protected path"
        val deleted = if (f.isDirectory) f.deleteRecursively() else f.delete()
        return if (deleted) "file_deleted: ${f.name}" else "file_error: could not delete"
    }

    private fun shareFile(path: String): String {
        if (path.isBlank()) return "file_error: empty path"
        val f = File(path)
        if (!f.exists() || !f.isFile) return "file_error: file not found '$path'"
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${f.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            "file_shared: ${f.name}"
        } catch (e: Exception) {
            "file_error: ${e.message}"
        }
    }

    private fun zipFiles(src: String, dst: String): String {
        if (src.isBlank()) return "file_error: source path required"
        val srcFile = File(src)
        if (!srcFile.exists()) return "file_error: not found '$src'"
        val zipPath = if (dst.isBlank()) "${srcFile.absolutePath}.zip" else dst
        val zipFile = File(zipPath)
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            if (srcFile.isDirectory) {
                srcFile.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val entry = ZipEntry(file.relativeTo(srcFile).path)
                        zos.putNextEntry(entry)
                        FileInputStream(file).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            } else {
                zos.putNextEntry(ZipEntry(srcFile.name))
                FileInputStream(srcFile).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return "file_zipped: ${zipFile.absolutePath} (${formatSize(zipFile.length())})"
    }

    private fun searchFiles(basePath: String, query: String): String {
        if (query.isBlank()) return "file_error: search query required"
        val base = if (basePath.isBlank()) Environment.getExternalStorageDirectory() else File(basePath)
        if (!base.exists()) return "file_error: base path not found"
        val results = mutableListOf<String>()
        base.walkTopDown()
            .maxDepth(5)
            .filter { it.name.contains(query, ignoreCase = true) }
            .take(20)
            .forEach { f ->
                results += "${if (f.isDirectory) "DIR" else "FILE"} ${f.absolutePath} ${if (f.isFile) formatSize(f.length()) else ""}"
            }
        return if (results.isEmpty()) "search: no results for '$query'"
        else "[search] ${results.size} results:\n${results.joinToString("\n")}"
    }

    private fun storageInfo(): String {
        val parts = mutableListOf<String>()
        // Internal storage
        val internalStat = StatFs(Environment.getDataDirectory().path)
        val internalTotal = internalStat.totalBytes / (1024.0 * 1024 * 1024)
        val internalFree = internalStat.availableBytes / (1024.0 * 1024 * 1024)
        val internalUsed = internalTotal - internalFree
        parts += "Internal: ${f(internalUsed)}/${f(internalTotal)} GB (${f(internalFree)} GB free)"
        // External storage (if different)
        val externalDirs = context.getExternalFilesDirs(null)
        if (externalDirs.size > 1 && externalDirs[1] != null) {
            try {
                val extStat = StatFs(externalDirs[1]!!.path)
                val extTotal = extStat.totalBytes / (1024.0 * 1024 * 1024)
                val extFree = extStat.availableBytes / (1024.0 * 1024 * 1024)
                parts += "SD Card: ${f(extTotal - extFree)}/${f(extTotal)} GB (${f(extFree)} GB free)"
            } catch (_: Exception) {}
        }
        return parts.joinToString(" | ")
    }

    private fun createDir(path: String): String {
        if (path.isBlank()) return "file_error: empty path"
        val dir = File(path)
        return if (dir.mkdirs() || dir.exists()) "dir_created: ${dir.absolutePath}" else "file_error: could not create"
    }

    private fun readTextFile(path: String): String {
        if (path.isBlank()) return "file_error: empty path"
        val f = File(path)
        if (!f.exists() || !f.isFile) return "file_error: not found '$path'"
        if (f.length() > 50_000) return "file_error: file too large (${formatSize(f.length())}), max 50KB"
        return "file_content:\n${f.readText(Charsets.UTF_8).take(5000)}"
    }

    private fun dirSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    private fun f(gb: Double) = String.format("%.1f", gb)
    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "${String.format("%.1f", bytes / 1_073_741_824.0)}GB"
        bytes >= 1_048_576 -> "${String.format("%.1f", bytes / 1_048_576.0)}MB"
        bytes >= 1024 -> "${bytes / 1024}KB"
        else -> "${bytes}B"
    }
}

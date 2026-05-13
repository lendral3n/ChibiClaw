package com.chibiclaw.permissions

import com.chibiclaw.api.IChibiShizukuService
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ChibiShizukuService — UserService implementation yang di-load di proses
 * Shizuku (UID 2000 ADB atau UID 0 Sui). Class ini TIDAK boleh punya
 * dependency Hilt / Android Context — dia jalan di proses terpisah.
 *
 * Pattern: Constructor default required oleh Shizuku.bindUserService.
 */
class ChibiShizukuService : IChibiShizukuService.Stub() {

    override fun exec(command: String, timeoutMs: Long): String {
        if (command.isBlank()) return "(empty command)"
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val output = StringBuilder()

        val readerThread = Thread {
            BufferedReader(InputStreamReader(process.inputStream)).use { r ->
                r.lineSequence().forEach { output.append(it).append('\n') }
            }
        }.apply { isDaemon = true; start() }

        val errReaderThread = Thread {
            BufferedReader(InputStreamReader(process.errorStream)).use { r ->
                r.lineSequence().forEach { output.append("STDERR: ").append(it).append('\n') }
            }
        }.apply { isDaemon = true; start() }

        if (timeoutMs > 0) {
            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return "TIMEOUT after ${timeoutMs}ms\n$output"
            }
        } else {
            process.waitFor()
        }
        readerThread.join(500)
        errReaderThread.join(500)
        return output.toString().trim()
    }

    override fun destroy() {
        System.exit(0)
    }
}

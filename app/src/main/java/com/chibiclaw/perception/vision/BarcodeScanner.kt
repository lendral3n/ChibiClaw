package com.chibiclaw.perception.vision

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Phase 4.4 — ML Kit barcode + QR scanner wrapper.
 *
 * Returns the first barcode's raw value (URL, text, wifi config, etc.)
 * or null if nothing was found. The scanner is instantiated lazily and
 * reused across calls.
 */
@Singleton
class BarcodeScanner @Inject constructor() {
    private val scanner = BarcodeScanning.getClient()

    suspend fun scan(context: Context, uri: Uri): String? {
        val image = InputImage.fromFilePath(context, uri)
        return runScan(image)
    }

    suspend fun scanBitmap(bitmap: Bitmap): String? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return runScan(image)
    }

    private suspend fun runScan(image: InputImage): String? =
        suspendCancellableCoroutine { cont ->
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val first = barcodes.firstOrNull()
                    cont.resume(describe(first))
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }

    private fun describe(barcode: Barcode?): String? {
        if (barcode == null) return null
        val format = formatName(barcode.format)
        val value = barcode.rawValue ?: barcode.displayValue ?: return null
        return "barcode:$format: $value"
    }

    private fun formatName(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "qr"
        Barcode.FORMAT_EAN_13 -> "ean13"
        Barcode.FORMAT_EAN_8 -> "ean8"
        Barcode.FORMAT_CODE_128 -> "code128"
        Barcode.FORMAT_CODE_39 -> "code39"
        Barcode.FORMAT_UPC_A -> "upc_a"
        Barcode.FORMAT_UPC_E -> "upc_e"
        Barcode.FORMAT_PDF417 -> "pdf417"
        Barcode.FORMAT_DATA_MATRIX -> "dm"
        else -> "format_$format"
    }
}

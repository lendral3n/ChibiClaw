package com.chibiclaw.vision.projection

import android.content.Intent
import android.os.Parcel
import com.chibiclaw.data.prefs.SecurePreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ProjectionTokenStore — persist resultCode + resultData dari MediaProjection
 * permission grant.
 *
 * Note: Android 14+ tidak persistent token across process death. Token akan
 * invalid kalau ChibiService di-kill. User harus re-grant via wizard.
 *
 * Strategi:
 *  - Saat user grant, persist Intent.toByteArray() (Parcelable) ke
 *    SecurePreferences sebagai Base64.
 *  - Saat ChibiService start, baca dari SecurePreferences → recreate Intent →
 *    `MediaProjectionManager.getMediaProjection(code, data)`.
 *  - Kalau gagal (token revoked), notify user via notification action.
 *
 * In-memory: holding MediaProjection instance hidup selama service running
 * supaya capture cepat (no re-permission per shot).
 */
@Singleton
class ProjectionTokenStore @Inject constructor(
    private val securePreferences: SecurePreferences,
) {

    fun saveToken(resultCode: Int, data: Intent) {
        securePreferences.putInt(KEY_RESULT_CODE, resultCode)
        val parcel = Parcel.obtain()
        try {
            data.writeToParcel(parcel, 0)
            val bytes = parcel.marshall()
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            securePreferences.putString(KEY_RESULT_DATA, base64)
            Timber.i("MediaProjection token saved (code=$resultCode, ${bytes.size}B)")
        } finally {
            parcel.recycle()
        }
    }

    fun loadResultCode(): Int = securePreferences.getInt(KEY_RESULT_CODE, 0)

    fun loadResultData(): Intent? {
        val base64 = securePreferences.getString(KEY_RESULT_DATA) ?: return null
        return runCatching {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
            val parcel = Parcel.obtain()
            try {
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                Intent.CREATOR.createFromParcel(parcel)
            } finally {
                parcel.recycle()
            }
        }.onFailure { Timber.w(it, "Failed unmarshal projection token") }.getOrNull()
    }

    fun hasToken(): Boolean = loadResultData() != null

    fun clear() {
        securePreferences.putString(KEY_RESULT_DATA, null)
        securePreferences.putInt(KEY_RESULT_CODE, 0)
    }

    companion object {
        private const val KEY_RESULT_CODE = "media_projection_result_code"
        private const val KEY_RESULT_DATA = "media_projection_result_data_b64"
    }
}

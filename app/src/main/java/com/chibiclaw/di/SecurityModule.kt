package com.chibiclaw.di

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chibiclaw.data.prefs.SecurePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Security / encryption bindings.
 *
 * - SQLCipher passphrase: 32-byte random, generated once, stored di
 *   EncryptedSharedPreferences (Android Keystore-backed).
 * - SecurePreferences wrapper untuk semua secret (API key, cloud session token,
 *   consent flags, dll).
 *
 * Catatan: kalau user erase data → SecurePreferences clear → passphrase hilang →
 * SQLCipher database tidak bisa dibuka lagi (intentional, "right to be forgotten").
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    private const val SECURE_PREFS_FILE = "chibiclaw_secure_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase_b64"

    @Provides
    @Singleton
    fun provideMasterKey(@ApplicationContext context: Context): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    @Provides
    @Singleton
    fun provideEncryptedPrefs(
        @ApplicationContext context: Context,
        masterKey: MasterKey,
    ): EncryptedSharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as EncryptedSharedPreferences
    }

    @Provides
    @Singleton
    fun provideSecurePreferences(prefs: EncryptedSharedPreferences): SecurePreferences {
        return SecurePreferences(prefs)
    }

    @Provides
    @Singleton
    fun provideDatabasePassphrase(prefs: EncryptedSharedPreferences): ByteArray {
        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null) {
            return android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)
        }
        // Generate new 32-byte passphrase, store base64.
        val fresh = ByteArray(32).also {
            java.security.SecureRandom().nextBytes(it)
        }
        prefs.edit()
            .putString(KEY_DB_PASSPHRASE, android.util.Base64.encodeToString(fresh, android.util.Base64.NO_WRAP))
            .apply()
        return fresh
    }
}

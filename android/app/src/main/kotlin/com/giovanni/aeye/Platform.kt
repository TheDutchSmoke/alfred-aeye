package com.giovanni.aeye

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.giovanni.aeye.core.AeyeError
import com.giovanni.aeye.core.AeyeSnapshot
import com.giovanni.aeye.core.RowVisibility
import com.giovanni.aeye.core.SecretStore
import com.giovanni.aeye.core.SnapshotCodec
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Keychain's job on Android: each credential is encrypted with an AES-GCM key that lives in
 * the Android Keystore and never leaves this device, and the ciphertext sits in its own
 * preferences file, excluded from backup and device transfer (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`) — the
 * equivalent of `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`.
 */
class KeystoreSecretStore(context: Context) : SecretStore {
    private val prefs = context.getSharedPreferences("aeye_credentials", Context.MODE_PRIVATE)

    override fun load(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_LENGTH))
            String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8)
        } catch (_: Exception) {
            // A wiped key (or a copy restored onto another device) cannot decrypt what is here;
            // treat it as signed out rather than crash, and let Settings ask again.
            null
        }
    }

    override fun save(key: String, value: String) {
        val ok = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val sealed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(key, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()
        } catch (_: Exception) {
            false
        }
        if (!ok) throw AeyeError("Keystore save failed")
    }

    override fun delete(key: String) {
        prefs.edit().remove(key).commit()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "aeye_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}

/**
 * The App Group on iOS: the last snapshot (a JSON file) plus the row choices and the sample-data
 * preference. The widget runs in the app's process here, so plain app storage is shared already.
 */
class SnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences("aeye", Context.MODE_PRIVATE)
    private val file = File(File(context.filesDir, "Aeye").apply { mkdirs() }, "aeye-snapshot.json")

    fun save(snapshot: AeyeSnapshot) {
        try {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(SnapshotCodec.encode(snapshot))
            tmp.renameTo(file)
        } catch (_: Exception) {
            // Fail silently — the widget falls back to the last good snapshot.
        }
    }

    fun load(): AeyeSnapshot? =
        try {
            if (file.exists()) SnapshotCodec.decode(file.readText()) else null
        } catch (_: Exception) {
            null
        }

    fun saveVisibility(visibility: RowVisibility) {
        prefs.edit().putString(VISIBILITY_KEY, SnapshotCodec.encodeVisibility(visibility)).apply()
    }

    fun loadVisibility(): RowVisibility =
        prefs.getString(VISIBILITY_KEY, null)?.let { SnapshotCodec.decodeVisibility(it) } ?: RowVisibility.allEnabled

    /** Backs [SampleDataMode]; lives beside the snapshot it produces. */
    var isSampleDataEnabled: Boolean
        get() = prefs.getBoolean(SAMPLE_DATA_KEY, false)
        set(value) {
            prefs.edit().putBoolean(SAMPLE_DATA_KEY, value).apply()
        }

    private companion object {
        const val VISIBILITY_KEY = "row-visibility-v1"
        const val SAMPLE_DATA_KEY = "sample-data-enabled-v1"
    }
}

/**
 * Shows `MockSnapshot` instead of live numbers, so Aeye can be tried, demonstrated and
 * store-reviewed without a Cursor or Claude account. Every surface labels it.
 *
 * Debug builds also accept the iOS screenshot launch arguments as intent extras
 * (`--ez screenshot true`, `--ez screenshot_settings true`): they force the sample on without
 * writing the preference, and without the chip.
 */
class SampleDataMode(private val store: SnapshotStore) {
    var isForcedByLaunchArgument: Boolean = false
    var opensSettingsAtLaunch: Boolean = false

    var isEnabled: Boolean
        get() = isForcedByLaunchArgument || store.isSampleDataEnabled
        set(value) {
            store.isSampleDataEnabled = value
        }
}

/** Where the About section points, and the version it shows. */
object AppLinks {
    // App Review (and Play review) expects the disclaimer and both URLs inside the app.
    const val PRIVACY = "https://giovannicoppola.github.io/alfred-aeye/android/privacy.html"
    const val SUPPORT = "https://github.com/giovannicoppola/alfred-aeye/issues"
    val version: String get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
}

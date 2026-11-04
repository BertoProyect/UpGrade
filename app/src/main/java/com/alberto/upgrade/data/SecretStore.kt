package com.alberto.upgrade.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda la clave de Gemini cifrada con una clave AES del Android Keystore.
 * La clave AES nunca sale del móvil (no entra en copias de seguridad), y el archivo
 * `secrets.xml` está excluido del backup en `res/xml/backup_rules.xml` y `data_extraction_rules.xml`.
 */
object SecretStore {
    private const val PREFS = "secrets"
    private const val ALIAS = "upgrade_secrets_v1"
    private const val GEMINI = "gemini_key"
    /** Si el Keystore falla en algún móvil, la clave queda aquí sin cifrar (sigue fuera de copias). */
    private const val GEMINI_PLAIN = "gemini_key_plain"

    private lateinit var prefs: SharedPreferences

    @Volatile
    private var cached: String? = null

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var geminiKey: String
        get() {
            cached?.let { return it }
            if (!::prefs.isInitialized) return ""
            val v = prefs.getString(GEMINI, null)?.let { decrypt(it) }
                ?: prefs.getString(GEMINI_PLAIN, null)
                ?: ""
            cached = v
            return v
        }
        set(value) {
            val v = value.trim()
            cached = v
            if (!::prefs.isInitialized) return
            val e = prefs.edit().remove(GEMINI).remove(GEMINI_PLAIN)
            if (v.isNotEmpty()) {
                val enc = encrypt(v)
                if (enc != null) e.putString(GEMINI, enc) else e.putString(GEMINI_PLAIN, v)
            }
            e.commit()
        }

    fun hasGeminiKey(): Boolean = geminiKey.isNotBlank()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        val out = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(out, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(stored: String): String? = runCatching {
        val (ivB, dataB) = stored.split(":", limit = 2)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(ivB, Base64.NO_WRAP)))
        String(c.doFinal(Base64.decode(dataB, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()
}

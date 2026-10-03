package com.reex.idex.core
import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AiKeyStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("reex_ai", Context.MODE_PRIVATE)
    private val alias = "reex_ai_key_v1"
    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(alias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
    fun saveKey(value: String) {
        if (value.isBlank()) { prefs.edit().remove("key").apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString("key", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }
    fun readKey(): String = runCatching {
        val raw = Base64.decode(prefs.getString("key", null), Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, 12)
        val data = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(data), StandardCharsets.UTF_8)
    }.getOrDefault("")
    fun clear() = prefs.edit().remove("key").apply()
}

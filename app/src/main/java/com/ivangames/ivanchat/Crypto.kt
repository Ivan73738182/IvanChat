package com.ivangames.ivanchat

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Crypto {

    private const val ALGO = "AES/CBC/PKCS5Padding"
    private const val KEY_ALGO = "AES"

    /**
     * Из пароля (ключ чата) делаем 256-битный AES-ключ.
     * Используем SHA-256.
     */
    private fun deriveKey(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(hash, KEY_ALGO)
    }

    /**
     * Шифрует строку. Возвращает Base64 (IV + зашифрованный текст).
     */
    fun encrypt(plainText: String, password: String): String {
        return try {
            val key = deriveKey(password)
            val cipher = Cipher.getInstance(ALGO)
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // склеиваем IV + encrypted
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Расшифровывает Base64-строку (IV + encrypted).
     */
    fun decrypt(cipherText: String, password: String): String {
        return try {
            val combined = Base64.decode(cipherText, Base64.NO_WRAP)
            if (combined.size < 16) return ""

            val iv = ByteArray(16)
            System.arraycopy(combined, 0, iv, 0, 16)

            val encrypted = ByteArray(combined.size - 16)
            System.arraycopy(combined, 16, encrypted, 0, encrypted.size)

            val key = deriveKey(password)
            val cipher = Cipher.getInstance(ALGO)
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            val decrypted = cipher.doFinal(encrypted)

            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}

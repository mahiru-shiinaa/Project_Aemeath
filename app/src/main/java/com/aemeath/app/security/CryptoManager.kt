package com.aemeath.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoManager @Inject constructor() {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val BIOMETRIC_KEY_ALIAS = "aemeath_biometric_key"

        private const val AES_ALGORITHM = "AES"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12  // 96 bits

        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 310_000  // OWASP recommended 2023
        private const val PBKDF2_KEY_LENGTH = 256
        private const val SALT_LENGTH = 32
    }

    // ─── PBKDF2 Key Derivation ───────────────────────────────────────────────

    /**
     * Tạo salt ngẫu nhiên mới (32 bytes)
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * Derive AES-256 key từ Master Password + salt
     */
    fun deriveKeyFromPassword(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_LENGTH
        )
        val secretKey = factory.generateSecret(spec)
        spec.clearPassword()
        return SecretKeySpec(secretKey.encoded, AES_ALGORITHM)
    }

    // ─── AES-256-GCM Encryption ─────────────────────────────────────────────

    /**
     * Mã hóa plaintext bằng key
     * @return ByteArray = IV (12 bytes) + Ciphertext + GCM tag
     */
    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val paramSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, paramSpec)
        val ciphertext = cipher.doFinal(plaintext)
        // Prepend IV vào output
        return iv + ciphertext
    }

    /**
     * Giải mã. Input phải là output của encrypt() — IV (12 bytes) + ciphertext
     */
    fun decrypt(encryptedData: ByteArray, key: SecretKey): ByteArray {
        val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val paramSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, paramSpec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Mã hóa String → Base64 String
     */
    fun encryptString(plaintext: String, key: SecretKey): String {
        val encrypted = encrypt(plaintext.toByteArray(Charsets.UTF_8), key)
        return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
    }

    /**
     * Giải mã Base64 String → String
     */
    fun decryptString(encryptedBase64: String, key: SecretKey): String {
        val encrypted = android.util.Base64.decode(encryptedBase64, android.util.Base64.NO_WRAP)
        return decrypt(encrypted, key).toString(Charsets.UTF_8)
    }

    // ─── Password Verification ───────────────────────────────────────────────

    /**
     * Hash password để verify (không dùng cho encryption, chỉ để check đúng/sai)
     * Lưu dạng: Base64(salt) + ":" + Base64(derivedKey)
     */
    fun hashPasswordForVerification(password: String): String {
        val salt = generateSalt()
        val key = deriveKeyFromPassword(password, salt)
        val saltB64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
        val keyB64 = android.util.Base64.encodeToString(key.encoded, android.util.Base64.NO_WRAP)
        return "$saltB64:$keyB64"
    }

    /**
     * Kiểm tra password có khớp với stored hash không
     */
    fun verifyPassword(password: String, storedHash: String): Boolean {
        return try {
            val parts = storedHash.split(":")
            if (parts.size != 2) return false
            val salt = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP)
            val storedKeyBytes = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
            val derivedKey = deriveKeyFromPassword(password, salt)
            // Constant time comparison để chống timing attack
            storedKeyBytes.size == derivedKey.encoded.size &&
                    storedKeyBytes.zip(derivedKey.encoded.toList()).all { (a, b) -> a == b }
        } catch (e: Exception) {
            false
        }
    }

    // ─── Android Keystore (cho Biometric) ────────────────────────────────────

    /**
     * Tạo key trong Android Keystore để dùng với Biometric
     */
    fun generateBiometricKey() {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        if (!keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                BIOMETRIC_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    /**
     * Lấy Cipher đã init để truyền vào BiometricPrompt
     */
    fun getBiometricCipher(): Cipher {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        val key = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher
    }

    /**
     * Dùng Cipher từ BiometricPrompt result để encrypt Master Password
     */
    fun encryptWithBiometricCipher(data: ByteArray, cipher: Cipher): ByteArray {
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        return iv + ciphertext
    }

    fun decryptWithBiometricKey(encryptedData: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        val key = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as SecretKey
        val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    // ─── Password Strength ──────────────────────────────────────────────────

    enum class PasswordStrength { WEAK, FAIR, GOOD, STRONG }

    fun checkPasswordStrength(password: String): PasswordStrength {
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return when {
            score <= 2 -> PasswordStrength.WEAK
            score <= 3 -> PasswordStrength.FAIR
            score <= 4 -> PasswordStrength.GOOD
            else -> PasswordStrength.STRONG
        }
    }

    /**
     * Tạo password mạnh ngẫu nhiên
     */
    fun generateStrongPassword(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeLowercase: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        val chars = buildString {
            if (includeUppercase) append("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            if (includeLowercase) append("abcdefghijklmnopqrstuvwxyz")
            if (includeDigits) append("0123456789")
            if (includeSymbols) append("!@#\$%^&*()_+-=[]{}|;':\",./<>?")
        }
        val random = SecureRandom()
        return (1..length)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }
}
package com.aemeath.app.ui.lansync.server

import android.util.Base64
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xử lý ECDH key exchange cho LAN Sync.
 *
 * Flow:
 * 1. Phone (Server) sinh ECDH key pair → public key gửi lên web
 * 2. Laptop (Browser) nhận public key phone → sinh key pair của nó → gửi lại public key laptop
 * 3. Phone nhận public key laptop → ECDH compute shared secret
 * 4. verificationCode = SHA-256(sharedSecret).take(6 digits)
 * 5. User so sánh 6 số trên 2 màn hình → xác nhận
 * 6. AES session key = SHA-256(sharedSecret + "AEMEATH_SESSION")
 */
@Singleton
class LanSyncCrypto @Inject constructor() {

    private var localKeyPair: KeyPair? = null
    private var sessionKey: SecretKey? = null
    private var verificationCode: String? = null

    // ─── ECDH Key Pair ────────────────────────────────────────────────────────

    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        val kp = kpg.generateKeyPair()
        localKeyPair = kp
        return kp
    }

    fun getLocalPublicKeyBase64(): String {
        val kp = localKeyPair ?: generateKeyPair()
        return Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
    }

    // ─── ECDH Compute ─────────────────────────────────────────────────────────

    /**
     * Nhận public key của đối tác (Base64), tính shared secret,
     * tạo verification code + session key.
     */
    fun computeSharedSecret(partnerPublicKeyBase64: String): String {
        val kp = localKeyPair ?: throw IllegalStateException("Key pair chưa được sinh")

        val partnerKeyBytes = Base64.decode(partnerPublicKeyBase64, Base64.NO_WRAP)
        val partnerPublicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(partnerKeyBytes))

        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(kp.private)
        ka.doPhase(partnerPublicKey, true)
        val sharedSecret = ka.generateSecret()

        // Verification code: 6 chữ số từ SHA-256 của shared secret
        val digest = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
        val code = digest.take(4)
            .fold(0L) { acc, b -> acc * 256 + (b.toLong() and 0xFF) }
            .let { Math.abs(it) % 1_000_000 }
            .toString()
            .padStart(6, '0')
        verificationCode = code

        // Session key: SHA-256(sharedSecret + salt)
        val sessionKeyBytes = MessageDigest.getInstance("SHA-256")
            .apply {
                update(sharedSecret)
                update("AEMEATH_SESSION_KEY".toByteArray(Charsets.UTF_8))
            }.digest()
        sessionKey = SecretKeySpec(sessionKeyBytes, "AES")

        return code
    }

    fun getVerificationCode(): String? = verificationCode
    fun getSessionKey(): SecretKey? = sessionKey

    // ─── Encrypt/Decrypt với session key ─────────────────────────────────────

    /**
     * Mã hoá payload JSON bằng AES-256-GCM với session key.
     * Output: Base64(IV[12] + ciphertext + tag[16])
     */
    fun encryptPayload(data: ByteArray): String {
        val key = sessionKey ?: throw IllegalStateException("Session key chưa sẵn sàng")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, spec)
        val ciphertext = cipher.doFinal(data)
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptPayload(base64Data: String): ByteArray {
        val key = sessionKey ?: throw IllegalStateException("Session key chưa sẵn sàng")
        val combined = Base64.decode(base64Data, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(ciphertext)
    }

    // Tính SHA-256 hash của payload để verify toàn vẹn
    fun computeHash(data: ByteArray): String {
        return Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(data),
            Base64.NO_WRAP
        )
    }

    fun reset() {
        localKeyPair = null
        sessionKey = null
        verificationCode = null
    }
}
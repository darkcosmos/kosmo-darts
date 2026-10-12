package canary

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey

// Intentionally weak cryptography: CI canary for Semgrep. Do not use.
object WeakCrypto {
    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun encryptEcb(key: SecretKey, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(data)
    }
}

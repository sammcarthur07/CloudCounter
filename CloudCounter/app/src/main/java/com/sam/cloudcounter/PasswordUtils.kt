package com.sam.cloudcounter

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Utility class for password hashing and validation
 */
object PasswordUtils {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 32

    data class PasswordValidation(
        val isValid: Boolean,
        val errors: List<String>
    )

    /**
     * Hashes a password using PBKDF2 with a random salt
     */
    fun hashPassword(password: String): String {
        val salt = generateSalt()
        val hash = pbkdf2(password, salt)
        return "${bytesToHex(salt)}:${bytesToHex(hash)}"
    }

    /**
     * Verifies a password against a stored hash
     */
    fun verifyPassword(password: String, storedHash: String): Boolean {
        return try {
            val parts = storedHash.split(":")
            if (parts.size != 2) return false

            val salt = hexToBytes(parts[0])
            val hash = hexToBytes(parts[1])
            val testHash = pbkdf2(password, salt)

            hash.contentEquals(testHash)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates password strength - SIMPLIFIED: Any password is valid
     */
    fun validatePasswordStrength(password: String): PasswordValidation {
        val errors = mutableListOf<String>()

        // REMOVED ALL VALIDATION RULES - any password is valid
        // Users can use any length, any characters, or no special requirements

        return PasswordValidation(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
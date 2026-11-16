package org.example.project

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TotpManager {
    private val rng = SecureRandom()
    private const val STEP_SECONDS = 30L
    private const val DIGITS = 6
    private const val WINDOW = 1

    fun generateSecretBase32(): String = base32Encode(ByteArray(20).also { rng.nextBytes(it) })

    fun buildOtpauthUri(secretBase32: String, label: String = "HomeBox"): String =
        "otpauth://totp/${label}?secret=${secretBase32}&issuer=HomeBox&digits=${DIGITS}&period=${STEP_SECONDS}"

    /** Returns true if [code] is valid for [secretBase32] within ±1 step clock skew. */
    fun verify(secretBase32: String, code: String): Boolean {
        val trimmed = code.trim()
        if (trimmed.length != DIGITS || !trimmed.all { it.isDigit() }) return false
        val userCode = trimmed.toLong()
        val secret = base32Decode(secretBase32)
        val t = System.currentTimeMillis() / 1000 / STEP_SECONDS
        return (-WINDOW..WINDOW).any { step -> hotp(secret, t + step) == userCode }
    }

    private fun hotp(secret: ByteArray, counter: Long): Long {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = h.last().toInt() and 0x0F
        val code = ByteBuffer.wrap(h, offset, 4).int and 0x7FFFFFFF
        return code % 1_000_000L
    }

    private val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun base32Encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buf = 0; var bits = 0
        for (b in data) {
            buf = (buf shl 8) or (b.toInt() and 0xFF); bits += 8
            while (bits >= 5) { bits -= 5; sb.append(ALPHABET[(buf shr bits) and 0x1F]) }
        }
        if (bits > 0) sb.append(ALPHABET[(buf shl (5 - bits)) and 0x1F])
        return sb.toString()
    }

    private fun base32Decode(input: String): ByteArray {
        var buf = 0; var bits = 0
        val out = mutableListOf<Byte>()
        for (c in input.uppercase().trimEnd('=')) {
            val v = ALPHABET.indexOf(c); if (v < 0) continue
            buf = (buf shl 5) or v; bits += 5
            if (bits >= 8) { bits -= 8; out.add(((buf shr bits) and 0xFF).toByte()) }
        }
        return out.toByteArray()
    }
}

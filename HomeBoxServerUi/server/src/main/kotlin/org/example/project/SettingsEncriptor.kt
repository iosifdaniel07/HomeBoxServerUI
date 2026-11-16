package org.example.project

import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.serverData.MASKED_PASSWORD_SENTINEL
import org.example.project.serverData.ServerSettings
import org.example.project.serverData.ServerSettingsClient
import org.example.project.serverData.TotpDevice
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

object SettingsEncriptor {

    private val log = LoggerFactory.getLogger(SettingsEncriptor::class.java)

    // AES-256-GCM with a random 12-byte IV per encryption.
    // Stored as: "gcm1$" + base64(iv || ciphertext || gcmTag)
    // Values without the prefix are considered legacy (ECB) and discarded on read.
    private const val FORMAT_PREFIX = "gcm1$"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BYTES = 32

    private val secureRandom = SecureRandom()

    /** Lazily loaded symmetric key. Validated eagerly at boot by [assertKeyAvailable]. */
    private val keyBytes: ByteArray by lazy { loadKey() }

    private fun loadKey(): ByteArray {
        val hex = System.getenv(SETTINGS_ENC_KEY_HEX)
            ?: error("Missing env var: $SETTINGS_ENC_KEY_HEX (expect 64 hex chars)")
        val raw = runCatching { hexToBytes(hex) }
            .getOrElse { error("$SETTINGS_ENC_KEY_HEX is not valid hex") }
        require(raw.size == KEY_BYTES) {
            "$SETTINGS_ENC_KEY_HEX must decode to $KEY_BYTES bytes, got ${raw.size}"
        }
        return raw
    }

    /** Called from Application.main() to fail fast if the key is missing/invalid. */
    fun assertKeyAvailable() {
        loadKey()
    }

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(TAG_BITS, iv),
        )
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(iv.size + ct.size).also {
            System.arraycopy(iv, 0, it, 0, iv.size)
            System.arraycopy(ct, 0, it, iv.size, ct.size)
        }
        return FORMAT_PREFIX + Base64.getEncoder().encodeToString(packed)
    }

    /**
     * Returns the decrypted text, or `""` if the stored value is legacy (ECB)
     * or otherwise unreadable. Never throws out of here — callers get empty
     * strings so startup can continue and the operator can re-enter credentials.
     */
    fun decrypt(encryptedText: String): String {
        if (!encryptedText.startsWith(FORMAT_PREFIX)) {
            log.warn("settings value in legacy format detected; discarding. Re-enter credentials.")
            return ""
        }
        return try {
            val packed = Base64.getDecoder().decode(encryptedText.removePrefix(FORMAT_PREFIX))
            require(packed.size > IV_BYTES) { "ciphertext too short" }
            val iv = packed.copyOfRange(0, IV_BYTES)
            val ct = packed.copyOfRange(IV_BYTES, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            log.warn("failed to decrypt settings value: {}", e.javaClass.simpleName)
            ""
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "hex length must be even" }
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(clean[2 * i], 16) shl 4) +
                    Character.digit(clean[2 * i + 1], 16)).toByte()
        }
        return out
    }

    /** Resolve the settings file path. Operator-controlled via `HOMEBOX_SETTINGS_FILE` env. */
    private fun settingsFilePath(): Path {
        System.getenv(HOMEBOX_SETTINGS_FILE)?.takeIf { it.isNotBlank() }?.let {
            return Paths.get(it).toAbsolutePath().normalize()
        }
        val home = System.getProperty("user.home")
        return Paths.get(home, SERVER_SETTINGS_FILE)
    }

    /** Best-effort chmod 600 on POSIX systems. Silently skipped on Windows/non-POSIX FS. */
    private fun chmod600(path: Path) {
        try {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        } catch (_: UnsupportedOperationException) {
            // non-POSIX filesystem — ignore
        } catch (e: Exception) {
            log.warn("could not chmod 600 {}: {}", path, e.javaClass.simpleName)
        }
    }

    /**
     * Creates the settings file with empty credentials if it does not exist yet.
     * `downloadFolder` is intentionally left blank here — the operator provides it
     * through the `HOMEBOX_DOWNLOAD_FOLDER` environment variable at boot (see Application.kt).
     */
    fun createServerSettingsIfNotExist() {
        val filePath = settingsFilePath()
        val defaultSettings = ServerSettings("", "", "", "", "")
        if (!Files.exists(filePath)) {
            Files.createDirectories(filePath.parent)
            Files.createFile(filePath)
            val jsonString = Json.encodeToString(defaultSettings)

            try {
                Files.write(
                    filePath,
                    jsonString.toByteArray(),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
                )
                chmod600(filePath)
                log.info("initial settings file created at {}", filePath)
            } catch (e: IOException) {
                log.error("failed to create initial settings file: {}", e.message)
            }
        } else {
            chmod600(filePath)
        }
    }

    /**
     * Settings view exposed to the web UI.
     *
     * - Does not include `downloadFolder` (operator-only).
     * - Never returns decrypted passwords. If a password is set on disk,
     *   the client sees [MASKED_PASSWORD_SENTINEL] — a sentinel, not the real value.
     *   On save, the client sends the same sentinel back to mean
     *   "leave it alone" (see [saveClientSettings]).
     */
    fun readClientSettings(): ServerSettingsClient {
        val s = readSettingsFromFile()
        return ServerSettingsClient(
            filelistUsername = s.filelistUsername,
            filelistPassword = if (s.filelistPassword.isNotEmpty()) MASKED_PASSWORD_SENTINEL else "",
            qbUsername = s.qbUsername,
            qbPassword = if (s.qbPassword.isNotEmpty()) MASKED_PASSWORD_SENTINEL else "",
            totpDeviceNames = s.totpDevices.map { it.name },
        )
    }

    /**
     * Save settings sent by the UI, preserving the operator-controlled
     * `downloadFolder` already on disk. UI can never override it.
     *
     * Password fields:
     * - empty string  → clear the stored password.
     * - [MASKED_PASSWORD_SENTINEL]  → keep the existing password (UI didn't touch it).
     * - anything else → set as new password.
     */
    fun saveClientSettings(incoming: ServerSettingsClient): Boolean {
        val existing = readSettingsFromFile()
        val merged = existing.copy(
            filelistUsername = incoming.filelistUsername,
            filelistPassword = resolvePasswordField(incoming.filelistPassword, existing.filelistPassword),
            qbUsername = incoming.qbUsername,
            qbPassword = resolvePasswordField(incoming.qbPassword, existing.qbPassword),
        )
        return saveSettingsToFile(merged)
    }

    private fun resolvePasswordField(incoming: String, existing: String): String = when (incoming) {
        MASKED_PASSWORD_SENTINEL -> existing
        else -> incoming
    }

    fun readSettingsFromFile(): ServerSettings {
        return try {
            val filePath = settingsFilePath()
            val jsonString = Files.readString(filePath)
            val decoded = Json.decodeFromString<ServerSettings>(jsonString)
            val decryptedDevices = decoded.totpDevices.map { d ->
                TotpDevice(d.name, if (d.secret.isNotEmpty()) decrypt(d.secret) else "")
            }
            val legacySecret = decoded.totpSecret
            val migratedDevices = if (decryptedDevices.isEmpty() && !legacySecret.isNullOrEmpty()) {
                val migratedSecret = decrypt(legacySecret)
                if (migratedSecret.isNotEmpty()) listOf(TotpDevice("Device 1", migratedSecret)) else emptyList()
            } else {
                decryptedDevices
            }
            decoded.copy(
                filelistPassword = if (decoded.filelistPassword.isNotEmpty()) decrypt(decoded.filelistPassword) else "",
                qbPassword = if (decoded.qbPassword.isNotEmpty()) decrypt(decoded.qbPassword) else "",
                totpSecret = null,
                totpDevices = migratedDevices,
            )
        } catch (e: Exception) {
            log.error("failed to read settings: {}", e.message)
            ServerSettings("", "", "", "", "")
        }
    }

    /**
     * If a `settings-seed.json` file exists next to `server_settings.json`,
     * reads it, merges plaintext credentials into any empty fields, saves
     * the result encrypted, then deletes the seed file. Called once at startup.
     */
    fun consumeSeedFile() {
        val seedPath = settingsFilePath().parent.resolve("settings-seed.json")
        if (!Files.exists(seedPath)) return
        try {
            @Serializable
            data class Seed(
                val qbUsername: String = "",
                val qbPassword: String = "",
                val filelistUsername: String = "",
                val filelistPassword: String = "",
            )
            val seed = Json.decodeFromString<Seed>(Files.readString(seedPath))
            val existing = readSettingsFromFile()
            val merged = existing.copy(
                qbUsername      = existing.qbUsername.ifEmpty { seed.qbUsername },
                qbPassword      = existing.qbPassword.ifEmpty { seed.qbPassword },
                filelistUsername = existing.filelistUsername.ifEmpty { seed.filelistUsername },
                filelistPassword = existing.filelistPassword.ifEmpty { seed.filelistPassword },
            )
            if (merged != existing) {
                saveSettingsToFile(merged)
                log.info("initial credentials seeded from settings-seed.json")
            }
        } catch (e: Exception) {
            log.warn("failed to consume settings-seed.json: {}", e.message)
        } finally {
            runCatching { Files.delete(seedPath) }
        }
    }

    fun saveSettingsToFile(settings: ServerSettings): Boolean {
        try {
            val jsonString = Json.encodeToString(
                settings.copy(
                    qbPassword = encrypt(settings.qbPassword),
                    filelistPassword = encrypt(settings.filelistPassword),
                    totpSecret = null,
                    totpDevices = settings.totpDevices.map { d ->
                        TotpDevice(d.name, if (d.secret.isNotEmpty()) encrypt(d.secret) else "")
                    },
                )
            )

            val filePath = settingsFilePath()
            Files.write(
                filePath,
                jsonString.toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
            chmod600(filePath)
            log.info("settings saved")
            return true
        } catch (e: Exception) {
            log.error("failed to save settings: {}", e.message)
            return false
        }
    }
}

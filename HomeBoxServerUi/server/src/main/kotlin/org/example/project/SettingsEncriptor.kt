package org.example.project

import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import org.example.project.serverData.ServerSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

object SettingsEncriptor {

    private fun deriveKeyFromArgon2Hash(argon2Hash: ByteArray): ByteArray {
        return argon2Hash.copyOf(32)  // Truncate or pad to 32 bytes for AES-256
    }

    fun encrypt(text: String): String {
        // Generate AES key (16 bytes for AES-128)
        val keyBytes = deriveKeyFromArgon2Hash(requireEnv(AdminPasswordArgon2.ADMIN_USERNAME).toByteArray())

        // Use AES in ECB mode
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)

        val encryptedBytes = cipher.doFinal(text.toByteArray())

        // Return the base64 encoded encrypted data
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }

    fun decrypt(encryptedText: String): String {
        // Decode the base64 encoded encrypted data
        val encryptedBytes = Base64.getDecoder().decode(encryptedText)

        // Generate AES key (16 bytes for AES-128)
        val keyBytes = deriveKeyFromArgon2Hash(requireEnv(AdminPasswordArgon2.ADMIN_USERNAME).toByteArray())

        // Use AES in ECB mode
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)

        val decryptedBytes = cipher.doFinal(encryptedBytes)

        // Return the decrypted text as a string
        return String(decryptedBytes)
    }

    fun createServerSettingsIfNotExist() {
        val home = System.getProperty("user.home")
        val filePath: Path = Paths.get(home, SERVER_SETTINGS_FILE)
        val downloadPath: Path = Paths.get(home, DEFAULT_DOWNLOAD_FOLDER)
        val defaultSettings = ServerSettings(downloadPath.toString(), "", "", "", "")
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
                println("Settings saved successfully.")
            } catch (e: IOException) {
                println("Error while saving settings: ${e.message}")
            }
        }
    }

    fun readSettingsFromFile(): ServerSettings {
        return try {
            val home = System.getProperty("user.home")
            val filePath: Path = Paths.get(home, SERVER_SETTINGS_FILE)
            // Read file contents into a String
            val jsonString = Files.readString(filePath)
            // Deserialize the JSON string into a ServerSettings object
            val decoded = Json.decodeFromString<ServerSettings>(jsonString)
            decoded.copy(
                filelistPassword =
                    if (decoded.filelistPassword.isNotEmpty())
                        decrypt(decoded.filelistPassword)
                    else
                        "",
                qbPassword =
                    if (decoded.qbPassword.isNotEmpty())
                        decrypt(decoded.qbPassword)
                    else
                        ""
            )
        } catch (e: Exception) {
            println("Error reading settings: ${e.message}")
            ServerSettings("", "", "", "", "")
        }
    }

    fun saveSettingsToFile(settings: ServerSettings): Boolean {
        try {
            // Serialize the ServerSettings object into a JSON string
            val qbP = settings.qbPassword
            val filP = settings.filelistPassword
            val jsonString = Json.encodeToString(
                settings.copy(
                    qbPassword = encrypt(qbP).toString(),
                    filelistPassword = encrypt(filP).toString()
                )
            )

            // Write the JSON string to the file
            val home = System.getProperty("user.home")
            val filePath: Path = Paths.get(home, SERVER_SETTINGS_FILE)
            Files.write(
                filePath,
                jsonString.toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
            FileManager.configureExistingBase(settings.downloadFolder)
            println("Settings saved successfully.")
            return true
        } catch (e: Exception) {
            println("Error saving settings: ${e.message}")
           return false
        }
    }
}

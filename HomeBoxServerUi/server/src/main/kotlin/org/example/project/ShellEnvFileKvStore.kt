package org.example.project

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission

class ShellEnvFileKvStore(private val envFile: Path) {

    fun ensureDirAndUmaskLikePermissions() {
        Files.createDirectories(envFile.parent)
        // "umask 077" echivalent practic: fișier 600 (owner read/write)
        chmod600IfPossible()
    }

    fun get(key: String): String? {
        if (!Files.exists(envFile)) return null
        val prefix = "$key='"
        val lines = Files.readAllLines(envFile)

        // ca in script: ultimele apariții câștigă (tail -n 1)
        for (i in lines.size - 1 downTo 0) {
            val line = lines[i]
            if (line.startsWith(prefix) && line.endsWith("'")) {
                val inside = line.substring(prefix.length, line.length - 1)
                return unescapeSingleQuotes(inside)
            }
        }
        return null
    }

    fun set(key: String, value: String) {
        val escaped = escapeSingleQuotes(value)
        val newLine = "$key='$escaped'"

        val lines = if (Files.exists(envFile)) Files.readAllLines(envFile).toMutableList() else mutableListOf()

        val idx = lines.indexOfLast { it.startsWith("$key=") }
        if (idx >= 0) lines[idx] = newLine else lines.add(newLine)

        Files.writeString(
            envFile,
            lines.joinToString("\n", postfix = "\n"),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )

        chmod600IfPossible()
    }

    private fun chmod600IfPossible() {
        try {
            val perms = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            if (Files.exists(envFile)) Files.setPosixFilePermissions(envFile, perms)
        } catch (_: Exception) {
            // Windows / FS fără POSIX: ignoră
        }
    }

    // exact trucul din bash: ' -> '"'"'
    private fun escapeSingleQuotes(s: String): String = s.replace("'", "'\"'\"'")
    private fun unescapeSingleQuotes(s: String): String = s.replace("'\"'\"'", "'")
}

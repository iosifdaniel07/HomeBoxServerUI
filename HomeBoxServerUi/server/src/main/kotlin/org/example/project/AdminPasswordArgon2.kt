package org.example.project
import de.mkammerer.argon2.Argon2Factory
import java.io.Console
interface KvStore {
    fun get(key: String): String?
    fun set(key: String, value: String)
}

object AdminPasswordArgon2 {
    const val KEY = "ADMIN_PASSWORD_ARGON2ID"
    const val ADMIN_USERNAME = "ADMIN_USERNAME"

    // echivalent cu: -t 3 -m 16 -p 1
    private const val ITERATIONS = 3
    private const val MEMORY_KIB = 1 shl 16   // 65536 KiB (64 MiB)
    private const val PARALLELISM = 1

    // scriptul tău generează un salt random care ajunge tipic ~22 chars în hash-ul PHC;
    // în argon2-jvm setezi direct lungimea salt-ului în bytes.
    private const val SALT_LEN_BYTES = 16
    private const val HASH_LEN_BYTES = 32

    /**
     * Dacă nu există ADMIN_PASSWORD_ARGON2ID, îl setează.
     * Returnează hash-ul stocat (existent sau nou generat).
     */
    fun ensureAdminPassword(kv: KvStore, console: Console = System.console() ?: error(
        "Nu am Console (System.console() == null). Rulează dintr-un terminal real, nu din IDE."
    )): String {
        val existing = kv.get(KEY)
        if (!existing.isNullOrBlank()) return existing.trim()

        console.writer().println("Set admin password (will be stored as Argon2id hash):")
        val p1 = console.readPassword("Password: ") ?: error("Nu pot citi parola.")
        val p2 = console.readPassword("Repeat: ") ?: error("Nu pot citi parola.")

        if (!p1.contentEquals(p2)) {
            wipe(p1); wipe(p2)
            error("Passwords do not match.")
        }
        wipe(p2)

        val argon2 = Argon2Factory.create(
            Argon2Factory.Argon2Types.ARGON2id,
            SALT_LEN_BYTES,
            HASH_LEN_BYTES
        )

        val hash = try {
            // Rezultatul este encoded PHC: $argon2id$v=19$m=...,t=...,p=...$salt$hash
            argon2.hash(ITERATIONS, MEMORY_KIB, PARALLELISM, p1)
        } finally {
            argon2.wipeArray(p1)
        }

        kv.set(KEY, hash)
        console.writer().println("Generated $KEY $hash")
        return hash
    }

    /**
     * Verifică parola introdusă cu hash-ul stocat.
     * IMPORTANT: verify folosește parametrii din hash-ul encoded, deci rămâi compatibil.
     */
    fun verifyPassword(storedEncodedHash: String, password: CharArray): Boolean {
        // poți crea orice instanță, verify citește setările din hash-ul encoded
        val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
        return try {
            argon2.verify(storedEncodedHash.trim(), password)
        } finally {
            argon2.wipeArray(password)
        }
    }

    private fun wipe(chars: CharArray) {
        java.util.Arrays.fill(chars, '\u0000')
    }
}
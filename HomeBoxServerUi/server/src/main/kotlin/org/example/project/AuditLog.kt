package org.example.project

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Append-only JSON-lines audit log for admin actions.
 *
 * Why separate from the normal application log:
 *  - Audit lines must survive even if someone sets the app log level to WARN.
 *  - Format is stable (1 JSON per line) so SIEMs can tail it.
 *  - File permissions are 0600 — never world-readable.
 *
 * Path is resolved from env `HOMEBOX_AUDIT_LOG`, defaulting to
 * `$user.home/homebox_audit.log`. If writing fails (disk full, permissions),
 * we fall back to the standard SLF4J logger — admin actions are still
 * recorded, just not in the dedicated audit file.
 */
object AuditLog {

    private val log = LoggerFactory.getLogger(AuditLog::class.java)
    private val lock = ReentrantLock()
    private val json = Json { encodeDefaults = true }

    @kotlinx.serialization.Serializable
    private data class Entry(
        val ts: String,
        val who: String?,
        val ip: String?,
        val action: String,
        val target: String? = null,
        val result: String = "ok",
    )

    private val path: Path by lazy {
        val fromEnv = System.getenv("HOMEBOX_AUDIT_LOG")?.takeIf { it.isNotBlank() }
        val p = if (fromEnv != null) Paths.get(fromEnv) else {
            Paths.get(System.getProperty("user.home"), "homebox_audit.log")
        }
        try {
            Files.createDirectories(p.parent)
        } catch (_: Exception) {
            // best effort — if parent exists, this is a no-op
        }
        p
    }

    fun record(
        who: String?,
        ip: String?,
        action: String,
        target: String? = null,
        result: String = "ok",
    ) {
        val entry = Entry(
            ts = Instant.now().toString(),
            who = who,
            ip = ip,
            action = action,
            target = target,
            result = result,
        )
        val line = json.encodeToString(entry) + "\n"
        try {
            lock.withLock {
                Files.write(
                    path,
                    line.toByteArray(Charsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                )
                chmod600(path)
            }
        } catch (e: Exception) {
            log.warn("audit log write failed, falling back to app log: {}", e.javaClass.simpleName)
            log.info("AUDIT {}", line.trim())
        }
    }

    private fun chmod600(p: Path) {
        try {
            Files.setPosixFilePermissions(
                p,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        } catch (_: UnsupportedOperationException) {
            // non-POSIX filesystem
        } catch (_: Exception) {
            // best-effort; already logged by caller if the write itself fails
        }
    }
}

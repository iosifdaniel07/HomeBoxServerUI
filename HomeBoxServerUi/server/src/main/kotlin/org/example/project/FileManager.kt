package org.example.project

import kotlinx.io.files.FileNotFoundException
import org.example.project.serverData.DirListing
import org.example.project.serverData.FileEntry
import org.example.project.serverData.OpResult
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.*

object FileManager {
    // Not set until you configure it. Must point to an existing, writable directory.
    private val BASE_DIR_REF = AtomicReference<Path?>(null)

    /**
     * Configure the base directory to an **existing** directory on the device.
     * - Must be absolute or will be resolved to absolute.
     * - Must exist, be a directory, and be writable.
     * - No directories are created here.
     */
    /**
     * Configure the base directory.
     *
     * Security: this function does NOT create directories. The base must
     * already exist on disk and be writable. The path is provided by the
     * operator via env (HOMEBOX_DOWNLOAD_FOLDER) at boot, never by a web client.
     */
    fun configureExistingBase(absOrRelPath: String): OpResult {
        return try {
            require(absOrRelPath.isNotBlank()) { "Base path must not be blank" }
            val raw = Paths.get(absOrRelPath).toAbsolutePath().normalize()

            if (!raw.exists()) {
                return OpResult(false, "Base path does not exist: $raw (create it manually and restart)")
            }
            // Resolve symlinks at configuration time so every path we compare
            // against base later is in the same canonical namespace.
            val p = raw.toRealPath()
            require(p.isDirectory()) { "Base path is not a directory: $p" }
            require(Files.isWritable(p)) { "Base path is not writable: $p" }
            require(!p.toString().isEmpty() && p.nameCount > 0) {
                "Refusing to use filesystem root as base"
            }

            BASE_DIR_REF.set(p)
            OpResult(true, "Base configured: $p")
        } catch (e: Exception) {
            OpResult(false, "Base configuration failed: ${e.message}")
        }
    }

    /** Read the configured base dir or throw if not set. */
    fun baseDir(): Path {
        return BASE_DIR_REF.get()
            ?: throw IllegalStateException("FileManager base not configured. Call configureExistingBase(path) first.")
    }

    /** Resolve a user-supplied relative path safely inside BASE_DIR. */
    private fun resolveSafe(rel: String?): Path {
        val base = baseDir()
        val safeRel = (rel ?: "").trim().ifEmpty { "." }
        validateRelativePath(safeRel)
        val target = base.resolve(safeRel).toAbsolutePath().normalize()
        if (!target.startsWith(base)) throw AccessDeniedException("Path escapes base directory")
        return target
    }

    /**
     * Reject obviously-dangerous names before we even hit the filesystem:
     * absolute paths, parent-directory traversal, NUL bytes, overlong segments.
     * The `.` escape is still caught by the `startsWith(base)` check in
     * [resolveSafe] — this is just defence in depth.
     */
    private fun validateRelativePath(rel: String) {
        require(rel.none { it.code < 0x20 || it.code == 0x7F }) {
            "Path contains control characters"
        }
        require(rel.length <= 4096) { "Path too long" }
        val normalized = rel.replace('\\', '/')
        require(!normalized.startsWith("/")) { "Absolute paths not allowed" }
        val segments = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        for (seg in segments) {
            require(seg != "..") { "Parent traversal not allowed" }
            require(seg.length <= 255) { "Path segment too long" }
        }
    }

    /** Create a directory (and parents) under base. Base itself must already exist. */
    fun mkdirs(relDir: String): OpResult {
        return try {
            val p = resolveSafe(relDir)
            Files.createDirectories(p)
            OpResult(true, "Created: ${baseDir().relativize(p)}")
        } catch (e: Exception) {
            OpResult(false, "Create failed: ${e.message}")
        }
    }

    /** List a directory. Hidden files are skipped by default. */
    fun list(
        relDir: String = ".",
        includeHidden: Boolean = false,
        followSymlinks: Boolean = false
    ): DirListing {
        val base = baseDir()
        val dir = resolveSafe(relDir)
        if (!dir.exists()) throw NoSuchFileException("Not found: $relDir")
        if (!dir.isDirectory()) throw NotDirectoryException(dir.toString())

        val entries = listOneLevel(
            base = base,
            dir = dir,
            includeHidden = includeHidden,
            followSymlinks = followSymlinks
        ).sortedWith(entryComparator())

        return DirListing(
            // Don't leak the server-side absolute path to the client.
            base = ".",
            at = base.relativize(dir).toString().ifEmpty { "." },
            entries = entries
        )
    }

    /** Hard cap on recursion depth to prevent DoS via deep trees or symlink loops. */
    private const val MAX_LIST_DEPTH = 10

    /** List immediate children of a directory, filtering hidden if needed. */
    private fun listOneLevel(
        base: Path,
        dir: Path,
        includeHidden: Boolean,
        followSymlinks: Boolean,
        depth: Int = 0,
    ): List<FileEntry> {
        if (depth >= MAX_LIST_DEPTH) return emptyList()
        Files.list(dir).use { stream ->
            return stream
                .filter { path ->
                    if (includeHidden) true
                    else !(path.fileName?.toString()?.startsWith(".") ?: false)
                }
                .filter { path ->
                    // Defence in depth: refuse anything that resolves outside base.
                    path.toAbsolutePath().normalize().startsWith(base)
                }
                .map { p -> buildEntry(base, p, includeHidden, followSymlinks, depth + 1) }
                .toList()
        }
    }

    /** Build a FileEntry for a path (recursively if directory). */
    private fun buildEntry(
        base: Path,
        p: Path,
        includeHidden: Boolean,
        followSymlinks: Boolean,
        depth: Int = 0,
    ): FileEntry {
        if (!Files.exists(p)) {
            throw IllegalArgumentException("Path does not exist: $p")
        }

        val name = p.fileName?.toString() ?: p.toString()

        val attrs: BasicFileAttributes
        try {
            // Try reading attributes while considering symlinks
            attrs = if (followSymlinks) {
                Files.readAttributes(p, BasicFileAttributes::class.java)
            } else {
                Files.readAttributes(p, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            }
        } catch (e: FileNotFoundException) {
            throw IllegalStateException("File not found: $p", e)
        } catch (e: InvalidPathException) {
            throw IllegalStateException("Invalid path: $p", e)
        } catch (e: Exception) {
            // Log the specific error for better debugging
            throw IllegalStateException("Failed to read attributes for path: $p", e)
        }

        val isDir = attrs.isDirectory
        val isSymlink = attrs.isSymbolicLink

        // Handle symlinks and directories carefully
        val children: List<FileEntry> =
            if (isDir && (!isSymlink || followSymlinks)) {
                listOneLevel(base, p, includeHidden, followSymlinks, depth)
                    .sortedWith(entryComparator())
            } else emptyList()

        val sizeBytes: Long =
            if (children.isEmpty()) {
                // File or empty directory: for regular file use actual size; for dir with no children it's 0
                attrs.size()
            } else {
                // Directory with children: sum of descendant file sizes
                children.sumOf { it.sizeBytes }
            }

        return FileEntry(
            name = name,
            pathRel = base.relativize(p).toString(),
            isDir = isDir,
            sizeBytes = sizeBytes,
            modifiedEpochMs = attrs.lastModifiedTime().toMillis(),
            readable = Files.isReadable(p),
            writable = Files.isWritable(p),
            children = children
        )
    }

    private fun entryComparator(): Comparator<FileEntry> =
        compareBy<FileEntry>({ if (it.isDir) 0 else 1 }, { it.name.lowercase() })

    fun deleteFile(
        fileName: String,
        recursive: Boolean = true
    ): OpResult {
        return try {
            val base = baseDir()
            val target = resolveSafe(fileName)

            if (!target.exists()) return OpResult(false, "Not found: $fileName")
            if (target == base) return OpResult(false, "Refusing to delete base directory")

            if (target.isDirectory()) {
                if (!recursive) return OpResult(
                    false,
                    "Refusing to delete directory without recursive=true"
                )
                // Files.walk defaults to NOT following symlinks; filter each entry
                // to stay inside base as defence in depth.
                Files.walk(target).use { stream ->
                    stream.sorted(Comparator.reverseOrder())
                        .filter { it.toAbsolutePath().normalize().startsWith(base) }
                        .forEach { Files.deleteIfExists(it) }
                }
                OpResult(true, "Directory deleted: ${base.relativize(target)}")
            } else {
                Files.deleteIfExists(target)
                OpResult(true, "File deleted: ${base.relativize(target)}")
            }
        } catch (e: Exception) {
            OpResult(false, "Delete failed: ${e.message}")
        }
    }

}

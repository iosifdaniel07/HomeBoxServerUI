package org.example.project

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
    fun configureExistingBase(absOrRelPath: String): OpResult {
        return try {
            val p = Paths.get(absOrRelPath).toAbsolutePath().normalize()

            require(p.exists()) { "Base path does not exist: $p" }
            require(p.isDirectory()) { "Base path is not a directory: $p" }
            require(Files.isWritable(p)) { "Base path is not writable: $p" }

            BASE_DIR_REF.set(p)
            OpResult(true, "Base configured: $p")
        } catch (e: Exception) {
            OpResult(false, "Base configuration failed: ${e.message}")
        }
    }

    /** Read the configured base dir or throw if not set. */
    private fun baseDir(): Path {
        return BASE_DIR_REF.get()
            ?: throw IllegalStateException("FileManager base not configured. Call configureExistingBase(path) first.")
    }

    /** Resolve a user-supplied relative path safely inside BASE_DIR. */
    private fun resolveSafe(rel: String?): Path {
        val base = baseDir()
        val safeRel = (rel ?: "").trim().ifEmpty { "." }
        val target = base.resolve(safeRel).toAbsolutePath().normalize()
        if (!target.startsWith(base)) throw AccessDeniedException("Path escapes base directory")
        return target
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
        includeHidden: Boolean = false
    ): DirListing {
        val base = baseDir()
        val dir = resolveSafe(relDir)
        if (!dir.exists()) throw NoSuchFileException("Not found: $relDir")
        if (!dir.isDirectory()) throw NotDirectoryException(dir.toString())

        val entries = Files.list(dir).use { stream ->
            stream
                .filter { includeHidden || !it.name.startsWith(".") }
                .sorted()
                .map { p ->
                    val attrs = Files.readAttributes(p, BasicFileAttributes::class.java)
                    val isDir = attrs.isDirectory
                    val size = attrs.size()
                    FileEntry(
                        name = p.name,
                        pathRel = base.relativize(p).toString(),
                        isDir = isDir,
                        sizeBytes = size,
                        modifiedEpochMs = attrs.lastModifiedTime().toMillis(),
                        readable = Files.isReadable(p),
                        writable = Files.isWritable(p),
                        emptyList()
                    )
                }
                .toList()
        }

        return DirListing(
            base = base.toString(),
            at = base.relativize(dir).toString().ifEmpty { "." },
            entries = entries
        )
    }

    fun deleteFile(
        fileName: String,  // Use fileName as the input parameter
        recursive: Boolean = true
    ): OpResult {
        return try {
            val base = baseDir()
            val target = resolveSafe(fileName)  // Use fileName here

            // Check if the target exists
            if (!target.exists()) return OpResult(false, "Not found: $fileName")

            // Handle directory deletion
            if (target.isDirectory()) {
                if (!recursive) return OpResult(
                    false,
                    "Refusing to delete directory without recursive=true"
                )
                // Recursively delete the directory using walk
                Files.walk(target).sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
                OpResult(true, "Directory deleted: ${base.relativize(target)}")
            } else {
                // Delete a regular file
                Files.deleteIfExists(target)
                OpResult(true, "File deleted: ${base.relativize(target)}")
            }
        } catch (e: Exception) {
            OpResult(false, "Delete failed: ${e.message}")
        }
    }

}

package org.example.project

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours

/**
 * In-memory session revocation registry.
 *
 * Sessions are stateless cookies, so "logout-all" needs a way to invalidate
 * previously-issued cookies without rotating the encryption key. We track
 * active session IDs; revocation moves them to the revoked set, and
 * [isRevoked] blocks them from authenticating.
 *
 * Capacity is capped to prevent unbounded memory growth if an attacker
 * manages to cycle many valid logins. Old entries are evicted FIFO after
 * the cookie's expected lifetime (slightly longer than the cookie maxAge).
 */
object SessionRegistry {
    private const val MAX_ENTRIES = 10_000
    private val sessionLifetimeMs = 3.hours.inWholeMilliseconds  // cookie maxAge is 2h

    private data class Entry(val id: String, val createdAtMs: Long)

    private val active: MutableMap<String, Long> = ConcurrentHashMap()
    private val revoked: MutableMap<String, Long> = ConcurrentHashMap()

    fun register(sessionId: String) {
        pruneExpired()
        if (active.size >= MAX_ENTRIES) {
            // Drop oldest entry as a safety valve.
            active.entries.minByOrNull { it.value }?.key?.let { active.remove(it) }
        }
        active[sessionId] = System.currentTimeMillis()
    }

    fun revoke(sessionId: String) {
        active.remove(sessionId)
        revoked[sessionId] = System.currentTimeMillis()
    }

    fun revokeAll() {
        val now = System.currentTimeMillis()
        active.keys.forEach { revoked[it] = now }
        active.clear()
    }

    fun isRevoked(sessionId: String): Boolean {
        pruneExpired()
        return sessionId in revoked
    }

    private fun pruneExpired() {
        val cutoff = System.currentTimeMillis() - sessionLifetimeMs
        active.entries.removeIf { it.value < cutoff }
        revoked.entries.removeIf { it.value < cutoff }
    }
}

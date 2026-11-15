package org.example.project

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/**
 * Base URL of the backend API as seen from the current platform.
 * - Web (JS/Wasm): same-origin, e.g. `https://homestreambox.go.ro/api`
 * - JVM: localhost loopback for tests/dev tooling.
 */
expect fun apiBaseUrl(): String
package org.example.project

class JsPlatform: Platform {
    override val name: String = "Web with Kotlin/JS"
}

actual fun getPlatform(): Platform = JsPlatform()

actual fun apiBaseUrl(): String = "${kotlinx.browser.window.location.origin}/api"
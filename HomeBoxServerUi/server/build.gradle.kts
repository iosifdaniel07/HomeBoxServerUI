import org.gradle.api.tasks.Sync

tasks.named<Sync>("installDist") {
    from("setup-server-env.ssh") {
        into("bin")
        fileMode = Integer.parseInt("755", 8)
    }
    from("command_config") {
        into("bin")
        fileMode = Integer.parseInt("755", 8)
    }
    from("setup-caddy-local.ssh") {
        into("bin")
        fileMode = Integer.parseInt("755", 8)
    }
    // Deployment installer — see server/install.sh for what it does.
    from("install.sh") {
        into("bin")
        fileMode = Integer.parseInt("755", 8)
    }
}

// Also ship install.sh in the shadow (fat-jar) distribution — both the
// sync install and the tar/zip archives. Shadow plugin uses the standard
// Distribution contents for installShadowDist, and the archive tasks copy
// from the same spec, so we need to add to both to cover every path.
tasks.named<Sync>("installShadowDist") {
    from("install.sh") {
        into("bin")
        fileMode = Integer.parseInt("755", 8)
    }
}
tasks.named<Tar>("shadowDistTar") {
    from("install.sh") {
        into("server-shadow-${project.version}/bin")
        fileMode = Integer.parseInt("755", 8)
    }
}
tasks.named<Zip>("shadowDistZip") {
    from("install.sh") {
        into("server-shadow-${project.version}/bin")
        fileMode = Integer.parseInt("755", 8)
    }
}

// Convenience umbrella task. Usage:
//   ./gradlew :server:packageInstaller
// Output: server/build/distributions/server-shadow-<version>.tar
//         Unpack with `tar -xf` on the target, then `sudo ./bin/install.sh`.
tasks.register("packageInstaller") {
    group = "distribution"
    description = "Build a self-contained server tarball (fat JAR + install.sh)."
    dependsOn("shadowDistTar")
    // Capture these at configuration time so the task body is cache-safe.
    val tarProvider = layout.buildDirectory.file("distributions/server-shadow-${project.version}.tar")
    val versionCaptured = project.version.toString()
    doLast {
        val tar = tarProvider.get().asFile
        if (tar.exists()) {
            println("\n==> installer ready: ${tar.absolutePath}")
            println("==> deploy with:")
            println("      scp ${tar.name} user@server:/tmp/")
            println("      ssh user@server 'sudo tar -xf /tmp/${tar.name} -C /opt/ && sudo /opt/server-shadow-${versionCaptured}/bin/install.sh'")
        } else {
            println("tar not found at ${tar.absolutePath} — did shadowDistTar run?")
        }
    }
}

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

group = "org.example.project"
version = "1.0.0"
application {
    mainClass.set("org.example.project.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

tasks.test {
    useJUnit()
    // Inject the env vars the module() expects. Tests must not rely on the
    // developer's real environment. All of these are throwaway values —
    // never reuse them in production.
    environment("SESSION_ENCRYPT_KEY_HEX", "11".repeat(16))  // 16 bytes AES-128
    environment("SESSION_SIGN_KEY_HEX", "22".repeat(32))     // 32 bytes HMAC
    environment("SETTINGS_ENC_KEY_HEX", "33".repeat(32))     // 32 bytes AES-256-GCM
    environment("ADMIN_USERNAME", "admin")
    // Argon2id hash of literal "test-password", m=65536 KiB, t=3, p=1.
    // Regenerate with: argon2 inside the de.mkammerer:argon2-jvm:2.11 library.
    environment(
        "ADMIN_PASSWORD_ARGON2ID",
        "\$argon2id\$v=19\$m=65536,t=3,p=1\$PFTl3/7KnlmfMmE1rYyhiQ\$ge6sFvdRuBvmfW9ADSMit3pp2UR50WWbbQvuZKFVzSg"
    )
    environment("APP_ENV", "dev")
    environment("HOMEBOX_DOWNLOAD_FOLDER", layout.buildDirectory.dir("test-base").get().asFile.also { it.mkdirs() }.absolutePath)
    environment("HOMEBOX_SETTINGS_FILE", layout.buildDirectory.file("test-settings.json").get().asFile.absolutePath)
    environment("HOMEBOX_AUDIT_LOG", layout.buildDirectory.file("test-audit.log").get().asFile.absolutePath)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.common)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.serverCore.jvm)
    implementation(libs.ktor.serverNetty.jvm)
    implementation(libs.ktor.serverSessions)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.serverAuth)
    implementation(libs.ktor.serverRateLimit)
    implementation(libs.ktor.serverRequestValidation)
    implementation(libs.ktor.serverStatusPages)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.clientContentNegociacion)
    implementation(libs.ktot.serializationJson)
    // logback-classic is already declared via libs.logback (1.5.18 in libs.versions.toml);
    // duplicate pin removed to keep versions in sync with the version catalog.
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.3.0")
    implementation("de.mkammerer:argon2-jvm:2.11")
}
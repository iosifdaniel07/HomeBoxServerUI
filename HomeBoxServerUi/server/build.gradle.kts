import org.gradle.api.tasks.Sync

tasks.named<Sync>("installDist") {
    from("setup-server-env.ssh") {
        into("bin")
        // 0755
        fileMode = Integer.parseInt("755", 8)
    }
    from("command_config") {
        into("bin")
        // 0755
        fileMode = Integer.parseInt("755", 8)
    }
    from("setup-caddy-local.ssh") {
        into("bin")
        // 0755
        fileMode = Integer.parseInt("755", 8)
    }
}

plugins {
    alias(libs.plugins.kotlinJvm)
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
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.clientContentNegociacion)
    implementation(libs.ktot.serializationJson)
    implementation("ch.qos.logback:logback-classic:1.2.10")
    implementation("org.jsoup:jsoup:1.15.3")
    implementation("io.ktor:ktor-server-auth-jvm:3.3.0")
    implementation("io.ktor:ktor-server-sessions-jvm:3.3.0")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.3.0")
    implementation("de.mkammerer:argon2-jvm:2.11")
}
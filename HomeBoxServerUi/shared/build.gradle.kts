import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
}

kotlin {
    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.clientContentNegociacion)
            implementation(libs.ktor.clientCore)
            implementation(libs.ktot.serializationJson)
            implementation(libs.ktor.clientCio)
        }

        jsMain.dependencies {
            implementation(libs.ktor.clientJs)
            implementation(libs.ktor.client.serialization )
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.java)
            implementation(libs.ktor.client.serialization)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}


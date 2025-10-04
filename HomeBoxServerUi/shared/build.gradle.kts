import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    
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
            // Ktor client dependencies for HTTP requests
            implementation(libs.ktor.clientCore)
        }
        
        jvmMain.dependencies {
            // CIO engine for JVM
            implementation(libs.ktor.clientCio)
        }
        
        jsMain.dependencies {
            // Js engine for JavaScript
            implementation(libs.ktor.clientJs)
        }
        
        val wasmJsMain by getting {
            dependencies {
                // Js engine for WebAssembly
                implementation(libs.ktor.clientJs)
            }
        }
        
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}


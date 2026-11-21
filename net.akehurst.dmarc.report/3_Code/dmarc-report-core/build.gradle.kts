plugins {
    id("project-conventions")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.nak.kotlinx.filesystem)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ksoup)
                implementation(libs.ktor.client.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        webMain {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }
}

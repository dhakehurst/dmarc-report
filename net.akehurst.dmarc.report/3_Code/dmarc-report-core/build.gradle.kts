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
            }
        }
    }
}

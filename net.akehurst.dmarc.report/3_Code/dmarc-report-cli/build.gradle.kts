import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("project-conventions")
}

kotlin {
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        binaries {
            executable {
                applicationName.set("dmarc-report")
                mainClass.set("net.akehurst.dmarc.report.cli.MainJvmKt")
            }
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":dmarc-report-core"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        jvmMain {
            dependencies {
                // ktor logs through SLF4J; this keeps it quiet
                runtimeOnly(libs.slf4j.nop)
            }
        }
    }
}

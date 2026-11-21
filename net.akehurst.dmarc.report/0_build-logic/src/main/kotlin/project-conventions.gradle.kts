/**
 * Copyright (C) 2026 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.buildconfig)
}
val kotlin_languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_4
val kotlin_apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_4
val jvmTargetVersion = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

repositories {
    mavenLocal {
        content {
            includeGroupByRegex("net\\.akehurst.+")
        }
        mavenContent {
            snapshotsOnly()
        }
    }
    mavenCentral()
    gradlePluginPortal()
    google()
}

group = rootProject.name
version = libs.versions.project.get()
project.layout.buildDirectory = File(rootProject.projectDir, ".gradle-build/${project.name}")

buildConfig {
    val now = Instant.now()
    fun fBbuildStamp(): String = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC")).format(now)
    fun fBuildDate(): String = DateTimeFormatter.ofPattern("yyyy-MMM-dd").withZone(ZoneId.of("UTC")).format(now)
    fun fBuildTime(): String = DateTimeFormatter.ofPattern("HH:mm:ss z").withZone(ZoneId.of("UTC")).format(now)

    packageName("${project.group}.${project.name.replace("-", ".")}")
    buildConfigField("String", "version", "\"${project.version}\"")
    buildConfigField("String", "buildStamp", "\"${fBbuildStamp()}\"")
    buildConfigField("String", "buildDate", "\"${fBuildDate()}\"")
    buildConfigField("String", "buildTime", "\"${fBuildTime()}\"")
}

kotlin {
    applyDefaultHierarchyTemplate()
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
    jvm {
        val main by compilations.getting {
            compileTaskProvider.configure {
                compilerOptions {
                    languageVersion.set(kotlin_languageVersion)
                    apiVersion.set(kotlin_apiVersion)
                    jvmTarget.set(jvmTargetVersion)
                }
            }
        }
        val test by compilations.getting {
            compileTaskProvider.configure {
                compilerOptions {
                    languageVersion.set(kotlin_languageVersion)
                    apiVersion.set(kotlin_apiVersion)
                    jvmTarget.set(jvmTargetVersion)
                }
            }
        }
    }
    js {
        nodejs()
        browser()
        compilerOptions {
            target.set("es2015")
        }
    }
    wasmJs {
        browser()
    }
    //macosArm64() // disabled for now: needs Xcode to build

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(kotlin("test"))
            implementation(kotlin("test-annotations-common"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    filter {
        isFailOnNoMatchingTests = false
    }
    testLogging {
        showExceptions = true
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

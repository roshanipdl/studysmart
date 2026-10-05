import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.nio.file.Files

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val curriculumSnapshot = rootProject.layout.projectDirectory.dir("../Android data/database-stage-1")
val curriculumAssets = layout.buildDirectory.dir("generated/curriculumAssets/debug")
val prepareDebugCurriculumAssets by tasks.registering(Sync::class) {
    group = "verification"
    description = "Validate and package the immutable draft curriculum for debug only."
    from(curriculumSnapshot) {
        include("bundle-manifest.json", "curriculum.json", "subjects/*.json")
        into("curriculum")
    }
    into(curriculumAssets)
    doFirst {
        val root = curriculumSnapshot.asFile
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val manifest = JsonSlurper().parse(root.resolve("bundle-manifest.json")) as Map<*, *>
        require(manifest["schemaVersion"] == 1 && manifest["contentVersion"] == 1 && manifest["datasetId"] == "bca-local-curriculum") { "Unexpected Stage 1 manifest" }
        val rows = manifest["files"] as List<*>
        val entries = rows.map { it as Map<*, *> }.map { it["path"] as String to it["sha256"] as String }
        require(entries.map { it.first }.distinct().size == entries.size) { "Duplicate manifest file" }
        entries.forEach { (path, expected) ->
            require(path == "curriculum.json" || Regex("subjects/[A-Za-z0-9_-]+\\.json").matches(path)) { "Unsafe manifest path" }
            val file = root.resolve(path)
            require(file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) && !Files.isSymbolicLink(file.toPath())) { "Unsafe snapshot file" }
            require(hash(file.readBytes()) == expected) { "Curriculum hash mismatch: $path" }
        }
        val catalog = JsonSlurper().parse(root.resolve("curriculum.json")) as Map<*, *>
        val subjectPaths = (catalog["subjectFiles"] as List<*>).map { (it as Map<*, *>)["path"] as String }
        require(subjectPaths.distinct().size == subjectPaths.size && entries.map { it.first }.toSet() == setOf("curriculum.json") + subjectPaths) { "Catalog/manifest mismatch" }
        val actual = root.walkTopDown().filter { it.isFile && it.extension == "json" }.map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
        require(actual == entries.map { it.first }.toSet() + "bundle-manifest.json") { "Unexpected snapshot JSON files" }
        val digest = hash(entries.sortedBy { it.first }.joinToString("") { "${it.first}\t${it.second}\n" }.toByteArray(Charsets.UTF_8))
        require(digest == manifest["bundleDigest"]) { "Bundle digest mismatch" }
    }
}

android {
    namespace = "com.studysmart.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.studysmart.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets.getByName("androidTest").assets.srcDir("schemas")
    sourceSets.getByName("debug").assets.srcDir(curriculumAssets)
}

tasks.matching {
    it.name in setOf("mergeDebugAssets", "generateDebugAssets", "mergeDebugUnitTestAssets", "generateDebugUnitTestAssets") ||
        (it.name.contains("Debug") && (it.name.contains("Lint") || it.name.startsWith("lint")))
}
    .configureEach { dependsOn(prepareDebugCurriculumAssets) }

tasks.withType<Test>().configureEach {
    maxHeapSize = "256m"
    systemProperty("curriculum.snapshot", curriculumSnapshot.asFile.absolutePath)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20250517")

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

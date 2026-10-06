import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.nio.file.Files
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val curriculumSnapshot = rootProject.layout.projectDirectory.dir("curriculum/database-stage-1-formatted")
val reviewedCurriculumSnapshot = rootProject.layout.projectDirectory.dir("curriculum/database-stage-1-reviewed")
val legacyCurriculumSnapshot = rootProject.layout.projectDirectory.dir("curriculum/database-stage-1")
val curriculumAssets = layout.buildDirectory.dir("generated/curriculumAssets/debug")
val validateDebugCurriculumBundle by tasks.registering {
    group = "verification"
    description = "Validate and package the immutable draft curriculum for debug only."
    doLast {
        val root = curriculumSnapshot.asFile
        require(root.isDirectory && root.resolve("bundle-manifest.json").isFile) {
            "Required curriculum bundle missing: $root (restore curriculum/database-stage-1-formatted)"
        }
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val manifest = JsonSlurper().parse(root.resolve("bundle-manifest.json")) as Map<*, *>
        require(manifest["schemaVersion"] == 2 && manifest["contentVersion"] == 1 && manifest["datasetId"] == "bca-stage1-formatted") { "Unexpected formatted Stage 1 manifest" }
        val rows = manifest["files"] as List<*>
        val entries = rows.map { it as Map<*, *> }.map { it["path"] as String to it["sha256"] as String }
        require(entries.size == 11) { "Formatted bundle requires exactly 11 data files" }
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
        require(digest == "876b3154fca24caa8324c3613d2905da1336924f5c9ec1c5b7576f2d7fede6e5") { "Unapproved formatted bundle" }
    }
}

val prepareDebugCurriculumAssets by tasks.registering(Sync::class) {
    dependsOn(validateDebugCurriculumBundle)
    from(curriculumSnapshot) {
        include("bundle-manifest.json", "curriculum.json", "subjects/*.json")
        into("curriculum")
    }
    into(curriculumAssets)
}

tasks.register("verifyDebugCurriculumApk") {
    group = "verification"
    description = "Verify current debug APK curriculum bytes and exact inventory."
    dependsOn("assembleDebug", validateDebugCurriculumBundle)
    doLast {
        val outputs = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        val metadata = JsonSlurper().parse(outputs.resolve("output-metadata.json")) as Map<*, *>
        val elements = metadata["elements"] as List<*>
        require(elements.isNotEmpty()) { "No current debug APK outputs" }
        val root = curriculumSnapshot.asFile
        val manifestBytes = root.resolve("bundle-manifest.json").readBytes()
        val manifest = JsonSlurper().parse(manifestBytes.inputStream()) as Map<*, *>
        val files = (manifest["files"] as List<*>).map { it as Map<*, *> }
        val expected = files.map { "assets/curriculum/${it["path"]}" }.toSet() + "assets/curriculum/bundle-manifest.json"
        elements.forEach { element ->
            val apk = outputs.resolve((element as Map<*, *>)["outputFile"] as String)
            require(apk.isFile && apk.canonicalFile.parentFile == outputs.canonicalFile) { "Missing or unsafe APK output" }
            ZipFile(apk).use { zip ->
                val actual = zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("assets/curriculum/") }.map { it.name }.toList()
                require(actual.size == expected.size && actual.toSet() == expected) { "APK curriculum inventory mismatch" }
                require(zip.getInputStream(zip.getEntry("assets/curriculum/bundle-manifest.json")).use { it.readBytes() }.contentEquals(manifestBytes)) { "APK manifest bytes mismatch" }
                files.forEach { row ->
                    val bytes = zip.getInputStream(zip.getEntry("assets/curriculum/${row["path"]}")).use { it.readBytes() }
                    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                    require(hash == row["sha256"]) { "APK curriculum hash mismatch: ${row["path"]}" }
                }
            }
            logger.lifecycle("Verified {}: {} curriculum entries, bundle {}", apk, expected.size, manifest["bundleDigest"])
        }
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

    sourceSets.getByName("androidTest").assets.setSrcDirs(listOf("schemas"))
    sourceSets.getByName("debug").assets.srcDir(curriculumAssets)
}

tasks.matching {
    it.name in setOf("mergeDebugAssets", "generateDebugAssets", "mergeDebugUnitTestAssets", "generateDebugUnitTestAssets") ||
        (it.name.contains("Debug") && (it.name.contains("Lint") || it.name.startsWith("lint")))
}
    .configureEach { dependsOn(prepareDebugCurriculumAssets) }

tasks.withType<Test>().configureEach {
    inputs.dir(legacyCurriculumSnapshot).withPropertyName("curriculumSnapshot")
    inputs.dir(reviewedCurriculumSnapshot).withPropertyName("curriculumReviewed")
    inputs.dir(curriculumSnapshot).withPropertyName("curriculumFormatted")
    maxHeapSize = "256m"
    // Existing adversarial Stage 1 fixtures stay immutable across content upgrades.
    systemProperty("curriculum.snapshot", legacyCurriculumSnapshot.asFile.absolutePath)
    systemProperty("curriculum.reviewed", reviewedCurriculumSnapshot.asFile.absolutePath)
    systemProperty("curriculum.formatted", curriculumSnapshot.asFile.absolutePath)
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

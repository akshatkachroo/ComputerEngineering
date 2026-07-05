import java.io.RandomAccessFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.scribesync.scribesync"
    compileSdk = 35
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.scribesync.scribesync"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags("")
            }
        }
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

// A build that is cancelled or killed mid-compile (Stop button, daemon crash, OOM) leaves a
// truncated .o in .cxx. Ninja's up-to-date check is timestamp-only, so it never recompiles the
// file and every link fails ("ld.lld: section header string table index N does not exist")
// until .cxx is deleted. Detect objects whose ELF section header table lies past EOF and delete
// them so ninja rebuilds just those files.
val purgeCorruptNativeObjects = tasks.register("purgeCorruptNativeObjects") {
    val cxxDir = file(".cxx")
    doLast {
        fun isTruncatedElf(f: File): Boolean = RandomAccessFile(f, "r").use { raf ->
            if (raf.length() < 64) return@use true
            val ident = ByteArray(5).also { raf.readFully(it) }
            if (ident[0] != 0x7F.toByte() || ident[1] != 'E'.code.toByte() ||
                ident[2] != 'L'.code.toByte() || ident[3] != 'F'.code.toByte()
            ) return@use true
            val is64 = ident[4].toInt() == 2
            fun read(pos: Long, bytes: Int): Long {
                raf.seek(pos)
                val b = ByteArray(bytes).also { raf.readFully(it) }
                var v = 0L
                for (i in b.indices.reversed()) v = (v shl 8) or (b[i].toLong() and 0xFF)
                return v
            }
            val shOff = if (is64) read(0x28, 8) else read(0x20, 4)
            val shEntSize = read(if (is64) 0x3A else 0x2E, 2)
            val shNum = read(if (is64) 0x3C else 0x30, 2)
            shNum == 0L || raf.length() < shOff + shEntSize * shNum
        }
        if (cxxDir.exists()) {
            cxxDir.walkTopDown()
                .filter { it.isFile && it.extension == "o" }
                .filter { isTruncatedElf(it) }
                .forEach {
                    logger.lifecycle("Deleting corrupt native object left by an interrupted build: $it")
                    it.delete()
                }
        }
    }
}

tasks.matching { it.name.startsWith("buildCMake") || it.name.startsWith("configureCMake") }
    .configureEach { dependsOn(purgeCorruptNativeObjects) }

// `clean` (Android Studio's "Clean Project") only deletes build/, never .cxx — so it cannot
// recover a corrupted native build. Run this instead of deleting folders by hand.
tasks.register<Delete>("cleanNative") {
    group = "build"
    description = "Deletes all CMake/ninja state (.cxx and native intermediates) to force a full native rebuild."
    delete(".cxx")
    delete(layout.buildDirectory.dir("intermediates/cxx"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    // Location
    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

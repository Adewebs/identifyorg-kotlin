plugins {
    id("com.android.library") version "8.5.2"
    kotlin("android") version "2.0.21"
    id("maven-publish")
}

android {
    namespace = "com.identifyorg.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api("io.livekit:livekit-android:2.+")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
}

// JitPack builds this task to produce the artifact consumers pull in.
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.Adewebs"
                artifactId = "identifyorg-kotlin"
            }
        }
    }
}

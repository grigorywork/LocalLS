plugins {
    id("com.android.application")
}

val releaseStore = providers.environmentVariable("LOCALLS_RELEASE_STORE").orNull
val releaseStorePassword = providers.environmentVariable("LOCALLS_RELEASE_STORE_PASSWORD").orNull
val releaseAlias = providers.environmentVariable("LOCALLS_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("LOCALLS_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    doLast {
        check(hasReleaseSigning) { "Set all four LOCALLS_RELEASE_* signing environment variables." }
        check(file(releaseStore!!).isFile) { "Release keystore file was not found." }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

android {
    namespace = "com.bitpoint.homeservercontrol"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bitpoint.homeservercontrol"
        minSdk = 24
        targetSdk = 37
        versionCode = 14
        versionName = "0.9.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("ownerRelease") {
                storeFile = file(releaseStore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("ownerRelease")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":transport"))
    implementation(project(":ui"))
    androidTestImplementation("com.github.mwiede:jsch:2.28.7")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

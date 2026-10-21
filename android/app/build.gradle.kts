import org.gradle.api.file.DirectoryProperty
import java.util.Properties
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
}

abstract class StageAgentAssets : DefaultTask() {
    @get:InputDirectory abstract val sourceDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @TaskAction fun stage() {
        val destination = outputDir.get().asFile
        destination.mkdirs()
        listOf("agent.py", "setup_agent.py").forEach { name ->
            sourceDir.get().file(name).asFile.copyTo(destination.resolve(name), overwrite = true)
        }
    }
}
val agentAssets = tasks.register<StageAgentAssets>("stageAgentAssets") {
    sourceDir.set(rootProject.layout.projectDirectory.dir("realme-agent"))
    outputDir.set(layout.buildDirectory.dir("generated/agentAssets"))
}
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(agentAssets, StageAgentAssets::outputDir)
    }
}

val releaseMetadata = Properties().apply {
    rootProject.file("release.properties").inputStream().use { load(it) }
}
val androidVersionName = releaseMetadata.getProperty("versionName")
val androidVersionCode = releaseMetadata.getProperty("versionCode").toInt()
check(androidVersionCode > releaseMetadata.getProperty("previousVersionCode").toInt()) {
    "Android versionCode must increase for updates."
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
        val password = releaseStorePassword!!.toCharArray()
        try {
            val keystore = KeyStore.getInstance(file(releaseStore), password)
            check(keystore.isKeyEntry(releaseAlias)) { "Release alias must contain a private key." }
            val certificate = keystore.getCertificate(releaseAlias)
            val actual = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            check(actual == releaseMetadata.getProperty("releaseCertificateSha256")) {
                "Signing certificate differs from published LocalLS; update would be incompatible."
            }
        } finally { password.fill('\u0000') }
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
        versionCode = androidVersionCode
        versionName = androidVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources.merges.addAll(listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/NOTICE"))
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

// Robolectric native SQLite bindings cannot be shared across all sandbox configurations.
// Each test class gets a fresh process; the release APK is unaffected.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    forkEvery = 1L
}

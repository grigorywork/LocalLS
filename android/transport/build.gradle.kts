plugins { id("com.android.library") }

android {
    namespace = "com.bitpoint.homeservercontrol.transport"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(project(":core"))
    implementation("com.github.mwiede:jsch:2.28.7")
}

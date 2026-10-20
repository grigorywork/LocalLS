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
    implementation(project(":data"))
    implementation("com.github.mwiede:jsch:2.28.7")
    implementation("org.apache.sshd:sshd-core:2.20.0")
    implementation("org.apache.sshd:sshd-sftp:2.20.0")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")
}

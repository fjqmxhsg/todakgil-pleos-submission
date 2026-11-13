plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "local.todakgil.probe"
    compileSdk = 35
    defaultConfig { applicationId = "local.todakgil.probe"; minSdk = 28; targetSdk = 35; versionCode = 1; versionName = "0.1-probe" }
    buildTypes {
        getByName("release") {
            applicationIdSuffix = ".submission"
            versionNameSuffix = "-submission"
            isDebuggable = false
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    testImplementation("junit:junit:4.13.2")
}

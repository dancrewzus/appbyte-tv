import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Per-install settings come from launcher/local.properties (never committed) or -P flags; see docs/INSTALL.md.
val localProps = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
fun cfg(key: String, default: String = ""): String = localProps.getProperty(key) ?: (project.findProperty(key) as String?) ?: default

android {
    namespace = "cl.appbyte.tv"
    compileSdk = 35
    defaultConfig {
        applicationId = "cl.appbyte.tv"
        minSdk = 26          // Android 8 (2017 TVs) and up
        targetSdk = 30
        versionCode = 11
        versionName = "1.1"
        buildConfigField("String", "HUB_URL", "\"${cfg("appbyte.hubUrl")}\"")               // optional home hub (see docs/HUB_API.md); empty = no home features
        buildConfigField("String", "USER_NAME", "\"${cfg("appbyte.userName")}\"")           // initial shown in the avatar circle
        buildConfigField("String", "AVATAR_NAME", "\"${cfg("appbyte.avatarName", "me")}\"")  // name asked to the hub for the profile picture
        buildConfigField("double", "LAT", cfg("appbyte.lat", "0.0"))                          // location: day/sunset/night and the weather (0/0 = use the clock)
        buildConfigField("double", "LON", cfg("appbyte.lon", "0.0"))
        buildConfigField("String", "LIVE_TV", "\"${cfg("appbyte.liveTv")}\"")                // "package/Activity" of the TV's live-TV app; empty hides the tab
    }
    // full = everything; lite = no sensitive permissions (installs on TVs whose Play Protect blocks "full"); loc = lite + location (shows the Wi-Fi name). See docs/INSTALL.md.
    flavorDimensions += "tier"
    productFlavors { create("full") { dimension = "tier" }; create("loc") { dimension = "tier" }; create("lite") { dimension = "tier" } }
    lint { checkReleaseBuilds = false }                 // targetSdk stays 30 on purpose for private installs; Play publishing needs 34+ (see docs/INSTALL.md)
    buildFeatures { buildConfig = true }
    buildTypes { release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")   // the only dependency: TVs have ~1.4 GB of RAM, the launcher must stay tiny
}

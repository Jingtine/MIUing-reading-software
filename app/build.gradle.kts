plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val personalFonts = providers.gradleProperty("personalFonts").orNull == "true"
val personalFontAssets = rootProject.file(".local-fonts")
if (personalFonts) {
    check(listOf("st-kaiti.ttf", "kai-ti.ttf").all { personalFontAssets.resolve("fonts/$it").isFile }) {
        "Personal fonts require .local-fonts/fonts/st-kaiti.ttf and kai-ti.ttf; see docs/reading-fonts-0.6.8.md"
    }
}
android {
    namespace = "com.miuing.reader"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.miuing.reader"
        minSdk = 26
        targetSdk = 36
        versionCode = 26
        versionName = "0.7.5"
        buildConfigField("boolean", "PERSONAL_FONTS", personalFonts.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    if (personalFonts) sourceSets.getByName("main").assets.srcDir(personalFontAssets)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("net.lingala.zip4j:zip4j:2.11.6")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

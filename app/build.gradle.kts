import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// The assistant key lives in local.properties (gitignored) and reaches the app through BuildConfig.
// It is never written into a tracked file.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val aiKey = localProperties.getProperty("DEEPCOOK_AI_KEY").orEmpty()

android {
    namespace = "com.deepcook.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.deepcook.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 23
        versionName = "0.6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEEPCOOK_AI_KEY", "\"" + aiKey.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
        // The keyword spotter ships native libs; phones are all arm64 or armv7.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Locally installable optimized build; production release signing is separate.
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    // sherpa-onnx mmaps its models straight out of assets; a compressed asset cannot be opened
    // that way, so the recogniser and keyword-spotter models must be stored uncompressed.
    androidResources { noCompress += listOf("onnx") }
}
kotlin { jvmToolchain(21) }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.navigation)
    implementation(libs.datastore)
    implementation(libs.serialization)
    implementation(libs.coroutines)
    implementation(libs.coil)
    implementation(libs.exifinterface)
    implementation(libs.material.motion)
    implementation(libs.sherpa.onnx)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
}

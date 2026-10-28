import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":sharedUI"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

/** Google Wallet issuer and pass service settings, from `local.properties` (not committed) or Gradle properties. */
val walletProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun walletProperty(name: String): String =
    (walletProperties.getProperty(name) ?: providers.gradleProperty(name).orNull).orEmpty()

android {
    namespace = "cz.poqet.walletka"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "cz.poqet.walletka"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GOOGLE_ISSUER_ID", "\"${walletProperty("walletka.googleIssuerId")}\"")
        buildConfigField("String", "GOOGLE_ISSUER_EMAIL", "\"${walletProperty("walletka.googleIssuerEmail")}\"")
        buildConfigField("String", "SERVICE_URL", "\"${walletProperty("walletka.serviceUrl")}\"")
        buildConfigField("String", "SERVICE_API_KEY", "\"${walletProperty("walletka.serviceApiKey")}\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

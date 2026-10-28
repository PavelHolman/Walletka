import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    // Swift Export (instead of the Objective-C framework export).
    // Xcode consumes it through `./gradlew :sharedLogic:embedSwiftExportForXcode`.
    swiftExport {
        moduleName = "SharedLogic"
        flattenPackage = "cz.poqet.walletka"
    }

    android {
        namespace = "cz.poqet.walletka.sharedLogic"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    sourceSets {
        matching { it.name.startsWith("ios") }.configureEach {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
        }
        commonMain.dependencies {
            api(libs.decompose)
            api(libs.essenty.lifecycle)
            api(libs.essenty.stateKeeper)
            api(libs.essenty.instanceKeeper)
            api(libs.essenty.backHandler)
            implementation(libs.essenty.lifecycle.coroutines)
            api(libs.arrow.core)
            implementation(libs.arrow.fx.coroutines)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.io.core)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.playServices)
            implementation(libs.mlkit.barcodeScanning)
            implementation(libs.mlkit.textRecognition)
            implementation(libs.playServices.pay)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

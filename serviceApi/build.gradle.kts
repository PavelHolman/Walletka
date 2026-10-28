plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

// The GraphQL contract between the app and the pass service, shared so neither side can drift.
kotlin {
    jvm()
    js {
        nodejs()
    }
    iosArm64()
    iosSimulatorArm64()
}

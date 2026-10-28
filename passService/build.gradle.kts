plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Cloudflare Worker (see worker/). JVM only runs the tests against OpenSSL.
    js {
        outputModuleName = "pass-service"
        useEsModules()
        nodejs {
            testTask {
                useMocha { timeout = "20s" }
            }
        }
        binaries.library()
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":serviceApi"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jsMain.dependencies {
            implementation(npm("graphql", libs.versions.npm.graphql.get()))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

/** Copies the compiled Worker code where Wrangler picks it up (`worker/src/index.mjs` imports it). */
val assembleWorker by tasks.registering(Sync::class) {
    dependsOn("jsProductionLibraryCompileSync")
    from(layout.buildDirectory.dir("compileSync/js/main/productionLibrary/kotlin"))
    into(layout.projectDirectory.dir("worker/build/kotlin"))
}

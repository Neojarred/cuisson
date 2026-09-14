plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "app.cuisson.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:domain"))
            implementation(project(":shared:text"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            // Lets the schema and its migrations run against a real SQLite database off
            // the device, which is the only way to know a migration works before it
            // touches someone's phone. No JVM build of Cuisson ships.
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

sqldelight {
    databases {
        create("CuissonDatabase") {
            packageName.set("app.cuisson.data.db")
        }
    }
}

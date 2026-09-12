rootProject.name = "cuisson"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":shared:domain")
include(":shared:data")
include(":shared:importer")
include(":shared:text")
include(":androidApp")

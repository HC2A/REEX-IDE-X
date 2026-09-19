pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://storage.googleapis.com/download.flutter.io")
        val flutterRepo = file("$rootDir/app/flutter_repo")
        if (flutterRepo.exists()) {
            maven { url = flutterRepo.toURI() }
        }
    }
}

rootProject.name = "REEX-IDE-X"
include(":app")

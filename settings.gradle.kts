pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }

        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/trustwallet/wallet-core")
            credentials {
                username = System.getenv("TRUSTWALLET_USER")?.takeIf { it.isNotBlank() }
                    ?: System.getenv("GITHUB_ACTOR")?.takeIf { it.isNotBlank() }
                    ?: "token"
                password = System.getenv("TRUSTWALLET_PAT")?.takeIf { it.isNotBlank() }
                    ?: System.getenv("GH_TOKEN")?.takeIf { it.isNotBlank() }
                    ?: ""
            }
            content {
                includeGroup("com.trustwallet")
            }
        }
    }
}

rootProject.name = "vultisig"
include(":app")
include(":data")

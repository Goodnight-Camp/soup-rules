pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
// core의 jvmToolchain(17)용 JDK 17이 로컬에 없으면 자동으로 내려받습니다.
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" }
dependencyResolutionManagement {
 repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
 repositories { google(); mavenCentral(); maven("https://nexus-playground.pleos.ai/repository/maven-releases/") }
}
rootProject.name = "SoupRules"
include(":app", ":core")

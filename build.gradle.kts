import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PublishPluginTask

plugins {
  id("org.jetbrains.intellij") version "1.15.0"
}
repositories {
  mavenCentral()
  maven("https://dl.bintray.com/jetbrains/intellij-plugin-service")
}

allprojects {
  version = "0.13"

  apply {
    plugin("java")
  }
  configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  sourceSets {
    main {
      java.srcDirs("main/src")
      resources.srcDirs("main/resources")
    }
    test {
      java.srcDir("tests/src")
      resources.srcDirs("tests/testData")
    }
  }
  apply {
    plugin("org.jetbrains.intellij")
  }
  intellij {
    pluginName.set("dg-test-helper")
    type.set("IC")
    localPath.set(project.properties["local.idea"] as String?)
    plugins.set(listOf("java", "DatabaseTools"))
  }

  tasks {
    withType<PatchPluginXmlTask> {
      sinceBuild.set("233")
      untilBuild.set("993")
    }

    withType<PublishPluginTask> {
//      username.set(project.properties["publish.user"])
      token.set(project.properties["publish.token"] as String?)
      channels.set(listOf(project.properties["publish.channel"] as String? ?: "Stable"))
    }
    runIde {
      maxHeapSize = "2g"
    }

    buildSearchableOptions {
      enabled = false
    }
  }
}


import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "2.1.21" // Kotlin version to use
  groovy
  application
  id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "org.noise-planet"
version = "1.0.0-SNAPSHOT"

repositories {
  maven("https://maven.geotoolkit.org")
  maven("https://repo.osgeo.org/repository/release/")
  mavenCentral()
}

val vertxVersion = "5.0.0"
val junitJupiterVersion = "5.9.1"

val mainVerticleName = "org.noise_planet.onomap.MainVerticle"
val launcherClassName = "io.vertx.core.Launcher"

val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

application {
  mainClass.set(launcherClassName)
}

dependencies {
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  implementation("com.zaxxer:HikariCP:[6.3.0,7)")
  implementation("org.apache.groovy:groovy-all:[4.0.26,5)")
  implementation("org.postgresql:postgresql:42.7.2")
  implementation("io.vertx:vertx-web")
  implementation("org.orbisgis:h2gis:[2.2.3,3)")
  implementation("org.osgi:org.osgi.service.jdbc:[1.0.0,2)")
  implementation("io.vertx:vertx-config")
  implementation("io.vertx:vertx-lang-kotlin")
  implementation("org.jetbrains.kotlinx:dataframe:1.0.0-Beta2")
  implementation("org.apache.commons:commons-text:[1.13.1,2)")
  implementation(kotlin("stdlib-jdk8"))
  implementation("org.osgi:org.osgi.framework:1.10.0")
  implementation("com.ongres.scram:client:2.1")
  implementation("org.geotools.xsd:gt-xsd-wps:[22.2,23)")
  implementation("org.geotools.xsd:gt-xsd-core:[22.2,23)")
  testImplementation("org.slf4j:slf4j-simple:[2.0.17,3)")
  testImplementation("io.vertx:vertx-junit5")
  testImplementation("io.vertx:vertx-web-client")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
}

tasks.withType<ShadowJar> {
  archiveClassifier.set("fat")
  manifest {
    attributes(mapOf("Main-Verticle" to mainVerticleName))
  }
  mergeServiceFiles()
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}

tasks.withType<JavaExec> {
  args = listOf("run", mainVerticleName, "--redeploy=$watchForChange", "--launcher-class=$launcherClassName", "--on-redeploy=$doOnChange")
}

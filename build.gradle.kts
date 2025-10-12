import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode.IGNORE
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
  kotlin("jvm") version "2.0.0"
  application
  // spring boot plugin
  id("org.springframework.boot") version "3.5.6"
}

group = "pl.kperczynski"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral()
}

val vertxVersion = "5.0.4"
val junitJupiterVersion = "5.9.1"

val mainVerticleName = "pl.kperczynski.kube_spot_operator.MainVerticle"
val launcherClassName = "io.vertx.launcher.application.VertxApplication"

application {
  mainClass.set(launcherClassName)
}

dependencies {
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  implementation("io.vertx:vertx-launcher-application")
  implementation("io.vertx:vertx-web-client")
  implementation("io.vertx:vertx-web")
  implementation("io.vertx:vertx-core")
  implementation("io.vertx:vertx-lang-kotlin")

  implementation("org.slf4j:slf4j-api:2.0.17")
  implementation("ch.qos.logback:logback-classic:1.5.19")
  implementation("net.logstash.logback:logstash-logback-encoder:8.1")
  implementation("org.codehaus.janino:janino:3.1.12")

  testImplementation("io.vertx:vertx-junit5")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions.jvmTarget = JvmTarget.JVM_21

tasks.withType<KotlinJvmCompile>().configureEach {
  jvmTargetValidationMode.set(IGNORE)
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}

tasks.withType<JavaExec> {
  args = listOf(mainVerticleName)
}

tasks.withType<Jar> {
  manifest {
    attributes["Main-Verticle"] = mainVerticleName
  }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
  archiveClassifier.set("layered")
  mainClass.set("pl.kperczynski.kube_spot_operator.KubeSpotOperatorApplicationKt")
  layered {
    layerOrder = listOf("deps", "app")
    application {
      intoLayer("app")
    }
    dependencies {
      intoLayer("deps")
    }
  }
}

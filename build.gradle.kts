plugins {
    java
}

group = "fr.vorace"
version = providers.gradleProperty("version").get()
val paperApi = providers.gradleProperty("paperApi").get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

// Copies de compilation des quelques classes internes de Paper utilisées (hébergement sur le port du jeu).
// Elles ne sont PAS mises dans le .jar : au lancement ce sont les vraies classes du serveur qui servent.
val stubs: SourceSet by sourceSets.creating

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    compileOnly("io.netty:netty-transport:4.1.97.Final")
    compileOnly("io.netty:netty-buffer:4.1.97.Final")
    compileOnly(stubs.output)
    "stubsCompileOnly"("io.netty:netty-transport:4.1.97.Final")
    "stubsCompileOnly"("net.kyori:adventure-key:4.17.0")

    testImplementation("io.papermc.paper:paper-api:$paperApi")
    testImplementation("io.netty:netty-transport:4.1.97.Final")
    testImplementation("io.netty:netty-buffer:4.1.97.Final")
    testImplementation(stubs.output)
}

java {
    // JDK 25 pour pouvoir lire l'API de Paper 26.x ; le code produit reste compatible Java 21 (release = 21)
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    // l'API 26.x se déclare « Java 25 » : on l'accepte quand même pour vérifier la compatibilité
    disableAutoTargetJvm()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("plugin.yml") { expand("version" to v) }
}

// Les tests sont des programmes simples lancés par la tâche "tests" (pas JUnit)
tasks.test {
    enabled = false
}

tasks.jar {
    archiveFileName = "Vorace-${project.version}.jar"
}

// Tests sans serveur (fusion des packs, hébergement HTTP) : ./gradlew tests
tasks.register<JavaExec>("tests") {
    group = "verification"
    description = "Lance les tests de fusion et d'hébergement"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "fr.vorace.Tests"
}

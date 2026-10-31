plugins {
    java
    `maven-publish`
}

group = "fr.vorace"
version = providers.gradleProperty("version").get()
val paperApi = providers.gradleProperty("paperApi").get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/") // PlaceholderAPI (facultatif au lancement)
}

// Copies de compilation des quelques classes internes de Paper utilisées (hébergement sur le port du jeu).
// Elles ne sont PAS mises dans le .jar : au lancement ce sont les vraies classes du serveur qui servent.
val stubs: SourceSet by sourceSets.creating

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    compileOnly("me.clip:placeholderapi:2.11.6")
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
    // (-Pjdk=21 pour JitPack, qui n'a pas forcément Java 25 : suffisant pour l'API 1.21.x)
    toolchain.languageVersion = JavaLanguageVersion.of(providers.gradleProperty("jdk").orElse("25").get().toInt())
    withSourcesJar()
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

// les sources (pour JitPack) hors de build/libs, pour ne pas les confondre avec le plugin
tasks.named<Jar>("sourcesJar") {
    destinationDirectory = layout.buildDirectory.dir("sources")
}

// Tests sans serveur (fusion des packs, hébergement HTTP) : ./gradlew tests
tasks.register<JavaExec>("tests") {
    group = "verification"
    description = "Lance les tests de fusion et d'hébergement"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "fr.vorace.Tests"
}

// Dépendance pour les autres plugins (via JitPack) :
//   repositories { maven("https://jitpack.io") }
//   dependencies { compileOnly("com.github.ORA-KUN:Vorace:Vorace-1.0.0") }
publishing {
    publications {
        create<MavenPublication>("vorace") {
            groupId = "com.github.ORA-KUN"
            artifactId = "Vorace"
            from(components["java"])
            pom {
                name = "Vorace"
                description = "Plugin Paper libre : packs, objets, meubles, blocs, glyphes, modèles animés, couche paquets"
                url = "https://github.com/ORA-KUN/Vorace"
                licenses { license { name = "MIT"; url = "https://opensource.org/licenses/MIT" } }
            }
        }
    }
}

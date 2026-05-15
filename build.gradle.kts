plugins {
    id("java-library")
    id("application")
}

group = "dev.sbs"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven(url = "https://central.sonatype.com/repository/maven-snapshots")
    maven(url = "https://jitpack.io")
}

dependencies {
    // Lombok Annotations
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Tests
    testImplementation(libs.hamcrest)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)

    // https://central.sonatype.com/artifact/com.discord4j/discord4j-core/versions
    api(libs.discord4j)

    // Simplified Libraries (extracted to github.com/simplified-dev)
    api("com.github.simplified-dev:collections") { version { strictly("6586657") } }
    api("com.github.simplified-dev:utils") { version { strictly("ca4cbca") } }
    api("com.github.simplified-dev:reflection") { version { strictly("746e607") } }
    api("com.github.simplified-dev:scheduler") { version { strictly("695f985") } }
    api("com.github.simplified-dev:yaml") { version { strictly("728bffc") } }
    api("com.github.simplified-dev:client") { version { strictly("6a11168") } }
    api("com.github.simplified-dev:dataflow") { version { strictly("670375c") } }

    implementation(libs.sentry)
}

tasks {
    test {
        useJUnitPlatform()
    }

    register<JavaExec>("generateDiagrams") {
        description = "Generates SVG hierarchy diagrams for context and component packages"
        group = "documentation"
        mainClass.set("dev.sbs.discordapi.diagram.DiagramGenerator")
        classpath = sourceSets["test"].runtimeClasspath
    }
}

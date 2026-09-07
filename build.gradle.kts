plugins {
    java
}

group = "fr.loual"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.register<Copy>("copyToServer") {
    dependsOn(tasks.jar)
    from(tasks.jar)
    into(file("../server/plugins"))
}


plugins {
    java
    id("org.springframework.boot") version "4.1.1"
}

group = "app.mealplan"
version = "0.1.0"
java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
tasks.withType<JavaCompile>().configureEach { options.release = 25 }
repositories { mavenCentral() }
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.snakeyaml:snakeyaml-engine:3.2")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
dependencyLocking { lockAllConfigurations(); lockMode = LockMode.STRICT }
tasks.withType<Test>().configureEach { useJUnitPlatform() }
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Generated assets stay under build/, never in source resources.
val frontendDirectory = layout.projectDirectory.dir("../frontend")
val buildFrontend = tasks.register<Exec>("buildFrontend") {
    workingDir(frontendDirectory)
    inputs.files(fileTree(frontendDirectory.dir("src")), frontendDirectory.file("index.html"), frontendDirectory.file("package-lock.json"), frontendDirectory.file("package.json"), frontendDirectory.file("vite.config.ts"), frontendDirectory.file("tsconfig.json"))
    outputs.dir(frontendDirectory.dir("dist"))
    commandLine("npm", "run", "build")
}
val stageFrontend = tasks.register<Sync>("stageFrontend") {
    dependsOn(buildFrontend)
    from(frontendDirectory.dir("dist"))
    into(layout.buildDirectory.dir("generated-resources/frontend/static"))
}
sourceSets.main { resources.srcDir(layout.buildDirectory.dir("generated-resources/frontend")) }
tasks.processResources { dependsOn(stageFrontend) }
tasks.withType<Test>().configureEach { jvmArgs("--enable-native-access=ALL-UNNAMED") }

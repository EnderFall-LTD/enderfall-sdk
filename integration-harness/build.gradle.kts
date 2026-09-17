plugins {
    application
}

description = "Process-level integration test harness for EnderFall SDK runtime targets"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

application {
    mainClass = "uk.co.enderfall.sdk.harness.ServerSmokeMain"
}

dependencies {
    implementation(project(":bridge-compiler"))
    // JSON report tooling only; supplied by the pinned Gradle distribution, never a mod dependency.
    implementation(localGroovy())
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

val runtimeSmokeTargets = listOf(
    "1.20.1-fabric",
    "1.20.1-forge",
    "1.20.1-neoforge",
    "1.21.1-fabric",
    "1.21.1-neoforge",
    "1.21.4-fabric",
    "1.21.4-neoforge",
    "26.2-fabric",
    "26.2-neoforge"
)

val generatedBridgeSmokeTargets = listOf(
    "1.20.1-fabric",
    "1.20.1-forge",
    "1.20.1-neoforge",
    "1.21.1-fabric",
    "1.21.1-neoforge",
    "1.21.4-fabric",
    "1.21.4-neoforge",
    "26.2-fabric",
    "26.2-neoforge"
)

val generatedBridgeRepository = rootProject.layout.buildDirectory.dir("generated-bridge-repository")
val generatedBridgeReportRoot = rootProject.layout.buildDirectory.dir("reports/generated-bridge-runtime-smoke")
val generatedBridgeGradleUserHome = rootProject.layout.buildDirectory.dir("generated-bridge-gradle-user-home")

tasks.register<JavaExec>("serverSmoke") {
    group = "verification"
    description = "Starts and cleanly stops the contract-test dedicated server on every selected target."
    dependsOn(rootProject.tasks.named("publishWorkspace"), tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val selectedTargets = providers.gradleProperty("enderfall.smokeTarget")
        .map { selection -> selection.split(',').map(String::trim).filter(String::isNotEmpty) }
        .getOrElse(runtimeSmokeTargets)
    args(
        rootProject.layout.projectDirectory.asFile.absolutePath,
        rootProject.layout.projectDirectory.dir("test-mod").asFile.absolutePath,
        rootProject.layout.buildDirectory.dir("reports/runtime-smoke").get().asFile.absolutePath,
        *selectedTargets.toTypedArray()
    )
}

tasks.register<JavaExec>("clientSmoke") {
    group = "verification"
    description = "Starts the contract-test client and verifies lifecycle/tick readiness on selected targets."
    dependsOn(rootProject.tasks.named("publishWorkspace"), tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "uk.co.enderfall.sdk.harness.ClientSmokeMain"
    val selectedTargets = providers.gradleProperty("enderfall.smokeTarget")
        .map { selection -> selection.split(',').map(String::trim).filter(String::isNotEmpty) }
        .getOrElse(runtimeSmokeTargets)
    args(
        rootProject.layout.projectDirectory.asFile.absolutePath,
        rootProject.layout.projectDirectory.dir("test-mod").asFile.absolutePath,
        rootProject.layout.buildDirectory.dir("reports/runtime-smoke").get().asFile.absolutePath,
        *selectedTargets.toTypedArray()
    )
}

tasks.register<JavaExec>("generatedBridgeServerSmoke") {
    group = "verification"
    description = "Starts dedicated servers against the centrally generated runtimes."
    dependsOn(rootProject.tasks.named("publishGeneratedBridgeWorkspace"), tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    environment("ENDERFALL_WORKSPACE_REPOSITORY", generatedBridgeRepository.get().asFile.absolutePath)
    environment("GRADLE_USER_HOME", generatedBridgeGradleUserHome.get().asFile.absolutePath)
    val selectedTargets = providers.gradleProperty("enderfall.smokeTarget")
        .map { selection -> selection.split(',').map(String::trim).filter(String::isNotEmpty) }
        .getOrElse(generatedBridgeSmokeTargets)
    args(
        rootProject.layout.projectDirectory.asFile.absolutePath,
        rootProject.layout.projectDirectory.dir("test-mod").asFile.absolutePath,
        generatedBridgeReportRoot.get().asFile.absolutePath,
        *selectedTargets.toTypedArray()
    )
}

tasks.register<JavaExec>("generatedBridgeClientSmoke") {
    group = "verification"
    description = "Starts clients against the centrally generated runtimes."
    dependsOn(rootProject.tasks.named("publishGeneratedBridgeWorkspace"), tasks.named("classes"))
    // Both harnesses prepare the same consumer target and can run memory-heavy native tooling.
    // If requested together, finish the server lane before starting the client lane.
    mustRunAfter("generatedBridgeServerSmoke")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "uk.co.enderfall.sdk.harness.ClientSmokeMain"
    environment("ENDERFALL_WORKSPACE_REPOSITORY", generatedBridgeRepository.get().asFile.absolutePath)
    environment("GRADLE_USER_HOME", generatedBridgeGradleUserHome.get().asFile.absolutePath)
    val selectedTargets = providers.gradleProperty("enderfall.smokeTarget")
        .map { selection -> selection.split(',').map(String::trim).filter(String::isNotEmpty) }
        .getOrElse(generatedBridgeSmokeTargets)
    args(
        rootProject.layout.projectDirectory.asFile.absolutePath,
        rootProject.layout.projectDirectory.dir("test-mod").asFile.absolutePath,
        generatedBridgeReportRoot.get().asFile.absolutePath,
        *selectedTargets.toTypedArray()
    )
}

tasks.register<JavaExec>("sameLoaderSmoke") {
    group = "verification"
    description = "Connects a real client to a dedicated server on each identical loader/version target."
    dependsOn(rootProject.tasks.named("publishWorkspace"), tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "uk.co.enderfall.sdk.harness.SameLoaderSmokeMain"
    args(
        rootProject.layout.projectDirectory.asFile.absolutePath,
        rootProject.layout.projectDirectory.dir("test-mod").asFile.absolutePath,
        rootProject.layout.buildDirectory.dir("reports/runtime-smoke").get().asFile.absolutePath
    )
    providers.gradleProperty("enderfall.smokeTarget").orNull?.let { selection ->
        args(*selection.split(',').map(String::trim).filter(String::isNotEmpty).toTypedArray())
    }
}

tasks.register<JavaExec>("generatedBridgeGameplaySmoke") {
    group = "verification"
    description = "Verifies real menu actions, slot crafting, input return, and networking on generated runtimes."
    dependsOn(rootProject.tasks.named("publishGeneratedBridgeWorkspace"), tasks.named("classes"))
    mustRunAfter("generatedBridgeServerSmoke", "generatedBridgeClientSmoke")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "uk.co.enderfall.sdk.harness.SameLoaderSmokeMain"
    environment("ENDERFALL_WORKSPACE_REPOSITORY", generatedBridgeRepository.get().asFile.absolutePath)
    environment("GRADLE_USER_HOME", generatedBridgeGradleUserHome.get().asFile.absolutePath)
    environment("ENDERFALL_GAMEPLAY_SMOKE", "true")
    environment("ENDERFALL_LEGACY_CONNECTION_DIAGNOSTICS",
        providers.gradleProperty("enderfall.connectionDiagnostics").getOrElse("false").toBooleanStrict().toString())
    val runName = providers.gradleProperty("enderfall.gameplayRun").orNull
    require(runName == null || runName.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))) {
        "enderfall.gameplayRun must contain 1-64 letters, digits, hyphens, or underscores, starting with a letter or digit"
    }
    val reportDirectory = if (runName == null) "reports/generated-bridge-gameplay"
        else "reports/generated-bridge-gameplay/runs/$runName"
    args(rootProject.layout.projectDirectory.asFile.absolutePath,
        rootProject.layout.projectDirectory.dir("test-mod").asFile.absolutePath,
        rootProject.layout.buildDirectory.dir(reportDirectory).get().asFile.absolutePath)
    val selected = providers.gradleProperty("enderfall.smokeTarget")
        .map { it.split(',').map(String::trim).filter(String::isNotEmpty) }.getOrElse(generatedBridgeSmokeTargets)
    require(selected.isNotEmpty()) { "Select at least one gameplay target" }
    args(*selected.toTypedArray())
}

tasks.register<JavaExec>("verifyGameplayMatrix") {
    group = "verification"
    description = "Audits explicitly ordered gameplay reports and logs for all nine catalog targets."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "uk.co.enderfall.sdk.harness.GameplayMatrixMain"
    args(rootProject.layout.projectDirectory.asFile.absolutePath,
        rootProject.layout.buildDirectory.file("reports/generated-bridge-gameplay/matrix.json").get().asFile.absolutePath)
    providers.gradleProperty("enderfall.gameplayReports").orNull?.let { selection ->
        args(*selection.split(',').map(String::trim).filter(String::isNotEmpty).toTypedArray())
    }
}

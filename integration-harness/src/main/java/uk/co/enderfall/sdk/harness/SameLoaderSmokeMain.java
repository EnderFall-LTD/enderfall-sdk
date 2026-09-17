package uk.co.enderfall.sdk.harness;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Runs a real client and dedicated server of the same target in separate processes. */
public final class SameLoaderSmokeMain {
    private static final Duration SERVER_READY_TIMEOUT = Duration.ofMinutes(8);
    private static final Duration PREPARATION_TIMEOUT = Duration.ofMinutes(20);
    private static final Duration CONNECTION_TIMEOUT = Duration.ofMinutes(4);
    private static final Duration EXIT_TIMEOUT = Duration.ofMinutes(2);
    private static final List<Target> TARGETS = List.of(
            new Target("1.20.1-fabric"),
            new Target("1.20.1-forge"),
            new Target("1.20.1-neoforge"),
            new Target("1.21.1-fabric"),
            new Target("1.21.1-neoforge"),
            new Target("1.21.4-fabric"),
            new Target("1.21.4-neoforge"),
            new Target("26.2-fabric"),
            new Target("26.2-neoforge")
    );
    private static final Map<String, Target> TARGET_BY_ID = targetIndex();

    private SameLoaderSmokeMain() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 3) {
            throw new IllegalArgumentException(
                    "Usage: SameLoaderSmokeMain <sdk-root> <fixture-root> <report-root> [target]...");
        }
        Path sdkRoot = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path fixtureRoot = Path.of(arguments[1]).toAbsolutePath().normalize();
        Path reportRoot = Path.of(arguments[2]).toAbsolutePath().normalize();
        Path workRoot = sdkRoot.resolve(gameplayMode()
                ? "build/generated-bridge-gameplay/workspaces" : "build/connection-smoke/workspaces").normalize();
        requireInsideBuildDirectory(sdkRoot, workRoot);
        List<Target> targets = arguments.length == 3
                ? TARGETS
                : parseTargets(List.of(arguments).subList(3, arguments.length));
        prepareReportDirectory(sdkRoot, reportRoot);

        Path serverWorkspace = workRoot.resolve("server");
        Path clientWorkspace = workRoot.resolve("client");
        prepareWorkspace(fixtureRoot, serverWorkspace);
        prepareWorkspace(fixtureRoot, clientWorkspace);
        if (gameplayMode()) {
            for (Target target : targets) {
                GameplayProbeSources.write(serverWorkspace, target.id());
                GameplayProbeSources.write(clientWorkspace, target.id());
            }
        }
        Files.createDirectories(reportRoot.resolve("connection"));

        List<Result> results = new ArrayList<>();
        boolean failed = false;
        try {
            for (Target target : targets) {
                Result result = runTarget(sdkRoot, serverWorkspace, clientWorkspace, reportRoot, target);
                results.add(result);
                failed |= !result.passed();
            }
        } finally {
            writeReport(reportRoot.resolve("connection.json"), results);
        }
        if (failed) {
            throw new IllegalStateException("One or more same-loader connection tests failed; see "
                    + reportRoot.resolve("connection.json"));
        }
    }

    static void prepareReportDirectory(Path sdkRoot, Path reportRoot) throws IOException {
        Path build = sdkRoot.toAbsolutePath().normalize().resolve("build");
        Path output = reportRoot.toAbsolutePath().normalize();
        if (!output.startsWith(build) || output.equals(build)) {
            throw new IllegalArgumentException("Connection reports must remain inside the SDK build directory");
        }
        Files.createDirectories(build);
        Path ancestor = output;
        while (!Files.exists(ancestor)) ancestor = ancestor.getParent();
        if (!ancestor.toRealPath().startsWith(build.toRealPath())) {
            throw new IllegalArgumentException("Connection report directory escapes the build directory through a link");
        }
        Path namedRuns = build.resolve("reports/generated-bridge-gameplay/runs");
        if (output.getParent().equals(namedRuns)) {
            Files.createDirectories(namedRuns);
            // Reserve a named run exactly once; never overwrite an earlier attempt's evidence.
            Files.createDirectory(output);
        } else {
            Files.createDirectories(output);
        }
    }

    private static Result runTarget(Path sdkRoot, Path serverWorkspace, Path clientWorkspace,
                                    Path reportRoot, Target target) throws InterruptedException, IOException {
        int port = availablePort();
        prepareServerRunDirectory(serverWorkspace, target, port);
        prepareClientRunDirectory(clientWorkspace, target);
        Path serverLog = reportRoot.resolve("connection").resolve(target.id() + ".server.log");
        Path clientLog = reportRoot.resolve("connection").resolve(target.id() + ".client.log");
        Path serverPreparationLog = reportRoot.resolve("connection")
                .resolve(target.id() + ".server-prepare.log");
        Path clientPreparationLog = reportRoot.resolve("connection")
                .resolve(target.id() + ".client-prepare.log");
        String serverReadyMarker = "Lifecycle SERVER_STARTED on " + target.id();
        String serverCompleteMarker = "ENDERFALL_CONNECTION_SERVER_COMPLETE " + target.id();
        String clientCompleteMarker = "ENDERFALL_CONNECTION_CLIENT_COMPLETE " + target.id();
        String menuOpenMarker = "ENDERFALL_MENU_CLIENT_OPEN " + target.id();
        CountDownLatch serverReady = new CountDownLatch(1);
        CountDownLatch serverComplete = new CountDownLatch(1);
        CountDownLatch clientComplete = new CountDownLatch(1);
        CountDownLatch menuOpen = new CountDownLatch(1);
        GameplayEvidence gameplay = new GameplayEvidence(target.id());
        Map<String, CountDownLatch> serverMarkers = new LinkedHashMap<>(
                Map.of(serverReadyMarker, serverReady, serverCompleteMarker, serverComplete));
        Map<String, CountDownLatch> clientMarkers = new LinkedHashMap<>(
                Map.of(clientCompleteMarker, clientComplete, menuOpenMarker, menuOpen));
        if (gameplayMode()) {
            serverMarkers.putAll(gameplay.serverMarkers());
            clientMarkers.putAll(gameplay.clientMarkers());
        }
        AtomicReference<Throwable> serverReaderFailure = new AtomicReference<>();
        AtomicReference<Throwable> clientReaderFailure = new AtomicReference<>();
        Process server = null;
        Process client = null;
        Thread serverReader = null;
        Thread clientReader = null;
        boolean sawServerReady = false;
        boolean sawServerComplete = false;
        boolean sawClientComplete = false;
        boolean sawMenuOpen = false;
        boolean clientExitedCleanly = false;
        boolean serverExitedCleanly = false;
        int clientExitCode = -1;
        int serverExitCode = -1;
        List<String> failures = new ArrayList<>();
        Instant started = Instant.now();

        System.out.println("Starting same-loader connection " + target.id()
                + " on 127.0.0.1:" + port);
        try {
            prepareGradleWorkspace(sdkRoot, serverWorkspace, target, true, false, serverPreparationLog);
            // The workspace repository deliberately republishes one coordinated development
            // version. Loom caches dependency remaps per consumer workspace, so both sides must
            // refresh while no game process is running or a client can test an older remapped JAR.
            prepareGradleWorkspace(sdkRoot, clientWorkspace, target, true, true, clientPreparationLog);
            ProcessBuilder serverBuilder = new ProcessBuilder(gradleCommand(sdkRoot, serverWorkspace,
                    "runServer", target.id(), false))
                    .directory(sdkRoot.toFile())
                    .redirectErrorStream(true);
            serverBuilder.environment().put("ENDERFALL_CONNECTION_SMOKE", "true");
            server = serverBuilder.start();
            Process runningServer = server;
            serverReader = outputReader(runningServer, serverLog, "server " + target.id(),
                    serverMarkers,
                    serverReaderFailure);
            serverReader.start();
            sawServerReady = waitForMarkerOrExit(server, serverReady, SERVER_READY_TIMEOUT);
            if (!sawServerReady) {
                failures.add(server.isAlive()
                        ? "Server did not become ready within " + SERVER_READY_TIMEOUT.toSeconds() + " seconds"
                        : "Server exited before its ready marker");
            } else {
                ProcessBuilder clientBuilder = new ProcessBuilder(gradleCommand(sdkRoot, clientWorkspace,
                        "runClient", target.id(), false))
                        .directory(sdkRoot.toFile())
                        .redirectErrorStream(true);
                clientBuilder.environment().put("ENDERFALL_CLIENT_SMOKE", "true");
                clientBuilder.environment().put("ENDERFALL_CONNECTION_SMOKE", "true");
                clientBuilder.environment().put("ENDERFALL_TEST_SERVER_HOST", "127.0.0.1");
                clientBuilder.environment().put("ENDERFALL_TEST_SERVER_PORT", Integer.toString(port));
                client = clientBuilder.start();
                Process runningClient = client;
                clientReader = outputReader(runningClient, clientLog, "client " + target.id(),
                        clientMarkers,
                        clientReaderFailure);
                clientReader.start();
                boolean scenarioComplete = waitForMarkerOrExit(client,
                        gameplayMode() ? gameplay.clientComplete() : clientComplete, CONNECTION_TIMEOUT);
                sawClientComplete = clientComplete.getCount() == 0;
                sawServerComplete = serverComplete.getCount() == 0;
                if (!scenarioComplete) {
                    failures.add(client.isAlive()
                            ? "Client did not complete the selected connection/gameplay scenario within "
                            + CONNECTION_TIMEOUT.toSeconds() + " seconds"
                            : "Client exited before the selected scenario completion marker");
                } else {
                    clientExitedCleanly = client.waitFor(EXIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                    if (!clientExitedCleanly) {
                        failures.add("Client did not exit within " + EXIT_TIMEOUT.toSeconds()
                                + " seconds after the network marker");
                    }
                }
                if (client.isAlive()) {
                    terminate(client);
                }
                clientReader.join(TimeUnit.SECONDS.toMillis(10));
                if (!client.isAlive()) {
                    clientExitCode = client.exitValue();
                }
                if (sawClientComplete && clientExitedCleanly && clientExitCode != 0) {
                    failures.add("Client Gradle process exited with code " + clientExitCode);
                }
                sawServerComplete = serverComplete.getCount() == 0;
                if (!sawServerComplete) {
                    failures.add("Server did not observe and acknowledge the client return packet");
                }
                sawMenuOpen = menuOpen.getCount() == 0;
                if (!sawMenuOpen) {
                    failures.add("Client did not open the synchronized contract screen");
                }
            }
        } catch (IOException | RuntimeException failure) {
            failures.add("Preparation/run failed: " + failure.getMessage());
        } finally {
            if (client != null && client.isAlive()) {
                terminate(client);
            }
            if (server != null && server.isAlive()) {
                try {
                    sendStop(server);
                } catch (IOException exception) {
                    failures.add("Could not send the server stop command: " + exception.getMessage());
                }
                serverExitedCleanly = server.waitFor(EXIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                if (!serverExitedCleanly) {
                    failures.add("Server did not stop within " + EXIT_TIMEOUT.toSeconds() + " seconds");
                    terminate(server);
                }
            } else if (server != null) {
                serverExitedCleanly = true;
            }
            if (serverReader != null) {
                serverReader.join(TimeUnit.SECONDS.toMillis(10));
            }
            if (clientReader != null && clientReader.isAlive()) {
                clientReader.join(TimeUnit.SECONDS.toMillis(10));
            }
        }

        if (server != null && !server.isAlive()) {
            serverExitCode = server.exitValue();
            if (sawServerReady && serverExitedCleanly && serverExitCode != 0) {
                failures.add("Server Gradle process exited with code " + serverExitCode);
            }
        }
        appendReaderFailure(failures, "server", serverReaderFailure.get());
        appendReaderFailure(failures, "client", clientReaderFailure.get());
        String portableHash = "";
        if (gameplayMode()) {
            if (!gameplay.passed()) failures.add("Missing gameplay evidence: " + gameplay.snapshot());
            portableHash = portableSourceHash(serverWorkspace, target);
            if (!portableHash.matches("[0-9a-f]{64}")
                    || !portableHash.equals(portableSourceHash(clientWorkspace, target))) {
                failures.add("Client/server portable source hashes differ or are missing");
            }
        }
        long durationMillis = Duration.between(started, Instant.now()).toMillis();
        boolean passed = sawServerReady && sawServerComplete && sawClientComplete && sawMenuOpen
                && clientExitedCleanly && serverExitedCleanly
                && clientExitCode == 0 && serverExitCode == 0 && failures.isEmpty();
        String failure = String.join("; ", failures);
        System.out.println((passed ? "PASS " : "FAIL ") + target.id() + " in " + durationMillis + " ms"
                + (failure.isEmpty() ? "" : ": " + failure));
        return new Result(target.id(), passed, sawServerReady,
                sawServerComplete, sawClientComplete, sawMenuOpen, serverExitCode, clientExitCode, durationMillis,
                failure, relativeLog(sdkRoot, serverLog), relativeLog(sdkRoot, clientLog),
                gameplayMode() ? gameplay.snapshot() : Map.of(), portableHash);
    }

    private static void prepareWorkspace(Path fixtureRoot, Path workspace) throws IOException {
        Files.createDirectories(workspace);
        Path destinationSource = workspace.resolve("src").normalize();
        if (!destinationSource.startsWith(workspace)) {
            throw new IllegalStateException("Connection workspace source escaped its workspace: " + destinationSource);
        }
        deleteRecursively(destinationSource);
        copyDirectory(fixtureRoot.resolve("src"), destinationSource);
        Files.copy(fixtureRoot.resolve("settings.gradle.kts"), workspace.resolve("settings.gradle.kts"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.copy(fixtureRoot.resolve("build.gradle.kts"), workspace.resolve("build.gradle.kts"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static void prepareServerRunDirectory(Path workspace, Target target, int port) throws IOException {
        Path runDirectory = workspace.resolve(".gradle/enderfall-sdk/projects")
                .resolve(target.id().replace('.', '_').replace('-', '_')).resolve("run/server");
        Files.createDirectories(runDirectory);
        // NeoForge 47.1's dual-stack status pinger can resolve the local endpoint through the
        // machine's IPv6 address even when given 127.0.0.1. Let that one test server listen on
        // all local interfaces so its required status negotiation can complete; the subsequent
        // play connection still uses the explicit IPv4 loopback address.
        String serverIp = target.id().equals("1.20.1-neoforge") ? "" : "127.0.0.1";
        Files.writeString(runDirectory.resolve("eula.txt"), "eula=true\n", StandardCharsets.UTF_8);
        Files.writeString(runDirectory.resolve("server.properties"),
                "online-mode=false\nserver-ip=" + serverIp + "\nserver-port=" + port
                        + "\nmotd=EnderFall same-loader smoke\n",
                StandardCharsets.UTF_8);
    }

    private static void prepareClientRunDirectory(Path workspace, Target target) throws IOException {
        Path runDirectory = workspace.resolve(".gradle/enderfall-sdk/projects")
                .resolve(target.id().replace('.', '_').replace('-', '_')).resolve("run/client");
        Files.createDirectories(runDirectory);
        Files.writeString(runDirectory.resolve("options.txt"),
                "onboardAccessibility:false\n"
                        + "skipMultiplayerWarning:true\n"
                        + "tutorialStep:none\n"
                        + "fullscreen:false\n"
                        + "pauseOnLostFocus:false\n"
                        + "enableVsync:false\n"
                        + "maxFps:30\n",
                StandardCharsets.UTF_8);
    }

    private static List<String> gradleCommand(Path sdkRoot, Path workspace, String task, String target,
                                              boolean refreshDependencies) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path wrapper = sdkRoot.resolve(windows ? "gradlew.bat" : "gradlew");
        if (!Files.isRegularFile(wrapper)) {
            throw new IllegalStateException("Gradle wrapper not found at " + wrapper);
        }
        List<String> command = new ArrayList<>();
        if (windows) {
            command.add(System.getenv().getOrDefault("ComSpec", "cmd.exe"));
            command.add("/d");
            command.add("/c");
        }
        command.add(wrapper.toString());
        command.add("--no-daemon");
        if (refreshDependencies) {
            // Refresh before the server starts; a concurrent refresh can collide with its open mappings JAR.
            command.add("--refresh-dependencies");
        }
        command.add("--console=plain");
        command.add("-p");
        command.add(workspace.toString());
        command.add(task);
        command.add("-Penderfall.target=" + target);
        command.add("-Penderfall.fixtureTarget=" + target);
        command.add("-Penderfall.workspaceRepository=" + workspaceRepository(sdkRoot));
        if (gameplayMode()) command.add("-Penderfall.gameplayProbe=true");
        return command;
    }

    private static boolean gameplayMode() {
        return "true".equalsIgnoreCase(System.getenv("ENDERFALL_GAMEPLAY_SMOKE"));
    }

    private static Path workspaceRepository(Path sdkRoot) {
        String configured = System.getenv("ENDERFALL_WORKSPACE_REPOSITORY");
        return configured == null || configured.isBlank() ? sdkRoot.resolve("build/repository")
                : Path.of(configured).toAbsolutePath().normalize();
    }

    private static String portableSourceHash(Path workspace, Target target) throws IOException {
        Path hash = workspace.resolve(".gradle/enderfall-sdk/projects")
                .resolve(target.id().replace('.', '_').replace('-', '_'))
                .resolve("build/resources/main/META-INF/enderfall/portable-source.sha256");
        return Files.isRegularFile(hash) ? Files.readString(hash, StandardCharsets.UTF_8).trim() : "";
    }

    private static void prepareGradleWorkspace(Path sdkRoot, Path workspace, Target target,
                                               boolean refreshDependencies, boolean client, Path logFile)
            throws IOException, InterruptedException {
        if (gameplayMode()) {
            Instant deadline = Instant.now().plus(PREPARATION_TIMEOUT);
            List<String> steps = gameplayPreparationTasks(target.id(), client);
            for (int index = 0; index < steps.size(); index++) {
                runPreparationStep(sdkRoot, workspace, target, steps.get(index),
                        index == 0 && refreshDependencies, logFile, index != 0, deadline);
            }
            return;
        }
        Process process = new ProcessBuilder(gradleCommand(sdkRoot, workspace,
                "help", target.id(), refreshDependencies))
                .directory(sdkRoot.toFile())
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();
        if (!process.waitFor(PREPARATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            terminate(process);
            throw new IllegalStateException("Gradle preparation timed out for " + target.id()
                    + "; see " + logFile);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Gradle preparation exited with code " + process.exitValue()
                    + " for " + target.id() + "; see " + logFile);
        }
    }

    static List<String> gameplayPreparationTasks(String targetId, boolean client) {
        requireTarget(targetId);
        String project = ":enderfallTargets:" + targetId.replace('.', '_').replace('-', '_');
        List<String> steps = new ArrayList<>(List.of("generateData", project + ":classes"));
        if (targetId.endsWith("-fabric")) {
            if (client) steps.add(project + ":downloadAssets");
        } else {
            steps.add(client ? "prepareClient" : "prepareServer");
        }
        return List.copyOf(steps);
    }

    private static void runPreparationStep(Path sdkRoot, Path workspace, Target target, String task,
                                            boolean refresh, Path logFile, boolean append, Instant deadline)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(gradleCommand(sdkRoot, workspace, task, target.id(), refresh))
                .directory(sdkRoot.toFile()).redirectErrorStream(true)
                .redirectOutput(append ? ProcessBuilder.Redirect.appendTo(logFile.toFile())
                        : ProcessBuilder.Redirect.to(logFile.toFile())).start();
        long remaining = Math.max(0L, Duration.between(Instant.now(), deadline).toMillis());
        if (!process.waitFor(remaining, TimeUnit.MILLISECONDS)) {
            terminate(process);
            throw new IOException("Gameplay preparation timed out for " + target.id() + "; see " + logFile);
        }
        if (process.exitValue() != 0) {
            throw new IOException("Gameplay preparation failed for " + target.id() + " at " + task
                    + "; see " + logFile);
        }
    }

    private static Thread outputReader(Process process, Path logFile, String label,
                                       Map<String, CountDownLatch> markers,
                                       AtomicReference<Throwable> failure) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter log = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.write(line);
                    log.newLine();
                    log.flush();
                    recordGameplayFailure(line, failure);
                    for (Map.Entry<String, CountDownLatch> marker : markers.entrySet()) {
                        if (line.contains(marker.getKey())) {
                            marker.getValue().countDown();
                        }
                    }
                    if (line.contains("ENDERFALL_") || line.contains("BUILD SUCCESSFUL")
                            || markers.keySet().stream().anyMatch(line::contains)) {
                        System.out.println('[' + label + "] " + line);
                    }
                }
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, "enderfall-connection-" + label.replace('.', '_').replace('-', '_').replace(' ', '_'));
        thread.setDaemon(true);
        return thread;
    }

    static void recordGameplayFailure(String line, AtomicReference<Throwable> failure) {
        if (line.contains("ENDERFALL_GAMEPLAY_FAILED ")) {
            // A later completion marker must not turn an earlier assertion/observer failure
            // into a successful individual run, even before the full matrix is audited.
            failure.compareAndSet(null, new IllegalStateException("Gameplay failure marker in process output"));
        }
    }

    private static boolean waitForMarkerOrExit(Process process, CountDownLatch marker, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (process.isAlive() && System.nanoTime() < deadline) {
            if (marker.await(250, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return marker.getCount() == 0;
    }

    private static void sendStop(Process process) throws IOException {
        BufferedWriter input = new BufferedWriter(new OutputStreamWriter(
                process.getOutputStream(), StandardCharsets.UTF_8));
        input.write("stop");
        input.newLine();
        input.flush();
    }

    private static void terminate(Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            return socket.getLocalPort();
        }
    }

    private static void copyDirectory(Path source, Path destination) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                Path target = destination.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void requireInsideBuildDirectory(Path sdkRoot, Path workRoot) {
        Path buildDirectory = sdkRoot.resolve("build").toAbsolutePath().normalize();
        if (!workRoot.toAbsolutePath().normalize().startsWith(buildDirectory)) {
            throw new IllegalStateException("Connection workspaces must remain under " + buildDirectory);
        }
    }

    private static Map<String, Target> targetIndex() {
        Map<String, Target> targets = new LinkedHashMap<>();
        for (Target target : TARGETS) {
            targets.put(target.id(), target);
        }
        return Map.copyOf(targets);
    }

    private static List<Target> parseTargets(List<String> specifications) {
        List<Target> targets = new ArrayList<>();
        for (String specification : specifications) {
            Target target = requireTarget(specification);
            targets.add(target);
        }
        return List.copyOf(targets);
    }

    private static Target requireTarget(String id) {
        Target target = TARGET_BY_ID.get(id);
        if (target == null) {
            throw new IllegalArgumentException("Unsupported target " + id + "; valid targets: "
                    + String.join(", ", TARGET_BY_ID.keySet().stream().sorted().toList()));
        }
        return target;
    }

    private static void appendReaderFailure(List<String> failures, String role, Throwable problem) {
        if (problem != null) {
            failures.add("Could not capture " + role + " output: " + problem.getMessage());
        }
    }

    private static String relativeLog(Path sdkRoot, Path log) {
        return sdkRoot.relativize(log).toString().replace('\\', '/');
    }

    private static void writeReport(Path report, List<Result> results) throws IOException {
        StringBuilder json = new StringBuilder("{\n  \"schemaVersion\": 1,\n  \"results\": [\n");
        for (int index = 0; index < results.size(); index++) {
            Result result = results.get(index);
            json.append("    {\"target\":\"").append(json(result.target()))
                    .append("\",\"passed\":").append(result.passed())
                    .append(",\"serverReady\":").append(result.serverReady())
                    .append(",\"serverRoundTripComplete\":").append(result.serverRoundTripComplete())
                    .append(",\"clientRoundTripComplete\":").append(result.clientRoundTripComplete())
                    .append(",\"menuOpened\":").append(result.menuOpened())
                    .append(",\"serverExitCode\":").append(result.serverExitCode())
                    .append(",\"clientExitCode\":").append(result.clientExitCode())
                    .append(",\"durationMillis\":").append(result.durationMillis())
                    .append(",\"failure\":\"").append(json(result.failure()))
                    .append("\",\"serverLog\":\"").append(json(result.serverLog()))
                    .append("\",\"clientLog\":\"").append(json(result.clientLog()))
                    .append("\",\"portableSourceHash\":\"").append(json(result.portableSourceHash()))
                    .append("\",\"gameplayChecks\":{");
            var checks = result.gameplayChecks().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
            for (int check = 0; check < checks.size(); check++) {
                if (check > 0) json.append(',');
                json.append('"').append(json(checks.get(check).getKey())).append("\":")
                        .append(checks.get(check).getValue());
            }
            json.append("}}");
            if (index + 1 < results.size()) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ]\n}\n");
        Files.writeString(report, json, StandardCharsets.UTF_8);
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private record Target(String id) {
    }

    private record Result(String target, boolean passed,
                          boolean serverReady, boolean serverRoundTripComplete,
                          boolean clientRoundTripComplete, boolean menuOpened, int serverExitCode,
                          int clientExitCode, long durationMillis, String failure,
                          String serverLog, String clientLog, Map<String, Boolean> gameplayChecks,
                          String portableSourceHash) {
    }
}

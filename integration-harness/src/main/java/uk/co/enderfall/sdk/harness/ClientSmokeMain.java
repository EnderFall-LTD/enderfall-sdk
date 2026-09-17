package uk.co.enderfall.sdk.harness;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Starts real clients and records lifecycle-plus-tick readiness evidence. */
public final class ClientSmokeMain {
    private static final Duration PREPARATION_TIMEOUT = Duration.ofMinutes(20);
    private static final Duration READY_TIMEOUT = Duration.ofMinutes(6);
    private static final Duration EXIT_TIMEOUT = Duration.ofMinutes(1);
    private static final Set<String> SUPPORTED_TARGETS = Set.of(
            "1.20.1-fabric",
            "1.20.1-forge",
            "1.20.1-neoforge",
            "1.21.1-fabric",
            "1.21.1-neoforge",
            "1.21.4-fabric",
            "1.21.4-neoforge",
            "26.2-fabric",
            "26.2-neoforge"
    );

    private ClientSmokeMain() {
    }

    /** Runs clients sequentially to keep GPU and memory demand bounded on CI workers. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 4) {
            throw new IllegalArgumentException(
                    "Usage: ClientSmokeMain <sdk-root> <consumer-root> <report-root> <target>...");
        }
        Path sdkRoot = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path consumerRoot = Path.of(arguments[1]).toAbsolutePath().normalize();
        Path reportRoot = Path.of(arguments[2]).toAbsolutePath().normalize();
        List<String> targets = List.of(arguments).subList(3, arguments.length);
        for (String target : targets) {
            if (!SUPPORTED_TARGETS.contains(target)) {
                throw new IllegalArgumentException("Unsupported smoke target " + target
                        + "; valid targets: " + String.join(", ", SUPPORTED_TARGETS.stream().sorted().toList()));
            }
        }

        Files.createDirectories(reportRoot.resolve("client"));
        List<Result> results = new ArrayList<>();
        boolean failed = false;
        try {
            for (String target : targets) {
                Result result = runTarget(sdkRoot, consumerRoot, reportRoot, target);
                results.add(result);
                failed |= !result.passed();
            }
        } finally {
            writeReport(reportRoot.resolve("client.json"), results);
        }
        if (failed) {
            throw new IllegalStateException("One or more client smoke tests failed; see "
                    + reportRoot.resolve("client.json"));
        }
    }

    private static Result runTarget(Path sdkRoot, Path consumerRoot, Path reportRoot, String target)
            throws IOException, InterruptedException {
        Path logFile = reportRoot.resolve("client").resolve(target + ".log");
        Path preparationLog = reportRoot.resolve("client").resolve(target + "-prepare.log");
        System.out.println("Preparing client smoke test for " + target);
        prepareGradleWorkspace(sdkRoot, consumerRoot, target, preparationLog);
        List<String> command = gradleCommand(sdkRoot, consumerRoot, "runClient", target, false);
        System.out.println("Starting client smoke test for " + target);
        Instant started = Instant.now();
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(sdkRoot.toFile())
                .redirectErrorStream(true);
        builder.environment().put("ENDERFALL_CLIENT_SMOKE", "true");
        Process process = builder.start();
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<Throwable> readerFailure = new AtomicReference<>();
        String marker = "ENDERFALL_CLIENT_SMOKE_COMPLETE " + target;
        Thread outputReader = new Thread(() -> copyOutput(process, logFile, target, marker, complete, readerFailure),
                "enderfall-client-smoke-" + target.replace('.', '_').replace('-', '_'));
        outputReader.setDaemon(true);
        outputReader.start();

        boolean sawComplete = waitForMarkerOrExit(process, complete, READY_TIMEOUT);
        boolean exited = false;
        String failure = "";
        if (sawComplete) {
            exited = process.waitFor(EXIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!exited) {
                failure = "Client did not exit within " + EXIT_TIMEOUT.toSeconds()
                        + " seconds after its completion marker";
            }
        } else if (!process.isAlive()) {
            failure = "Client exited before the lifecycle/tick completion marker";
        } else {
            failure = "Client did not reach the lifecycle/tick completion marker within "
                    + READY_TIMEOUT.toSeconds() + " seconds";
        }

        if (process.isAlive()) {
            terminate(process);
        }
        outputReader.join(TimeUnit.SECONDS.toMillis(10));
        Throwable outputProblem = readerFailure.get();
        if (outputProblem != null && failure.isEmpty()) {
            failure = "Could not capture client output: " + outputProblem.getMessage();
        }
        int exitCode = process.isAlive() ? -1 : process.exitValue();
        if (sawComplete && exited && exitCode != 0 && failure.isEmpty()) {
            failure = "Client Gradle process exited with code " + exitCode;
        }
        long durationMillis = Duration.between(started, Instant.now()).toMillis();
        boolean passed = sawComplete && exited && exitCode == 0 && failure.isEmpty();
        System.out.println((passed ? "PASS " : "FAIL ") + target + " in " + durationMillis + " ms"
                + (failure.isEmpty() ? "" : ": " + failure));
        return new Result(target, passed, sawComplete, exitCode, durationMillis, failure,
                sdkRoot.relativize(logFile).toString().replace('\\', '/'));
    }

    private static List<String> gradleCommand(Path sdkRoot, Path consumerRoot, String task, String target,
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
            // Refresh before the launch timer starts; remapping can be slow on a cold cache.
            command.add("--refresh-dependencies");
        }
        command.add("--console=plain");
        command.add("-p");
        command.add(consumerRoot.toString());
        command.add(task);
        command.add("-Penderfall.target=" + target);
        command.add("-Penderfall.workspaceRepository=" + workspaceRepository(sdkRoot));
        return command;
    }

    private static void prepareGradleWorkspace(Path sdkRoot, Path consumerRoot, String target, Path logFile)
            throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(PREPARATION_TIMEOUT);
        runPreparationStep(sdkRoot, consumerRoot, "generateData", target, true, logFile, false, deadline);
        String nativePreparationTask = nativePreparationTask(target);
        if (nativePreparationTask != null) {
            runPreparationStep(
                    sdkRoot, consumerRoot, nativePreparationTask, target, false, logFile, true, deadline);
        }
    }

    private static void runPreparationStep(
            Path sdkRoot,
            Path consumerRoot,
            String task,
            String target,
            boolean refreshDependencies,
            Path logFile,
            boolean appendLog,
            Instant deadline) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                gradleCommand(sdkRoot, consumerRoot, task, target, refreshDependencies))
                .directory(sdkRoot.toFile())
                .redirectErrorStream(true);
        builder.redirectOutput(appendLog
                ? ProcessBuilder.Redirect.appendTo(logFile.toFile())
                : ProcessBuilder.Redirect.to(logFile.toFile()));
        Process process = builder.start();
        long remainingMillis = Math.max(0L, Duration.between(Instant.now(), deadline).toMillis());
        if (!process.waitFor(remainingMillis, TimeUnit.MILLISECONDS)) {
            terminate(process);
            throw new IllegalStateException("Gradle client preparation timed out for " + target
                    + "; see " + logFile);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Gradle client preparation exited with code " + process.exitValue()
                    + " for " + target + "; see " + logFile);
        }
    }

    private static String nativePreparationTask(String target) {
        if (target.equals("1.20.1-forge") || target.equals("1.20.1-neoforge")
                || target.equals("1.21.1-neoforge") || target.equals("1.21.4-neoforge")
                || target.equals("26.2-neoforge")) {
            return "prepareClient";
        }
        return null;
    }

    private static Path workspaceRepository(Path sdkRoot) {
        String configured = System.getenv("ENDERFALL_WORKSPACE_REPOSITORY");
        if (configured == null || configured.isBlank()) {
            return sdkRoot.resolve("build/repository").toAbsolutePath().normalize();
        }
        return Path.of(configured).toAbsolutePath().normalize();
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

    private static void copyOutput(Process process, Path logFile, String target, String marker,
                                   CountDownLatch complete, AtomicReference<Throwable> failure) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter log = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.write(line);
                log.newLine();
                log.flush();
                System.out.println('[' + target + "] " + line);
                if (line.contains(marker)) {
                    complete.countDown();
                }
            }
        } catch (Throwable throwable) {
            failure.compareAndSet(null, throwable);
        }
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

    private static void writeReport(Path report, List<Result> results) throws IOException {
        StringBuilder json = new StringBuilder("{\n  \"schemaVersion\": 1,\n  \"results\": [\n");
        for (int index = 0; index < results.size(); index++) {
            Result result = results.get(index);
            json.append("    {\"target\":\"").append(json(result.target()))
                    .append("\",\"passed\":").append(result.passed())
                    .append(",\"lifecycleAndTicksReady\":").append(result.lifecycleAndTicksReady())
                    .append(",\"exitCode\":").append(result.exitCode())
                    .append(",\"durationMillis\":").append(result.durationMillis())
                    .append(",\"failure\":\"").append(json(result.failure()))
                    .append("\",\"log\":\"").append(json(result.log())).append("\"}");
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

    private record Result(String target, boolean passed, boolean lifecycleAndTicksReady,
                          int exitCode, long durationMillis, String failure, String log) {
    }
}

package uk.co.enderfall.sdk.gradle.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class GenerateModMetadataTask extends DefaultTask {
    @Input
    public abstract Property<String> getModId();

    @Input
    public abstract Property<String> getModName();

    @Input
    public abstract Property<String> getModVersion();

    @Input
    public abstract Property<String> getAuthor();

    @Input
    public abstract Property<String> getLicenseName();

    @Input
    public abstract Property<String> getEntrypoint();

    @Input
    public abstract Property<String> getClientEntrypoint();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract Property<String> getLoader();

    @Input
    public abstract Property<String> getLoaderVersion();

    @Input
    public abstract Property<Integer> getJavaVersion();

    @Input
    public abstract Property<String> getSdkVersion();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws IOException {
        Path output = getOutputDirectory().get().getAsFile().toPath();
        Files.createDirectories(output.resolve("META-INF"));
        write(output.resolve("META-INF/enderfall.mod.json"), universalMetadata());
        switch (getLoader().get()) {
            case "fabric" -> write(output.resolve("fabric.mod.json"), fabricMetadata());
            case "forge" -> write(output.resolve("META-INF/mods.toml"), forgeMetadata(false));
            case "neoforge" -> {
                String file = getMinecraftVersion().get().equals("1.20.1")
                        ? "META-INF/mods.toml" : "META-INF/neoforge.mods.toml";
                write(output.resolve(file), forgeMetadata(true));
            }
            default -> throw new IllegalStateException("Unsupported loader " + getLoader().get());
        }
    }

    private String universalMetadata() {
        return "{\n"
                + "  \"schemaVersion\": 1,\n"
                + "  \"id\": " + json(getModId().get()) + ",\n"
                + "  \"name\": " + json(getModName().get()) + ",\n"
                + "  \"version\": " + json(getModVersion().get()) + ",\n"
                + "  \"entrypoint\": " + json(getEntrypoint().get()) + ",\n"
                + "  \"clientEntrypoint\": " + json(getClientEntrypoint().get()) + ",\n"
                + "  \"minecraft\": " + json(getMinecraftVersion().get()) + ",\n"
                + "  \"loader\": " + json(getLoader().get()) + ",\n"
                + "  \"enderfallSdk\": " + json(getSdkVersion().get()) + "\n"
                + "}\n";
    }

    private String fabricMetadata() {
        String author = getAuthor().get().isBlank() ? "[]" : "[" + json(getAuthor().get()) + "]";
        return "{\n"
                + "  \"schemaVersion\": 1,\n"
                + "  \"id\": " + json(getModId().get()) + ",\n"
                + "  \"version\": " + json(getModVersion().get()) + ",\n"
                + "  \"name\": " + json(getModName().get()) + ",\n"
                + "  \"authors\": " + author + ",\n"
                + "  \"license\": " + json(getLicenseName().get()) + ",\n"
                + "  \"environment\": \"*\",\n"
                + "  \"custom\": {\n"
                + "    \"enderfall\": {\n"
                + "      \"entrypoint\": " + json(getEntrypoint().get()) + ",\n"
                + "      \"clientEntrypoint\": " + json(getClientEntrypoint().get()) + "\n"
                + "    }\n"
                + "  },\n"
                + "  \"depends\": {\n"
                + "    \"fabricloader\": \">=" + escapeJson(getLoaderVersion().get()) + "\",\n"
                + "    \"minecraft\": \"=" + escapeJson(getMinecraftVersion().get()) + "\",\n"
                + "    \"java\": \">=" + getJavaVersion().get() + "\",\n"
                + "    \"enderfall_sdk\": \">=" + escapeJson(getSdkVersion().get()) + "\"\n"
                + "  }\n"
                + "}\n";
    }

    private String forgeMetadata(boolean neoForge) {
        String loaderName = "javafml";
        boolean legacyMetadata = !neoForge || getMinecraftVersion().get().equals("1.20.1");
        String dependencyType = legacyMetadata ? "mandatory=true\n" : "type=\"required\"\n";
        String minecraftRange = '[' + getMinecraftVersion().get() + ']';
        String platformModId = neoForge ? "neoforge" : "forge";
        return "modLoader=\"" + loaderName + "\"\n"
                + "loaderVersion=\"[1,)\"\n"
                + "license=" + toml(getLicenseName().get()) + "\n\n"
                + "[[mods]]\n"
                + "modId=" + toml(getModId().get()) + "\n"
                + "version=" + toml(getModVersion().get()) + "\n"
                + "displayName=" + toml(getModName().get()) + "\n"
                + "authors=" + toml(getAuthor().get()) + "\n"
                + "modproperties={enderfall_entrypoint=" + toml(getEntrypoint().get())
                + ",enderfall_client_entrypoint=" + toml(getClientEntrypoint().get()) + "}\n\n"
                + "[[dependencies." + getModId().get() + "]]\n"
                + "modId=\"enderfall_sdk\"\n"
                + dependencyType
                + "versionRange=\"[" + getSdkVersion().get() + ",)\"\n"
                + "ordering=\"NONE\"\n"
                + "side=\"BOTH\"\n\n"
                + "[[dependencies." + getModId().get() + "]]\n"
                + "modId=\"" + platformModId + "\"\n"
                + dependencyType
                + "versionRange=\"[" + getLoaderVersion().get() + ",)\"\n"
                + "ordering=\"NONE\"\n"
                + "side=\"BOTH\"\n\n"
                + "[[dependencies." + getModId().get() + "]]\n"
                + "modId=\"minecraft\"\n"
                + dependencyType
                + "versionRange=\"" + minecraftRange + "\"\n"
                + "ordering=\"NONE\"\n"
                + "side=\"BOTH\"\n";
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static String json(String value) {
        return '"' + escapeJson(value) + '"';
    }

    private static String toml(String value) {
        return json(value);
    }

    private static String escapeJson(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> result.append(character);
            }
        }
        return result.toString();
    }
}

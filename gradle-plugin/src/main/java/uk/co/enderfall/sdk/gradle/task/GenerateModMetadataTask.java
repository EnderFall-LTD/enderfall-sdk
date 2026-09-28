package uk.co.enderfall.sdk.gradle.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class GenerateModMetadataTask extends DefaultTask {
    public GenerateModMetadataTask() {
        getModDependencies().convention(List.of());
    }

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

    @Input
    public abstract Property<Boolean> getBootstrapEnabled();

    @Input
    public abstract ListProperty<String> getModDependencies();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws IOException {
        Path output = getOutputDirectory().get().getAsFile().toPath();
        Files.createDirectories(output.resolve("META-INF"));
        write(output.resolve("META-INF/enderfall.mod.json"), universalMetadata());
        write(output.resolve("pack.mcmeta"), packMetadata());
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
                + "  \"enderfallSdk\": " + json(getSdkVersion().get()) + ",\n"
                + "  \"dependencies\": " + universalDependencies() + "\n"
                + "}\n";
    }

    private String packMetadata() {
        String versionFields = switch (getMinecraftVersion().get()) {
            case "1.20.1" -> "    \"pack_format\": 15,\n";
            case "1.21.1" -> "    \"pack_format\": 34,\n"
                    + "    \"supported_formats\": [34, 48],\n";
            case "1.21.4" -> "    \"pack_format\": 46,\n"
                    + "    \"supported_formats\": [46, 61],\n";
            case "26.2" -> "    \"min_format\": 88,\n"
                    + "    \"max_format\": [107, 1],\n";
            default -> throw new IllegalStateException("Missing pack format for " + getMinecraftVersion().get());
        };
        return "{\n"
                + "  \"pack\": {\n"
                + versionFields
                + "    \"description\": " + json(getModName().get() + " resources") + "\n"
                + "  }\n"
                + "}\n";
    }

    private String fabricMetadata() {
        String author = getAuthor().get().isBlank() ? "[]" : "[" + json(getAuthor().get()) + "]";
        String entrypoints = "";
        if (getBootstrapEnabled().get()) {
            String bootstrap = GenerateBootstrapSourcesTask.generatedClassName(getModId().get(), "fabric");
            entrypoints = "  \"entrypoints\": {\n"
                    + "    \"main\": [" + json(bootstrap) + "]\n"
                    + "  },\n";
        }
        StringBuilder dependencies = new StringBuilder();
        dependencies.append("    \"fabricloader\": \">=").append(escapeJson(getLoaderVersion().get()))
                .append("\",\n")
                .append("    \"minecraft\": \"=").append(escapeJson(getMinecraftVersion().get()))
                .append("\",\n")
                .append("    \"java\": \">=").append(getJavaVersion().get()).append("\",\n")
                .append("    \"enderfall_sdk\": \">=").append(escapeJson(getSdkVersion().get())).append('"');
        List<DependencyMetadata> optional = new ArrayList<>();
        for (DependencyMetadata dependency : dependencies()) {
            if (dependency.required()) {
                dependencies.append(",\n    ").append(json(dependency.id())).append(": ")
                        .append(json(dependency.fabricVersion()));
            } else {
                optional.add(dependency);
            }
        }
        String suggests = optional.isEmpty() ? "" : ",\n  \"suggests\": {\n"
                + dependencyJson(optional) + "\n  }";
        return "{\n"
                + "  \"schemaVersion\": 1,\n"
                + "  \"id\": " + json(getModId().get()) + ",\n"
                + "  \"version\": " + json(getModVersion().get()) + ",\n"
                + "  \"name\": " + json(getModName().get()) + ",\n"
                + "  \"authors\": " + author + ",\n"
                + "  \"license\": " + json(getLicenseName().get()) + ",\n"
                + "  \"environment\": \"*\",\n"
                + entrypoints
                + "  \"custom\": {\n"
                + "    \"enderfall\": {\n"
                + "      \"entrypoint\": " + json(getEntrypoint().get()) + ",\n"
                + "      \"clientEntrypoint\": " + json(getClientEntrypoint().get()) + "\n"
                + "    }\n"
                + "  },\n"
                + "  \"depends\": {\n"
                + dependencies + "\n"
                + "  }" + suggests + "\n"
                + "}\n";
    }

    private String forgeMetadata(boolean neoForge) {
        String loaderName = "javafml";
        boolean legacyMetadata = !neoForge || getMinecraftVersion().get().equals("1.20.1");
        String dependencyType = legacyMetadata ? "mandatory=true\n" : "type=\"required\"\n";
        String minecraftRange = '[' + getMinecraftVersion().get() + ']';
        String platformModId = neoForge && !getMinecraftVersion().get().equals("1.20.1")
                ? "neoforge" : "forge";
        StringBuilder result = new StringBuilder();
        result.append("modLoader=\"").append(loaderName).append("\"\n")
                .append("loaderVersion=\"[1,)\"\n")
                .append("license=").append(toml(getLicenseName().get())).append("\n\n")
                .append("[[mods]]\n")
                .append("modId=").append(toml(getModId().get())).append('\n')
                .append("version=").append(toml(getModVersion().get())).append('\n')
                .append("displayName=").append(toml(getModName().get())).append('\n')
                .append("authors=").append(toml(getAuthor().get())).append('\n')
                .append("modproperties={enderfall_entrypoint=").append(toml(getEntrypoint().get()))
                .append(",enderfall_client_entrypoint=").append(toml(getClientEntrypoint().get()))
                .append("}\n\n")
                .append("[[dependencies.").append(getModId().get()).append("]]\n")
                .append("modId=\"enderfall_sdk\"\n")
                .append(dependencyType)
                .append("versionRange=\"[").append(getSdkVersion().get()).append(",)\"\n")
                .append("ordering=\"NONE\"\n")
                .append("side=\"BOTH\"\n\n")
                .append("[[dependencies.").append(getModId().get()).append("]]\n")
                .append("modId=\"").append(platformModId).append("\"\n")
                .append(dependencyType)
                .append("versionRange=\"[").append(getLoaderVersion().get()).append(",)\"\n")
                .append("ordering=\"NONE\"\n")
                .append("side=\"BOTH\"\n\n")
                .append("[[dependencies.").append(getModId().get()).append("]]\n")
                .append("modId=\"minecraft\"\n")
                .append(dependencyType)
                .append("versionRange=\"").append(minecraftRange).append("\"\n")
                .append("ordering=\"NONE\"\n")
                .append("side=\"BOTH\"\n");
        for (DependencyMetadata dependency : dependencies()) {
            result.append("\n[[dependencies.").append(getModId().get()).append("]]\n")
                    .append("modId=").append(toml(dependency.id())).append('\n')
                    .append(legacyMetadata ? "mandatory=" + dependency.required() + "\n"
                            : "type=\"" + (dependency.required() ? "required" : "optional") + "\"\n")
                    .append("versionRange=\"").append(dependency.forgeVersion()).append("\"\n")
                    .append("ordering=\"").append(dependency.ordering()).append("\"\n")
                    .append("side=\"").append(dependency.side()).append("\"\n");
        }
        return result.toString();
    }

    private String universalDependencies() {
        List<DependencyMetadata> dependencies = dependencies();
        if (dependencies.isEmpty()) return "[]";
        StringBuilder result = new StringBuilder("[\n");
        for (int index = 0; index < dependencies.size(); index++) {
            DependencyMetadata dependency = dependencies.get(index);
            result.append("    {\"id\":").append(json(dependency.id()))
                    .append(",\"version\":").append(json(dependency.version()))
                    .append(",\"required\":").append(dependency.required())
                    .append(",\"side\":").append(json(dependency.side().toLowerCase(java.util.Locale.ROOT)))
                    .append(",\"ordering\":").append(json(dependency.ordering().toLowerCase(java.util.Locale.ROOT)))
                    .append('}');
            if (index + 1 < dependencies.size()) result.append(',');
            result.append('\n');
        }
        return result.append("  ]").toString();
    }

    private static String dependencyJson(List<DependencyMetadata> dependencies) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < dependencies.size(); index++) {
            DependencyMetadata dependency = dependencies.get(index);
            result.append("    ").append(json(dependency.id())).append(": ")
                    .append(json(dependency.fabricVersion()));
            if (index + 1 < dependencies.size()) result.append(',');
            if (index + 1 < dependencies.size()) result.append('\n');
        }
        return result.toString();
    }

    private List<DependencyMetadata> dependencies() {
        return getModDependencies().getOrElse(List.of()).stream()
                .map(DependencyMetadata::parse).toList();
    }

    private record DependencyMetadata(String id, String version, boolean required, String side,
                                      String ordering, boolean exact) {
        static DependencyMetadata parse(String descriptor) {
            String[] fields = descriptor.split("\\|", -1);
            if (fields.length != 6) throw new IllegalStateException("Invalid mod dependency descriptor");
            return new DependencyMetadata(fields[0], fields[1], fields[2].equals("REQUIRED"),
                    fields[3], fields[4], Boolean.parseBoolean(fields[5]));
        }

        String fabricVersion() { return (exact ? "=" : ">=") + version; }

        String forgeVersion() { return exact ? "[" + version + "]" : "[" + version + ",)"; }
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

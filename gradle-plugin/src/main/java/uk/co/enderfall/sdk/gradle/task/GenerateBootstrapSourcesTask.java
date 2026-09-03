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

/** Generates the tiny target-native loader entrypoint that delegates to the installed SDK runtime. */
@CacheableTask
public abstract class GenerateBootstrapSourcesTask extends DefaultTask {
    @Input
    public abstract Property<String> getModId();

    @Input
    public abstract Property<String> getEntrypoint();

    @Input
    public abstract Property<String> getClientEntrypoint();

    @Input
    public abstract Property<String> getLoader();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws IOException {
        String packageName = generatedPackage(getModId().get());
        String className = generatedSimpleClass(getLoader().get());
        String source = switch (getLoader().get()) {
            case "fabric" -> fabricSource(packageName, className);
            case "neoforge" -> getMinecraftVersion().get().equals("1.20.1")
                    ? legacyForgeSource(packageName, className) : neoForgeSource(packageName, className);
            case "forge" -> legacyForgeSource(packageName, className);
            default -> throw new IllegalStateException("Unsupported loader " + getLoader().get());
        };
        Path output = getOutputDirectory().get().getAsFile().toPath()
                .resolve(packageName.replace('.', '/')).resolve(className + ".java");
        Files.createDirectories(output.getParent());
        Files.writeString(output, source, StandardCharsets.UTF_8);
    }

    public static String generatedClassName(String modId, String loader) {
        return generatedPackage(modId) + '.' + generatedSimpleClass(loader);
    }

    private String fabricSource(String packageName, String className) {
        return "package " + packageName + ";\n\n"
                + "public final class " + className + " implements net.fabricmc.api.ModInitializer {\n"
                + "    @Override\n"
                + "    public void onInitialize() {\n"
                + "        uk.co.enderfall.sdk.runtime.fabric.v1_21_4.FabricConsumerBootstrap.initialize(\n"
                + "                " + quote(getModId().get()) + ",\n"
                + "                " + quote(getEntrypoint().get()) + ",\n"
                + "                " + quote(getClientEntrypoint().get()) + ");\n"
                + "    }\n"
                + "}\n";
    }

    private String neoForgeSource(String packageName, String className) {
        return "package " + packageName + ";\n\n"
                + "@net.neoforged.fml.common.Mod(" + quote(getModId().get()) + ")\n"
                + "public final class " + className + " {\n"
                + "    public " + className + "(net.neoforged.bus.api.IEventBus modBus) {\n"
                + "        uk.co.enderfall.sdk.runtime.neoforge.v1_21_4.NeoForgeConsumerBootstrap.initialize(\n"
                + "                " + quote(getModId().get()) + ",\n"
                + "                " + quote(getEntrypoint().get()) + ",\n"
                + "                " + quote(getClientEntrypoint().get()) + ",\n"
                + "                modBus,\n"
                + "                " + className + ".class.getClassLoader());\n"
                + "    }\n"
                + "}\n";
    }

    private String legacyForgeSource(String packageName, String className) {
        return "package " + packageName + ";\n\n"
                + "@net.minecraftforge.fml.common.Mod(" + quote(getModId().get()) + ")\n"
                + "public final class " + className + " {\n"
                + "    public " + className + "() {\n"
                + "        uk.co.enderfall.sdk.runtime.forge.v1_20_1.LegacyForgeConsumerBootstrap.initialize(\n"
                + "                " + quote(getModId().get()) + ",\n"
                + "                " + quote(getEntrypoint().get()) + ",\n"
                + "                " + quote(getClientEntrypoint().get()) + ",\n"
                + "                " + className + ".class.getClassLoader());\n"
                + "    }\n"
                + "}\n";
    }

    private static String generatedPackage(String modId) {
        return "uk.co.enderfall.sdk.generated." + modId;
    }

    private static String generatedSimpleClass(String loader) {
        return switch (loader) {
            case "fabric" -> "EnderfallFabricBootstrap";
            case "forge" -> "EnderfallForgeBootstrap";
            case "neoforge" -> "EnderfallNeoForgeBootstrap";
            default -> throw new IllegalArgumentException("Unsupported loader " + loader);
        };
    }

    private static String quote(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return '"' + escaped + '"';
    }
}

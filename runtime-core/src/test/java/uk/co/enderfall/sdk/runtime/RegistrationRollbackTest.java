package uk.co.enderfall.sdk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.blockentity.BlockLocation;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeTypeRef;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import uk.co.enderfall.sdk.api.ui.StorageContainerRef;
import uk.co.enderfall.sdk.api.ui.StorageContainerSpec;
import uk.co.enderfall.sdk.api.ui.WorkbenchRef;
import uk.co.enderfall.sdk.api.ui.WorkbenchSpec;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.platform.Loader;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;
import uk.co.enderfall.sdk.api.registry.ItemSpec;

/**
 * Registration IDs are permanent for the life of a process. Every registrar therefore has to
 * release its dedup entry when native registration fails, otherwise one transient loader failure
 * silently makes that ID unregisterable and reports a misleading duplicate on every retry.
 */
class RegistrationRollbackTest {
    private static final WorkbenchRecipeTypeRef RECIPE =
            new WorkbenchRecipeTypeRef(ResourceId.of("test_mod", "recipe"), 3);
    private static final BlockEntitySpec CABINET =
            BlockEntitySpec.builder(new BlockRef(ResourceId.of("test_mod", "cabinet"))).inventorySlots(27).build();

    @TempDir
    Path temporaryDirectory;

    @Test
    void aFailedItemRegistrationReleasesItsId() {
        FailingAdapter adapter = new FailingAdapter(temporaryDirectory);
        RuntimeModContext context = new RuntimeModContext("test_mod", adapter);
        ResourceId id = ResourceId.of("test_mod", "ingot");

        adapter.fail = true;
        assertThrows(IllegalStateException.class,
                () -> context.items().register(id, ItemSpec.builder().build()));

        adapter.fail = false;
        assertNotNull(context.items().register(id, ItemSpec.builder().build()));
    }

    @Test
    void aFailedBlockRegistrationReleasesItsIdAndSpec() {
        FailingAdapter adapter = new FailingAdapter(temporaryDirectory);
        RuntimeModContext context = new RuntimeModContext("test_mod", adapter);
        ResourceId id = ResourceId.of("test_mod", "machine");

        adapter.failBlocks = true;
        assertThrows(IllegalStateException.class,
                () -> context.blocks().register(id, BlockSpec.builder().build()));

        adapter.failBlocks = false;
        assertNotNull(context.blocks().register(id, BlockSpec.builder().build()));
    }

    @Test
    void aRejectedBlockWithItemDoesNotBurnTheItemId() {
        FailingAdapter adapter = new FailingAdapter(temporaryDirectory);
        RuntimeModContext context = new RuntimeModContext("test_mod", adapter);

        // A duplicate block is rejected after the item ID has already been reserved.
        context.blocks().register(ResourceId.of("test_mod", "machine"), BlockSpec.builder().build());
        assertThrows(IllegalStateException.class, () -> context.blocks().registerWithItem(
                "machine", BlockSpec.builder().build(), ItemSpec.builder().build()));

        // The reserved item ID must still be usable, rather than reporting "Duplicate item ID".
        assertNotNull(context.items().register(ResourceId.of("test_mod", "machine"),
                ItemSpec.builder().build()));
    }

    @Test
    void aFailedBlockWithItemRegistrationReleasesTheItemId() {
        FailingAdapter adapter = new FailingAdapter(temporaryDirectory);
        RuntimeModContext context = new RuntimeModContext("test_mod", adapter);
        adapter.failBlocks = true;

        assertThrows(IllegalStateException.class, () -> context.blocks().registerWithItem(
                "machine", BlockSpec.builder().build(), ItemSpec.builder().build()));

        adapter.failBlocks = false;
        assertNotNull(context.items().register(ResourceId.of("test_mod", "machine"),
                ItemSpec.builder().build()));
    }

    @Test
    void aFailedCreativeTabRegistrationReleasesItsId() {
        FailingAdapter adapter = new FailingAdapter(temporaryDirectory);
        RuntimeModContext context = new RuntimeModContext("test_mod", adapter);
        ResourceId id = ResourceId.of("test_mod", "tab");

        adapter.fail = true;
        assertThrows(IllegalStateException.class,
                () -> context.creativeTabs().register(id, CreativeTabSpec.builder("test_mod.tab", new uk.co.enderfall.sdk.api.registry.ItemRef(ResourceId.of("test_mod", "ingot"))).build()));

        adapter.fail = false;
        assertNotNull(context.creativeTabs().register(id, CreativeTabSpec.builder("test_mod.tab", new uk.co.enderfall.sdk.api.registry.ItemRef(ResourceId.of("test_mod", "ingot"))).build()));
    }

    @Test
    void aFailedCommandRegistrationReleasesItsName() {
        FailingAdapter adapter = new FailingAdapter(temporaryDirectory);
        RuntimeModContext context = new RuntimeModContext("test_mod", adapter);

        adapter.fail = true;
        assertThrows(IllegalStateException.class,
                () -> context.commands().register(CommandSpec.builder("reload").executes(ignored -> 0).build()));

        adapter.fail = false;
        context.commands().register(CommandSpec.builder("reload").executes(ignored -> 0).build());
        assertEquals(1, adapter.registeredCommands.size());
    }

    @Test
    void aFailedWorkbenchRegistrationReleasesItsId() {
        FailingAdapter adapter = new FailingAdapter(temporaryDirectory);
        RuntimeModContext context = new RuntimeModContext("test_mod", adapter);
        WorkbenchSpec spec = WorkbenchSpec.builder("Bench", RECIPE).build();

        // A native rejection, such as a recipe type the loader has not registered yet.
        adapter.fail = true;
        assertThrows(IllegalStateException.class, () -> context.workbenches().register("bench", spec, craft -> { }));
        assertThrows(IllegalArgumentException.class, () -> context.workbenches().open(UUID.randomUUID(),
                new WorkbenchRef(ResourceId.of("test_mod", "bench"))));

        adapter.fail = false;
        WorkbenchRef bench = context.workbenches().register("bench", spec, craft -> { });
        context.workbenches().open(UUID.randomUUID(), bench);
        assertEquals(List.of(bench.id()), adapter.openedWorkbenches);
    }

    @Test
    void aWorkbenchCannotBeOpenedBeforeItsNativeRegistrationCompletes() {
        FailingAdapter adapter = new FailingAdapter(temporaryDirectory);
        RuntimeModContext context = new RuntimeModContext("test_mod", adapter);
        // Native registration observes the manager mid-registration, as another thread could.
        adapter.duringWorkbenchRegistration = definition -> assertThrows(IllegalArgumentException.class,
                () -> context.workbenches().open(UUID.randomUUID(), definition.reference()));

        context.workbenches().register("bench", WorkbenchSpec.builder("Bench", RECIPE).build(), craft -> { });

        assertEquals(List.of(), adapter.openedWorkbenches);
    }

    @Test
    void aFailedStorageContainerRegistrationReleasesItsId() {
        FailingAdapter adapter = new FailingAdapter(temporaryDirectory);
        RuntimeModContext context = new RuntimeModContext("test_mod", adapter);
        StorageContainerSpec spec = StorageContainerSpec.builder("Cabinet", CABINET).build();
        BlockLocation location = new BlockLocation(ResourceId.of("minecraft", "overworld"), 0, 64, 0);

        adapter.fail = true;
        assertThrows(IllegalStateException.class, () -> context.containers().register("cabinet", spec));
        assertThrows(IllegalArgumentException.class, () -> context.containers().openAt(UUID.randomUUID(),
                new StorageContainerRef(ResourceId.of("test_mod", "cabinet")), location));

        adapter.fail = false;
        assertNotNull(context.containers().register("cabinet", spec));
    }

    private static final class FailingAdapter implements PlatformAdapter {
        private final Path directory;
        private final List<CommandSpec> registeredCommands = new ArrayList<>();
        private final List<ResourceId> openedWorkbenches = new ArrayList<>();
        private Consumer<PortableWorkbenchDefinition> duringWorkbenchRegistration = definition -> { };
        private boolean fail;
        private boolean failBlocks;

        private FailingAdapter(Path directory) {
            this.directory = directory;
        }

        @Override public PlatformInfo platformInfo() { return new TestPlatformInfo(); }
        @Override public CapabilitySet capabilities() { return new ImmutableCapabilitySet(java.util.Set.of()); }
        @Override public Path commonConfigDirectory() { return directory; }
        @Override public Path serverConfigDirectory() { return directory.resolve("serverconfig"); }
        @Override public void registerItem(ResourceId id, ItemSpec spec) {
            if (fail) throw new IllegalStateException("native item registration failed");
        }
        @Override public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) {
            if (failBlocks) throw new IllegalStateException("native block registration failed");
        }
        @Override public void registerCreativeTab(ResourceId id, CreativeTabSpec spec) {
            if (fail) throw new IllegalStateException("native creative tab registration failed");
        }
        @Override public void registerCommand(CommandSpec command) {
            if (fail) throw new IllegalStateException("native command registration failed");
            registeredCommands.add(command);
        }
        @Override public void registerWorkbench(PortableWorkbenchDefinition definition) {
            duringWorkbenchRegistration.accept(definition);
            if (fail) throw new IllegalStateException("native workbench registration failed");
        }
        @Override public void openWorkbench(UUID playerId, PortableWorkbenchDefinition definition) {
            openedWorkbenches.add(definition.reference().id());
        }
        @Override public boolean supportsStorageContainers() { return true; }
        @Override public void registerStorageContainer(PortableStorageContainerDefinition definition) {
            if (fail) throw new IllegalStateException("native storage container registration failed");
        }
        @Override public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                              PayloadReceiver receiver) { }
        @Override public void sendToServer(ResourceId id, byte[] payload) { }
        @Override public void sendToPlayer(UUID playerId, ResourceId id, byte[] payload) { }
        @Override public void sendToAll(ResourceId id, byte[] payload) { }
    }

    private record TestPlatformInfo() implements PlatformInfo {
        @Override public Loader loader() { return Loader.FABRIC; }
        @Override public MinecraftVersion minecraftVersion() { return new MinecraftVersion("1.21.4"); }
        @Override public Environment environment() { return Environment.DEDICATED_SERVER; }
        @Override public boolean isModLoaded(String modId) { return false; }
        @Override public Optional<String> modVersion(String modId) { return Optional.empty(); }
    }
}
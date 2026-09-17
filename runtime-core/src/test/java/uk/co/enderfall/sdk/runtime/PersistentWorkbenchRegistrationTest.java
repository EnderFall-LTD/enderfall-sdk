package uk.co.enderfall.sdk.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.blockentity.BlockLocation;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeTypeRef;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.ItemSpec;
import uk.co.enderfall.sdk.api.ui.WorkbenchSpec;

class PersistentWorkbenchRegistrationTest {
    private static final String MOD = "test_mod";
    private static final String TARGET = "1.21.4-fabric";
    private static final ResourceId ID = ResourceId.of(MOD, "machine");
    private static final BlockEntitySpec STORAGE = BlockEntitySpec.builder(new BlockRef(ID)).inventorySlots(3).build();
    private static final WorkbenchRecipeTypeRef RECIPE = new WorkbenchRecipeTypeRef(ResourceId.of(MOD, "recipe"), 3);

    private static final class Fixture {
        final List<String> calls = new ArrayList<>();
        final RegistrationGate gate = new RegistrationGate();
        final DefaultItemRegistrar items;
        final DefaultBlockRegistrar blocks;
        final DefaultWorkbenchManager menus;
        Object[] lastArguments;
        Fixture(boolean supported) {
            this(supported, false);
        }
        Fixture(boolean supported, boolean shapes) {
            PlatformAdapter adapter = (PlatformAdapter) Proxy.newProxyInstance(PlatformAdapter.class.getClassLoader(),
                    new Class<?>[] {PlatformAdapter.class}, (proxy, method, args) -> {
                        if (method.getName().equals("supportsPersistentWorkbenches")) return supported;
                        if (method.getName().equals("supportsTimedWorkbenches")) return false;
                        if (method.getName().equals("supportsBlockShapes")) return shapes;
                        if (method.getName().equals("supportsHorizontalFacing")) return shapes;
                        if (method.getName().equals("supportsSixWayFacing")) return shapes;
                        if (method.getName().equals("supportsBlockStates")) return shapes;
                        calls.add(method.getName());
                        lastArguments = args;
                        return null;
                    });
            items = new DefaultItemRegistrar(MOD, TARGET, adapter, gate);
            blocks = new DefaultBlockRegistrar(MOD, TARGET, adapter, gate, items);
            menus = new DefaultWorkbenchManager(MOD, TARGET, adapter, gate);
        }
    }

    @Test void unsupportedCallsDoNotReserveBlockItemOrMenuIds() {
        var f = new Fixture(false);
        var failure = assertThrows(UnsupportedOperationException.class, () -> f.blocks.registerPersistentWithItem(
                "machine", BlockSpec.builder().build(), ItemSpec.builder().build(), STORAGE));
        assertTrue(failure.getMessage().contains(MOD));
        assertTrue(failure.getMessage().contains(TARGET));
        assertThrows(UnsupportedOperationException.class, () -> f.menus.register("menu",
                WorkbenchSpec.builder("Machine", RECIPE).persistent(STORAGE).build(), craft -> { }));
        assertTrue(f.calls.isEmpty());
        f.blocks.registerWithItem("machine", BlockSpec.builder().build(), ItemSpec.builder().build());
        f.menus.register("menu", WorkbenchSpec.builder("Temporary", RECIPE).build(), craft -> { });
        assertEquals(List.of("registerBlock", "registerWorkbench"), f.calls);
    }

    @Test void unsupportedShapesFailBeforeItemReservationAndSupportedShapesReachBothRegistrations() {
        var spec = BlockSpec.builder().shape(uk.co.enderfall.sdk.api.block.BlockShape.box(0, 0, 0, 16, 8, 16)).build();
        var unsupported = new Fixture(true);
        var failure = assertThrows(UnsupportedOperationException.class, () -> unsupported.blocks.registerWithItem("machine", spec, ItemSpec.builder().build()));
        assertTrue(failure.getMessage().contains(TARGET));
        assertTrue(failure.getMessage().contains(MOD));
        assertTrue(unsupported.calls.isEmpty());
        unsupported.blocks.registerWithItem("machine", BlockSpec.builder().build(), ItemSpec.builder().build());
        var ordinary = new Fixture(true, true);
        ordinary.blocks.registerWithItem("machine", spec, ItemSpec.builder().build());
        assertSame(spec, ordinary.lastArguments[1]);
        var persistent = new Fixture(true, true);
        persistent.blocks.registerPersistentWithItem("machine", spec, ItemSpec.builder().build(), STORAGE);
        assertEquals(List.of("registerPersistentBlock"), persistent.calls);
        assertSame(spec, persistent.lastArguments[1]);
    }

    @Test void horizontalFacingIsRejectedOrForwardedWithoutSilentlyDroppingIt() {
        var spec = BlockSpec.builder().horizontalFacing().build();
        var unsupported = new Fixture(true);
        assertThrows(UnsupportedOperationException.class, () -> unsupported.blocks.registerWithItem("machine", spec, ItemSpec.builder().build()));
        assertTrue(unsupported.calls.isEmpty());
        unsupported.blocks.registerWithItem("machine", BlockSpec.builder().build(), ItemSpec.builder().build());
        var supported = new Fixture(true, true);
        supported.blocks.registerPersistentWithItem("machine", spec, ItemSpec.builder().build(), STORAGE);
        assertSame(spec, supported.lastArguments[1]);
    }

    @Test void sixWayFacingIsCheckedIndependentlyBeforeRegistration() {
        var spec = BlockSpec.builder().sixWayFacing().build();
        var unsupported = new Fixture(true);
        var error = assertThrows(UnsupportedOperationException.class, () -> unsupported.blocks.registerPersistentWithItem("machine", spec, ItemSpec.builder().build(), STORAGE));
        assertTrue(error.getMessage().contains("Six-way"));
        assertTrue(unsupported.calls.isEmpty());
        unsupported.blocks.registerWithItem("machine", BlockSpec.builder().build(), ItemSpec.builder().build());
        var supported = new Fixture(true, true);
        supported.blocks.registerPersistentWithItem("machine", spec, ItemSpec.builder().build(), STORAGE);
        assertSame(spec, supported.lastArguments[1]);
    }

    @Test void customStateSupportIsCheckedBeforeReservingAnItemId() {
        var schema = uk.co.enderfall.sdk.api.block.BlockStateDefinition.builder()
                .property(uk.co.enderfall.sdk.api.block.BlockProperty.bool("open"), true).build();
        var spec = BlockSpec.builder().states(schema).build();
        var unsupported = new Fixture(true);
        var error = assertThrows(UnsupportedOperationException.class, () -> unsupported.blocks.registerPersistentWithItem("machine", spec, ItemSpec.builder().build(), STORAGE));
        assertTrue(error.getMessage().contains(MOD));
        assertTrue(error.getMessage().contains(TARGET));
        assertTrue(unsupported.calls.isEmpty());
        unsupported.blocks.registerWithItem("machine", BlockSpec.builder().build(), ItemSpec.builder().build());
        var supported = new Fixture(true, true);
        supported.blocks.registerPersistentWithItem("machine", spec, ItemSpec.builder().build(), STORAGE);
        assertSame(spec, supported.lastArguments[1]);
    }

    @Test void timedSupportIsCheckedSeparatelyBeforeReservingMenuId() {
        var f = new Fixture(true);
        var timedStorage = BlockEntitySpec.builder(new BlockRef(ID)).inventorySlots(4).build();
        var failure = assertThrows(UnsupportedOperationException.class, () -> f.menus.register("menu",
                WorkbenchSpec.builder("Machine", RECIPE).persistentTimed(timedStorage, 100).build(), craft -> { }));
        assertTrue(failure.getMessage().contains(MOD));
        assertTrue(failure.getMessage().contains(TARGET));
        assertTrue(f.calls.isEmpty());
        f.menus.register("menu", WorkbenchSpec.builder("Instant", RECIPE).persistent(STORAGE).build(), craft -> { });
        assertEquals(List.of("registerPersistentWorkbench"), f.calls);
    }

    @Test void routesPersistentRegistrationAndExactLocationWithoutTemporaryFallback() {
        var f = new Fixture(true);
        assertEquals(new BlockRef(ID), f.blocks.registerPersistentWithItem("machine",
                BlockSpec.builder().build(), ItemSpec.builder().build(), STORAGE));
        var menu = f.menus.register("menu", WorkbenchSpec.builder("Machine", RECIPE).persistent(STORAGE).build(), craft -> { });
        var location = new BlockLocation(ResourceId.of("minecraft", "overworld"), -12, 64, 37);
        UUID player = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> f.menus.open(player, menu));
        f.menus.openAt(player, menu, location);
        assertEquals(List.of("registerPersistentBlock", "registerPersistentWorkbench", "openPersistentWorkbench"), f.calls);
        assertSame(location, f.lastArguments[2]);
        assertEquals(player, f.lastArguments[0]);
    }

    @Test void rejectsWrongOwnershipAndSlotCountsBeforeAnyNativeCall() {
        var f = new Fixture(true);
        assertThrows(IllegalArgumentException.class, () -> f.blocks.registerPersistentWithItem("different",
                BlockSpec.builder().build(), ItemSpec.builder().build(), STORAGE));
        assertThrows(IllegalArgumentException.class, () -> WorkbenchSpec.builder("Machine", RECIPE).persistent(
                BlockEntitySpec.builder(new BlockRef(ID)).inventorySlots(4).build()));
        var foreign = BlockEntitySpec.builder(new BlockRef(ResourceId.of("other_mod", "machine"))).inventorySlots(3).build();
        assertThrows(IllegalArgumentException.class, () -> f.menus.register("menu",
                WorkbenchSpec.builder("Machine", RECIPE).persistent(foreign).build(), craft -> { }));
        assertTrue(f.calls.isEmpty());
    }

    @Test void persistentRegistrationSharesNormalDuplicateAndFreezeBoundaries() {
        var f = new Fixture(true);
        f.blocks.registerPersistentWithItem("machine", BlockSpec.builder().build(), ItemSpec.builder().build(), STORAGE);
        assertThrows(IllegalStateException.class, () -> f.items.register("machine", ItemSpec.builder().build()));
        assertThrows(IllegalStateException.class, () -> f.blocks.register("machine", BlockSpec.builder().build()));
        f.gate.freeze();
        assertThrows(IllegalStateException.class, () -> f.menus.register("menu",
                WorkbenchSpec.builder("Machine", RECIPE).persistent(STORAGE).build(), craft -> { }));
        assertEquals(List.of("registerPersistentBlock"), f.calls);
    }

    @Test void temporaryMenusRejectLocationOpeningButKeepExistingOpenPath() {
        var f = new Fixture(false);
        var spec = WorkbenchSpec.builder("Temporary", RECIPE).build();
        assertTrue(spec.storage().isEmpty());
        var menu = f.menus.register("menu", spec, craft -> { });
        UUID player = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> f.menus.openAt(player, menu,
                new BlockLocation(ResourceId.of("minecraft", "overworld"), 0, 64, 0)));
        f.menus.open(player, menu);
        assertEquals(List.of("registerWorkbench", "openWorkbench"), f.calls);
    }
}

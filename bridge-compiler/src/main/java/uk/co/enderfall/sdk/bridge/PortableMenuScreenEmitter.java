package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.MenuAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** One portable label/button screen implementation with typed native rendering policies. */
final class PortableMenuScreenEmitter {
    private PortableMenuScreenEmitter() { }

    private enum Rendering {
        IMMEDIATE("GuiGraphics", "Target-native", "render", "drawCenteredString", "drawString", "", "screen"),
        EXTRACTED("GuiGraphicsExtractor", "Minecraft 26.2", "extractRenderState", "centeredText", "text", "gui.", "gui.screen()");

        final String graphics, description, method, centeredText, text, gui, screen;
        Rendering(String graphics, String description, String method, String centeredText, String text, String gui, String screen) {
            this.graphics = graphics; this.description = description; this.method = method;
            this.centeredText = centeredText; this.text = text; this.gui = gui; this.screen = screen;
        }
    }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths) throws BridgeGenerationException {
        return emitIfPresent(target, paths, false);
    }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths, boolean gauges) throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) {
            throw new BridgeGenerationException("Unreviewed portable screen target " + target.id());
        }
        boolean fabric = target.loaderAbi() == LoaderAbi.FABRIC;
        boolean legacyFml = target.loaderAbi() == LoaderAbi.LEGACY_FML;
        Rendering rendering = target.menuAbi() == MenuAbi.V26_2 ? Rendering.EXTRACTED : Rendering.IMMEDIATE;
        boolean backgroundInsideRender = target.menuAbi() == MenuAbi.V1_21_1
                || target.menuAbi() == MenuAbi.V1_21_4;
        String multilineCreation = rendering == Rendering.EXTRACTED
                ? "return MultiLineEditBox.builder().setX(x).setY(y).setPlaceholder(placeholder)\n"
                        + "                .build(font, definition.width(), definition.height(), placeholder);"
                : "return new MultiLineEditBox(font, x, y, definition.width(), definition.height(),\n"
                        + "                placeholder, placeholder);";
        String canonicalPrefix = fabric ? "Fabric" : "NeoForge";
        String mouseScrolled = target.menuAbi() == MenuAbi.V1_20_1
                ? MOUSE_SCROLLED_1201
                : MOUSE_SCROLLED_MODERN;
        String selectionRendering = selectionRendering(target);
        String root = "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge") + "/v1_21_4/";
        String canonical = root + canonicalPrefix + "PortableMenuScreen.java";
        if (!paths.contains(canonical)) return List.of();
        String outputRoot = legacyFml ? "uk/co/enderfall/sdk/runtime/forge/v1_20_1/" : root;
        String prefix = legacyFml ? "LegacyForge" : canonicalPrefix;
        String filename = prefix + (rendering == Rendering.EXTRACTED ? "26" : "") + "PortableMenuScreen.java";
        String content = SCREEN.formatted(outputRoot.substring(0, outputRoot.length() - 1).replace('/', '.'),
                prefix + "PortableMenuScreen", rendering.graphics, rendering.description, rendering.method,
                rendering.centeredText, rendering.text, rendering.gui, rendering.screen, gauges ? GAUGES : "",
                backgroundInsideRender ? "super.renderBackground(graphics, mouseX, mouseY, partialTick);\n        " : "",
                backgroundInsideRender ? BACKGROUND_OVERRIDE : "", multilineCreation, mouseScrolled,
                selectionRendering);
        return List.of(new RuntimeSource(canonical, outputRoot + filename, content.getBytes(StandardCharsets.UTF_8)));
    }

    private static String selectionRendering(TargetSpec target) {
        boolean extracted = target.menuAbi() == MenuAbi.V26_2;
        boolean legacy = target.menuAbi() == MenuAbi.V1_20_1;
        boolean legacyFml = target.loaderAbi() == LoaderAbi.LEGACY_FML;
        boolean valueLookup = target.menuAbi() == MenuAbi.V1_21_4 || extracted;
        String id = extracted
                ? "net.minecraft.resources.Identifier.parse(icon.id().toString())"
                : legacy
                        ? "net.minecraft.resources.ResourceLocation.tryParse(icon.id().toString())"
                        : "net.minecraft.resources.ResourceLocation.parse(icon.id().toString())";
        String registry = legacyFml
                ? "net.minecraftforge.registries.ForgeRegistries.ITEMS"
                : "net.minecraft.core.registries.BuiltInRegistries.ITEM";
        String lookup = legacyFml || valueLookup
                ? registry + ".getValue(id)"
                : registry + ".get(id)";
        String draw = extracted
                ? "graphics.item(stack, iconX, iconY);\n"
                        + "                graphics.itemDecorations(font, stack, iconX, iconY);"
                : "graphics.renderItem(stack, iconX, iconY);\n"
                        + "                graphics.renderItemDecorations(font, stack, iconX, iconY);";
        String tooltip = extracted
                ? "graphics.setTooltipForNextFrame(font, Component.literal(row.tooltip()), mouseX, mouseY);"
                : "graphics.renderTooltip(font, Component.literal(row.tooltip()), mouseX, mouseY);";
        return """
                private void renderSelectionVisuals(%s graphics, int left, int top,
                        int mouseX, int mouseY) {
                    var rows = view.spec().selectionVisuals(view.state());
                    for (var row : rows) {
                        if (row.icon().isEmpty()) continue;
                        var icon = row.icon().orElseThrow();
                        var id = %s;
                        if (id == null || !%s.containsKey(id)) continue;
                        var stack = new net.minecraft.world.item.ItemStack(%s, row.count());
                        int iconX = left + row.x() + 2;
                        int iconY = top + row.y() + Math.max(0, (row.height() - 16) / 2);
                        %s
                    }
                    for (var row : rows) {
                        int rowX = left + row.x();
                        int rowY = top + row.y();
                        if (!row.tooltip().isBlank() && mouseX >= rowX && mouseX < rowX + row.width()
                                && mouseY >= rowY && mouseY < rowY + row.height()) {
                            %s
                            break;
                        }
                    }
                }
            """.formatted(extracted ? "GuiGraphicsExtractor" : "GuiGraphics", id, registry, lookup, draw, tooltip);
    }

    private static final String GAUGES = """
            for (var gauge : view.spec().gauges()) {
                        int gx = left + gauge.x();
                        int gy = top + gauge.y();
                        graphics.fill(gx, gy, gx + gauge.width(), gy + gauge.height(), 0xFF90879B);
                        graphics.fill(gx + 1, gy + 1, gx + gauge.width() - 1, gy + gauge.height() - 1, 0xFF17131D);
                        int pixels = gauge.filledPixels(view.state());
                        if (pixels > 0) graphics.fill(gx + 1, gy + gauge.height() - 1 - pixels,
                                gx + gauge.width() - 1, gy + gauge.height() - 1, gauge.color());
                    }
            """ + "                    ";

    private static final String BACKGROUND_OVERRIDE = """
                // Screen.render invokes this after our foreground. The background was already drawn
                // first, so do not blur the panel again during widget rendering.
                @Override
                public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                }
            """;

    private static final String MOUSE_SCROLLED_1201 = """
                @Override
                public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
                    if (submitScroll(mouseX, mouseY, amount)) return true;
                    return super.mouseScrolled(mouseX, mouseY, amount);
                }
            """;

    private static final String MOUSE_SCROLLED_MODERN = """
                @Override
                public boolean mouseScrolled(double mouseX, double mouseY,
                        double horizontalAmount, double verticalAmount) {
                    if (submitScroll(mouseX, mouseY, verticalAmount)) return true;
                    return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
                }
            """;

    // Manifest-listed feature selection, not a rewrite of canonical Java source.
    private static final String SCREEN = """
            package %1$s;
            
            import java.util.ArrayList;
            import java.util.LinkedHashMap;
            import java.util.List;
            import java.util.Map;
            import java.util.Set;
            import java.util.function.Consumer;
            import net.minecraft.client.Minecraft;
            import net.minecraft.client.gui.%3$s;
            import net.minecraft.client.gui.components.Button;
            import net.minecraft.client.gui.components.EditBox;
            import net.minecraft.client.gui.components.MultiLineEditBox;
            import net.minecraft.client.gui.screens.Screen;
            import net.minecraft.network.chat.Component;
            import uk.co.enderfall.sdk.api.ui.MenuButton;
            import uk.co.enderfall.sdk.api.ui.MenuLabel;
            import uk.co.enderfall.sdk.api.ui.MenuState;
            import uk.co.enderfall.sdk.api.ui.MenuTextInput;
            import uk.co.enderfall.sdk.runtime.PortableMenuSubmission;
            import uk.co.enderfall.sdk.runtime.PortableMenuView;
            
            /** %4$s renderer for the portable synchronized menu contract. */
            final class %2$s extends Screen {
                private PortableMenuView view;
                private final Consumer<PortableMenuSubmission> actionSender;
                private final Runnable closeSender;
                private final List<ButtonBinding> buttonBindings = new ArrayList<>();
                private final List<TextInputBinding> textInputBindings = new ArrayList<>();
                private final Map<String, String> drafts = new LinkedHashMap<>();
                private final Set<String> dirtyInputs = new java.util.LinkedHashSet<>();
                private boolean synchronizingInputs;
                private boolean remoteClose;
                private boolean closeNotified;
            
                %2$s(PortableMenuView view, Consumer<PortableMenuSubmission> actionSender,
                        Runnable closeSender) {
                    super(Component.literal(view.state().resolve(view.spec().title())));
                    this.view = view;
                    this.actionSender = actionSender;
                    this.closeSender = closeSender;
                    for (MenuTextInput input : view.spec().textInputs()) {
                        drafts.put(input.key(), view.state().value(input.key()));
                    }
                }
            
                @Override
                protected void init() {
                    buttonBindings.clear();
                    textInputBindings.clear();
                    int left = (width - view.spec().width()) / 2;
                    int top = (height - view.spec().height()) / 2;
                    for (MenuTextInput definition : view.spec().textInputs()) {
                        String initial = drafts.getOrDefault(definition.key(), "");
                        if (definition.multiline()) {
                            MultiLineEditBox input = createMultiline(definition,
                                    left + definition.x(), top + definition.y());
                            input.setCharacterLimit(definition.maximumCharacters() * 2);
                            input.setValue(initial);
                            input.setValueListener(value -> changed(definition, value, input::setValue));
                            addRenderableWidget(input);
                            textInputBindings.add(new TextInputBinding(definition,
                                    input::setValue));
                        } else {
                            EditBox input = new EditBox(font, left + definition.x(), top + definition.y(),
                                    definition.width(), definition.height(), Component.literal(definition.key()));
                            input.setMaxLength(definition.maximumCharacters() * 2);
                            input.setHint(Component.literal(view.state().resolve(definition.placeholder())));
                            input.setValue(initial);
                            input.setResponder(value -> changed(definition, value, input::setValue));
                            addRenderableWidget(input);
                            textInputBindings.add(new TextInputBinding(definition,
                                    input::setValue));
                        }
                    }
                    for (MenuButton definition : view.spec().buttons()) {
                        Button button = Button.builder(Component.literal(view.spec().buttonText(definition, view.state())),
                                        ignored -> actionSender.accept(submission(definition.action())))
                                .bounds(left + definition.x(), top + definition.y(), definition.width(), definition.height())
                                .build();
                        button.active = view.spec().actionEnabled(definition.action(), view.state());
                        addRenderableWidget(button);
                        buttonBindings.add(new ButtonBinding(definition, button));
                    }
                }
            
                @Override
                public void %5$s(%3$s graphics, int mouseX, int mouseY, float partialTick) {
                    %11$sgraphics.fill(0, 0, width, height, 0xB0100D18);
                    int left = (width - view.spec().width()) / 2;
                    int top = (height - view.spec().height()) / 2;
                    graphics.fill(left - 2, top - 2, left + view.spec().width() + 2,
                            top + view.spec().height() + 2, 0xFF6C4CA3);
                    graphics.fill(left, top, left + view.spec().width(), top + view.spec().height(),
                            view.spec().backgroundColor());
                    graphics.%6$s(font, view.state().resolve(view.spec().title()),
                            left + view.spec().width() / 2, top + 12, 0xFFFFFFFF);
                    %10$sfor (MenuLabel label : view.spec().labels()) {
                        String text = view.state().resolve(label.text());
                        if (label.centered()) {
                            graphics.%6$s(font, text, left + label.x(), top + label.y(), label.color());
                        } else {
                            graphics.%7$s(font, text, left + label.x(), top + label.y(), label.color(), false);
                        }
                    }
                    super.%5$s(graphics, mouseX, mouseY, partialTick);
                    renderSelectionVisuals(graphics, left, top, mouseX, mouseY);
                }
            
            %12$s    @Override
                public void onClose() {
                    notifyClosed();
                    super.onClose();
                }
            
                @Override
                public void removed() {
                    notifyClosed();
                    super.removed();
                }
            
                @Override
                public boolean isPauseScreen() {
                    return false;
                }
            
                long sessionId() {
                    return view.sessionId();
                }
            
                void update(MenuState state) {
                    view = new PortableMenuView(view.sessionId(), view.menu(), view.spec(), state);
                    for (ButtonBinding binding : buttonBindings) {
                        binding.button().setMessage(Component.literal(view.spec().buttonText(binding.definition(), state)));
                        binding.button().active = view.spec().actionEnabled(binding.definition().action(), state);
                    }
                    for (TextInputBinding binding : textInputBindings) {
                        String key = binding.definition().key();
                        if (!dirtyInputs.contains(key)) {
                            String value = state.value(key);
                            drafts.put(key, value);
                            synchronizingInputs = true;
                            try {
                                binding.writer().accept(value);
                            } finally {
                                synchronizingInputs = false;
                            }
                        }
                    }
                }
            
                void closeFromServer() {
                    remoteClose = true;
                    onClose();
                }
            
                private void notifyClosed() {
                    if (!remoteClose && !closeNotified) {
                        closeNotified = true;
                        closeSender.run();
                    }
                }
            
                static void show(PortableMenuView view, Consumer<PortableMenuSubmission> actionSender,
                                 Runnable closeSender) {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> client.%8$ssetScreen(new %2$s(view, actionSender, closeSender)));
                }
            
                static void update(long sessionId, MenuState state) {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        if (client.%9$s instanceof %2$s screen && screen.sessionId() == sessionId) {
                            screen.update(state);
                        }
                    });
                }
            
                static void close(long sessionId) {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        if (client.%9$s instanceof %2$s screen && screen.sessionId() == sessionId) {
                            screen.closeFromServer();
                        }
                    });
                }

                private void changed(MenuTextInput definition, String value, Consumer<String> restore) {
                    if (synchronizingInputs) return;
                    try {
                        drafts.put(definition.key(), definition.validate(value));
                        dirtyInputs.add(definition.key());
                    } catch (IllegalArgumentException invalid) {
                        synchronizingInputs = true;
                        try {
                            restore.accept(drafts.getOrDefault(definition.key(), ""));
                        } finally {
                            synchronizingInputs = false;
                        }
                    }
                }

                private PortableMenuSubmission submission(String action) {
                    Map<String, String> values = new LinkedHashMap<>();
                    for (TextInputBinding binding : textInputBindings) {
                        String key = binding.definition().key();
                        values.put(key, drafts.getOrDefault(key, ""));
                    }
                    return new PortableMenuSubmission(action, values);
                }

            %15$s

            %14$s
                private boolean submitScroll(double mouseX, double mouseY, double verticalAmount) {
                    int left = (width - view.spec().width()) / 2;
                    int top = (height - view.spec().height()) / 2;
                    var action = view.spec().scrollAction(mouseX - left, mouseY - top,
                            verticalAmount, view.state());
                    if (action.isEmpty()) return false;
                    actionSender.accept(submission(action.orElseThrow()));
                    return true;
                }

                private MultiLineEditBox createMultiline(MenuTextInput definition, int x, int y) {
                    Component placeholder = Component.literal(view.state().resolve(definition.placeholder()));
                    %13$s
                }
            
                private record ButtonBinding(MenuButton definition, Button button) {
                }

                private record TextInputBinding(MenuTextInput definition, Consumer<String> writer) {
                }
            }
            """;
}

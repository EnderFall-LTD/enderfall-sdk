package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.MenuAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Emits the native shell and immediate renderer used by arbitrary portable client screens. */
final class PortableClientScreenEmitter {
    private PortableClientScreenEmitter() { }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> declarations)
            throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) {
            throw new BridgeGenerationException("Unreviewed portable client-screen target " + target.id());
        }
        boolean fabric = target.loaderAbi() == LoaderAbi.FABRIC;
        boolean legacyFml = target.loaderAbi() == LoaderAbi.LEGACY_FML;
        boolean legacy = target.menuAbi() == MenuAbi.V1_20_1;
        boolean keyed = target.menuAbi() == MenuAbi.V1_21_4;
        boolean extracted = target.menuAbi() == MenuAbi.V26_2;
        String packageName = legacyFml ? "uk.co.enderfall.sdk.runtime.forge.v1_20_1"
                : "uk.co.enderfall.sdk.runtime." + (fabric ? "fabric" : "neoforge") + ".v1_21_4";
        String prefix = legacyFml ? "LegacyForge" : fabric ? "Fabric" : "NeoForge";
        String canonicalRoot = "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge") + "/v1_21_4/";
        String canonicalAdapter = canonicalRoot + (fabric ? "Fabric" : "NeoForge") + "PlatformAdapter.java";
        if (!declarations.contains(canonicalAdapter)) return List.of();
        String className = prefix + "ClientScreenBridge";
        String graphics = extracted ? "GuiGraphicsExtractor" : "GuiGraphics";
        String identifier = extracted ? "Identifier" : "ResourceLocation";
        String parse = extracted ? "Identifier.parse(texture.toString())"
                : legacy ? "java.util.Objects.requireNonNull(ResourceLocation.tryParse(texture.toString()))"
                : "ResourceLocation.parse(texture.toString())";
        String setScreen = extracted ? "client.gui.setScreen(screen)" : "client.setScreen(screen)";
        String currentScreen = extracted ? "client.gui.screen()" : "client.screen";
        String render = extracted ? EXTRACTED_RENDER : IMMEDIATE_RENDER;
        String input = extracted ? EXTRACTED_INPUT : legacy ? LEGACY_INPUT : MODERN_INPUT;
        String pose = extracted ? EXTRACTED_POSE : IMMEDIATE_POSE;
        String blit = extracted ? EXTRACTED_BLIT : keyed ? KEYED_BLIT : IMMEDIATE_BLIT;
        String itemRegistry = legacyFml ? "net.minecraftforge.registries.ForgeRegistries.ITEMS"
                : "net.minecraft.core.registries.BuiltInRegistries.ITEM";
        String itemId = extracted
                ? "net.minecraft.resources.Identifier.parse(stack.item().id().toString())"
                : legacy ? "java.util.Objects.requireNonNull(net.minecraft.resources.ResourceLocation.tryParse(stack.item().id().toString()))"
                : "net.minecraft.resources.ResourceLocation.parse(stack.item().id().toString())";
        String itemLookup = legacyFml || keyed || extracted
                ? itemRegistry + ".getValue(id)" : itemRegistry + ".get(id)";
        String nativeStack = extracted
                ? "var holder = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id).orElse(null);\n"
                        + "                        if (holder == null) throw new IllegalArgumentException(\"Unknown portable UI item \" + stack.item().id());\n"
                        + "                        if (!holder.areComponentsBound()) return net.minecraft.world.item.ItemStack.EMPTY;\n"
                        + "                        return new net.minecraft.world.item.ItemStack(holder, stack.count());"
                : "var item = " + itemLookup + ";\n"
                        + "                        if (item == null) throw new IllegalArgumentException(\"Unknown portable UI item \" + stack.item().id());\n"
                        + "                        return new net.minecraft.world.item.ItemStack(item, stack.count());";
        String itemDraw = extracted
                ? "graphics.item(nativeStack, x, y);\n                        if (decorations) graphics.itemDecorations(font, nativeStack, x, y);"
                : "graphics.renderItem(nativeStack, x, y);\n                        if (decorations) graphics.renderItemDecorations(font, nativeStack, x, y);";
        String entityRegistry = legacyFml ? "net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES"
                : "net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE";
        String entityId = extracted
                ? "net.minecraft.resources.Identifier.parse(type.id().toString())"
                : legacy ? "java.util.Objects.requireNonNull(net.minecraft.resources.ResourceLocation.tryParse(type.id().toString()))"
                : "net.minecraft.resources.ResourceLocation.parse(type.id().toString())";
        String entityLookup = legacyFml || keyed || extracted
                ? entityRegistry + ".getValue(id)" : entityRegistry + ".get(id)";
        String entityCreate = keyed || extracted
                ? "nativeType.create(client.level, net.minecraft.world.entity.EntitySpawnReason.COMMAND)"
                : "nativeType.create(client.level)";
        String entityDraw = extracted
                ? "net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(\n"
                        + "                                graphics, bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), scale, 0.0625F,\n"
                        + "                                pointerX, pointerY, preview);"
                : legacy
                    ? "net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(\n"
                            + "                                graphics, bounds.x() + bounds.width() / 2, bounds.bottom() - 2, scale,\n"
                            + "                                pointerX, pointerY, preview);"
                    : "net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(\n"
                            + "                                graphics, bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), scale, 0.0625F,\n"
                            + "                                pointerX, pointerY, preview);";
        String source = SOURCE
                .replace("${PACKAGE}", packageName)
                .replace("${CLASS}", className)
                .replace("${GRAPHICS}", graphics)
                .replace("${IDENTIFIER}", identifier)
                .replace("${PARSE}", parse)
                .replace("${SET_SCREEN}", setScreen)
                .replace("${CURRENT_SCREEN}", currentScreen)
                .replace("${RENDER}", render)
                .replace("${INPUT}", input)
                .replace("${POSE}", pose)
                .replace("${BLIT}", blit)
                .replace("${ITEM_ID}", itemId)
                .replace("${ITEM_REGISTRY}", itemRegistry)
                .replace("${ITEM_LOOKUP}", itemLookup)
                .replace("${NATIVE_STACK}", nativeStack)
                .replace("${ITEM_DRAW}", itemDraw)
                .replace("${ENTITY_ID}", entityId)
                .replace("${ENTITY_REGISTRY}", entityRegistry)
                .replace("${ENTITY_LOOKUP}", entityLookup)
                .replace("${ENTITY_CREATE}", entityCreate)
                .replace("${ENTITY_DRAW}", entityDraw)
                .replace("${TEXT_DRAW}", extracted
                        ? "graphics.text(font, text, Math.round(x), Math.round(y), color(argb), shadow);"
                        : "graphics.drawString(font, text, Math.round(x), Math.round(y), color(argb), shadow);");
        String path = packageName.replace('.', '/') + "/" + className + ".java";
        return List.of(new RuntimeSource(path, path, source.getBytes(StandardCharsets.UTF_8)));
    }

    private static final String IMMEDIATE_RENDER = """
            @Override
            public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                NativeRenderContext context = new NativeRenderContext(graphics, entityPreviews,
                        new UiRenderFrame(width, height, mouseX, mouseY, partialTick,
                                Math.max(0L, System.nanoTime())));
                try {
                    portable.render(context);
                } finally {
                    context.finish();
                }
            }
            """;

    private static final String EXTRACTED_RENDER = """
            @Override
            public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                NativeRenderContext context = new NativeRenderContext(graphics, entityPreviews,
                        new UiRenderFrame(width, height, mouseX, mouseY, partialTick,
                                Math.max(0L, System.nanoTime())));
                try {
                    portable.render(context);
                } finally {
                    context.finish();
                }
            }
            """;

    private static final String LEGACY_INPUT = """
            @Override public boolean mouseScrolled(double x, double y, double amount) {
                return portable.mouseScrolled(x, y, 0, amount) || super.mouseScrolled(x, y, amount);
            }
            """ + immediateCommonInput();

    private static final String MODERN_INPUT = """
            @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
                return portable.mouseScrolled(x, y, horizontal, vertical)
                        || super.mouseScrolled(x, y, horizontal, vertical);
            }
            """ + immediateCommonInput();

    private static String immediateCommonInput() { return """
            @Override public boolean mouseClicked(double x, double y, int button) {
                return portable.mouseClicked(x, y, button) || super.mouseClicked(x, y, button);
            }
            @Override public boolean mouseReleased(double x, double y, int button) {
                return portable.mouseReleased(x, y, button) || super.mouseReleased(x, y, button);
            }
            @Override public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
                return portable.mouseDragged(x, y, button, dragX, dragY)
                        || super.mouseDragged(x, y, button, dragX, dragY);
            }
            @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
                return portable.keyPressed(key, scanCode, modifiers) || super.keyPressed(key, scanCode, modifiers);
            }
            @Override public boolean keyReleased(int key, int scanCode, int modifiers) {
                return portable.keyReleased(key, scanCode, modifiers) || super.keyReleased(key, scanCode, modifiers);
            }
            @Override public boolean charTyped(char character, int modifiers) {
                return portable.characterTyped(character, modifiers) || super.charTyped(character, modifiers);
            }
            """; }

    private static final String EXTRACTED_INPUT = """
            @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
                return portable.mouseClicked(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubled);
            }
            @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
                return portable.mouseReleased(event.x(), event.y(), event.button()) || super.mouseReleased(event);
            }
            @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,
                    double dragX, double dragY) {
                return portable.mouseDragged(event.x(), event.y(), event.button(), dragX, dragY)
                        || super.mouseDragged(event, dragX, dragY);
            }
            @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
                return portable.mouseScrolled(x, y, horizontal, vertical)
                        || super.mouseScrolled(x, y, horizontal, vertical);
            }
            @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
                return portable.keyPressed(event.key(), event.scancode(), event.modifiers()) || super.keyPressed(event);
            }
            @Override public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
                return portable.keyReleased(event.key(), event.scancode(), event.modifiers()) || super.keyReleased(event);
            }
            @Override public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
                return portable.characterTyped(event.codepoint(), 0) || super.charTyped(event);
            }
            """;

    private static final String IMMEDIATE_POSE = """
            @Override public void pushPose() { graphics.pose().pushPose(); poseDepth++; }
            @Override public void popPose() {
                requireStack(poseDepth, "pose"); graphics.pose().popPose(); poseDepth--;
            }
            @Override public void translate(float x, float y, float depth) { graphics.pose().translate(x, y, depth); }
            @Override public void scale(float x, float y) { graphics.pose().scale(x, y, 1); }
            @Override public void rotate(float degrees) {
                graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
            }
            """;

    private static final String EXTRACTED_POSE = """
            @Override public void pushPose() { graphics.pose().pushMatrix(); poseDepth++; }
            @Override public void popPose() {
                requireStack(poseDepth, "pose"); graphics.pose().popMatrix(); poseDepth--;
            }
            @Override public void translate(float x, float y, float depth) {
                if (depth != 0) graphics.nextStratum();
                graphics.pose().translate(x, y);
            }
            @Override public void scale(float x, float y) { graphics.pose().scale(x, y); }
            @Override public void rotate(float degrees) { graphics.pose().rotate((float) Math.toRadians(degrees)); }
            """;

    private static final String IMMEDIATE_BLIT = """
            int tint = color(argb);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(
                    ((tint >>> 16) & 255) / 255.0F, ((tint >>> 8) & 255) / 255.0F,
                    (tint & 255) / 255.0F, ((tint >>> 24) & 255) / 255.0F);
            try {
                graphics.blit(id, destination.x(), destination.y(), destination.width(), destination.height(),
                        sourceX, sourceY, sourceWidth, sourceHeight, textureWidth, textureHeight);
            } finally {
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
            """;

    private static final String KEYED_BLIT = """
            graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, id,
                    destination.x(), destination.y(), destination.width(), destination.height(),
                    sourceX, sourceY, sourceWidth, sourceHeight, textureWidth, textureHeight, color(argb));
            """;

    private static final String EXTRACTED_BLIT = """
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id,
                    destination.x(), destination.y(), destination.width(), destination.height(),
                    sourceX, sourceY, sourceWidth, sourceHeight, textureWidth, textureHeight, color(argb));
            """;

    private static final String SOURCE = """
            package ${PACKAGE};

            import java.util.ArrayDeque;
            import java.util.ArrayList;
            import java.util.Deque;
            import java.util.LinkedHashMap;
            import java.util.List;
            import java.util.Map;
            import java.util.function.Supplier;
            import net.minecraft.client.Minecraft;
            import net.minecraft.client.gui.Font;
            import net.minecraft.client.gui.${GRAPHICS};
            import net.minecraft.client.gui.screens.Screen;
            import net.minecraft.network.chat.Component;
            import net.minecraft.resources.${IDENTIFIER};
            import uk.co.enderfall.sdk.api.ResourceId;
            import uk.co.enderfall.sdk.api.client.ui.ClientScreenContext;
            import uk.co.enderfall.sdk.api.client.ui.ClientScreenRef;
            import uk.co.enderfall.sdk.api.client.ui.ClientScreenSpec;
            import uk.co.enderfall.sdk.api.client.ui.PortableClientScreen;
            import uk.co.enderfall.sdk.api.client.ui.UiInsets;
            import uk.co.enderfall.sdk.api.client.ui.UiRect;
            import uk.co.enderfall.sdk.api.client.ui.UiRenderContext;
            import uk.co.enderfall.sdk.api.client.ui.UiRenderFrame;
            import uk.co.enderfall.sdk.api.client.ui.UiTextAlign;
            import uk.co.enderfall.sdk.api.client.ui.UiTextMetrics;
            import uk.co.enderfall.sdk.api.entity.EntityTypeRef;
            import uk.co.enderfall.sdk.api.item.ItemStackRef;

            /** Generated target-native host for arbitrary portable client screens. */
            final class ${CLASS} {
                private static final Map<ResourceId, Definition> DEFINITIONS = new LinkedHashMap<>();

                private ${CLASS}() { }

                static synchronized void register(ResourceId id, ClientScreenSpec spec,
                        Supplier<? extends PortableClientScreen> factory) {
                    if (DEFINITIONS.putIfAbsent(id, new Definition(spec, factory)) != null) {
                        throw new IllegalStateException("Duplicate portable client screen " + id);
                    }
                }

                static void open(ResourceId id) {
                    Definition definition;
                    synchronized (${CLASS}.class) { definition = DEFINITIONS.get(id); }
                    if (definition == null) throw new IllegalArgumentException("Unknown portable client screen " + id);
                    PortableClientScreen portable = java.util.Objects.requireNonNull(
                            definition.factory().get(), "Portable screen factory returned null for " + id);
                    Minecraft client = Minecraft.getInstance();
                    Screen screen = new HostScreen(id, definition.spec(), portable);
                    client.execute(() -> ${SET_SCREEN});
                }

                static void close() {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        if (${CURRENT_SCREEN} instanceof HostScreen screen) screen.onClose();
                    });
                }

                private record Definition(ClientScreenSpec spec,
                        Supplier<? extends PortableClientScreen> factory) { }

                private static final class HostScreen extends Screen implements ClientScreenContext {
                    private final ResourceId id;
                    private final ClientScreenSpec spec;
                    private final PortableClientScreen portable;
                    private final Map<ResourceId, net.minecraft.world.entity.LivingEntity> entityPreviews =
                            new LinkedHashMap<>();
                    private boolean initialized;
                    private boolean removed;

                    private HostScreen(ResourceId id, ClientScreenSpec spec, PortableClientScreen portable) {
                        super(Component.literal(spec.title()));
                        this.id = id;
                        this.spec = spec;
                        this.portable = portable;
                    }

                    @Override protected void init() {
                        if (!initialized) {
                            initialized = true;
                            portable.initialize(this);
                        } else portable.resized(this);
                    }

                    @Override public void tick() { portable.tick(this); }
                    @Override public boolean isPauseScreen() { return spec.pausesGame(); }
                    @Override public boolean shouldCloseOnEsc() { return spec.closeOnEscape(); }
                    @Override public int width() { return width; }
                    @Override public int height() { return height; }
                    @Override public void close() { onClose(); }
                    @Override public void open(ClientScreenRef screen) { ${CLASS}.open(screen.id()); }

                    @Override public void removed() {
                        if (!removed) {
                            removed = true;
                            entityPreviews.clear();
                            portable.removed();
                        }
                        super.removed();
                    }

                    ${RENDER}
                    ${INPUT}
                }

                private static final class NativeRenderContext implements UiRenderContext {
                    private final ${GRAPHICS} graphics;
                    private final Font font = Minecraft.getInstance().font;
                    private final UiRenderFrame frame;
                    private final Map<ResourceId, net.minecraft.world.entity.LivingEntity> entityPreviews;
                    private final Deque<UiRect> clips = new ArrayDeque<>();
                    private final Deque<Float> opacities = new ArrayDeque<>();
                    private final Deque<Integer> depths = new ArrayDeque<>();
                    private int poseDepth;
                    private float opacity = 1;
                    private int depth;

                    private NativeRenderContext(${GRAPHICS} graphics,
                            Map<ResourceId, net.minecraft.world.entity.LivingEntity> entityPreviews,
                            UiRenderFrame frame) {
                        this.graphics = graphics;
                        this.entityPreviews = entityPreviews;
                        this.frame = frame;
                    }

                    @Override public UiRenderFrame frame() { return frame; }

                    @Override public void fill(UiRect bounds, int argb) {
                        if (bounds.width() == 0 || bounds.height() == 0) return;
                        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), color(argb));
                    }

                    @Override public void sprite(ResourceId texture, UiRect destination, int textureWidth,
                            int textureHeight, int sourceX, int sourceY, int sourceWidth, int sourceHeight, int argb) {
                        java.util.Objects.requireNonNull(texture, "texture");
                        java.util.Objects.requireNonNull(destination, "destination");
                        if (textureWidth <= 0 || textureHeight <= 0 || sourceWidth < 0 || sourceHeight < 0
                                || sourceX < 0 || sourceY < 0 || sourceX + sourceWidth > textureWidth
                                || sourceY + sourceHeight > textureHeight) {
                            throw new IllegalArgumentException("Invalid portable sprite region");
                        }
                        if (destination.width() == 0 || destination.height() == 0
                                || sourceWidth == 0 || sourceHeight == 0) return;
                        ${IDENTIFIER} id = ${PARSE};
                        ${BLIT}
                    }

                    @Override public void nineSlice(ResourceId texture, UiRect destination, int textureWidth,
                            int textureHeight, UiInsets border, int argb) {
                        java.util.Objects.requireNonNull(border, "border");
                        int left = Math.min(border.left(), Math.min(textureWidth, destination.width()) / 2);
                        int right = Math.min(border.right(), Math.min(textureWidth - left, destination.width() - left));
                        int top = Math.min(border.top(), Math.min(textureHeight, destination.height()) / 2);
                        int bottom = Math.min(border.bottom(), Math.min(textureHeight - top, destination.height() - top));
                        int sourceMiddleWidth = Math.max(0, textureWidth - left - right);
                        int sourceMiddleHeight = Math.max(0, textureHeight - top - bottom);
                        int middleWidth = Math.max(0, destination.width() - left - right);
                        int middleHeight = Math.max(0, destination.height() - top - bottom);
                        region(texture, destination.x(), destination.y(), left, top, textureWidth, textureHeight,
                                0, 0, left, top, argb);
                        region(texture, destination.x() + left, destination.y(), middleWidth, top, textureWidth,
                                textureHeight, left, 0, sourceMiddleWidth, top, argb);
                        region(texture, destination.right() - right, destination.y(), right, top, textureWidth,
                                textureHeight, textureWidth - right, 0, right, top, argb);
                        region(texture, destination.x(), destination.y() + top, left, middleHeight, textureWidth,
                                textureHeight, 0, top, left, sourceMiddleHeight, argb);
                        region(texture, destination.x() + left, destination.y() + top, middleWidth, middleHeight,
                                textureWidth, textureHeight, left, top, sourceMiddleWidth, sourceMiddleHeight, argb);
                        region(texture, destination.right() - right, destination.y() + top, right, middleHeight,
                                textureWidth, textureHeight, textureWidth - right, top, right, sourceMiddleHeight, argb);
                        region(texture, destination.x(), destination.bottom() - bottom, left, bottom, textureWidth,
                                textureHeight, 0, textureHeight - bottom, left, bottom, argb);
                        region(texture, destination.x() + left, destination.bottom() - bottom, middleWidth, bottom,
                                textureWidth, textureHeight, left, textureHeight - bottom, sourceMiddleWidth, bottom, argb);
                        region(texture, destination.right() - right, destination.bottom() - bottom, right, bottom,
                                textureWidth, textureHeight, textureWidth - right, textureHeight - bottom, right, bottom, argb);
                    }

                    @Override public void tile(ResourceId texture, UiRect destination, int textureWidth,
                            int textureHeight, int tileWidth, int tileHeight, int sourceX, int sourceY, int argb) {
                        if (tileWidth <= 0 || tileHeight <= 0) throw new IllegalArgumentException("Tile size must be positive");
                        if (sourceX < 0 || sourceY < 0 || sourceX + tileWidth > textureWidth
                                || sourceY + tileHeight > textureHeight) {
                            throw new IllegalArgumentException("Tile region is outside the texture");
                        }
                        for (int y = destination.y(); y < destination.bottom(); y += tileHeight) {
                            int height = Math.min(tileHeight, destination.bottom() - y);
                            for (int x = destination.x(); x < destination.right(); x += tileWidth) {
                                int width = Math.min(tileWidth, destination.right() - x);
                                sprite(texture, new UiRect(x, y, width, height), textureWidth,
                                        textureHeight, sourceX, sourceY, width, height, argb);
                            }
                        }
                    }

                    private void region(ResourceId texture, int x, int y, int width, int height,
                            int textureWidth, int textureHeight, int sourceX, int sourceY,
                            int sourceWidth, int sourceHeight, int argb) {
                        if (width == 0 || height == 0 || sourceWidth == 0 || sourceHeight == 0) return;
                        sprite(texture, new UiRect(x, y, width, height), textureWidth, textureHeight,
                                sourceX, sourceY, sourceWidth, sourceHeight, argb);
                    }

                    @Override public UiTextMetrics measureText(String text) {
                        java.util.Objects.requireNonNull(text, "text");
                        return new UiTextMetrics(font.width(text), font.lineHeight);
                    }

                    @Override public List<String> wrapText(String text, int maximumWidth) {
                        java.util.Objects.requireNonNull(text, "text");
                        if (maximumWidth <= 0) throw new IllegalArgumentException("maximumWidth must be positive");
                        List<String> lines = new ArrayList<>();
                        for (String paragraph : text.split("\\n", -1)) wrapParagraph(paragraph, maximumWidth, lines);
                        return List.copyOf(lines);
                    }

                    private void wrapParagraph(String paragraph, int maximumWidth, List<String> output) {
                        if (paragraph.isEmpty()) { output.add(""); return; }
                        StringBuilder line = new StringBuilder();
                        for (String word : paragraph.trim().split("\\s+")) {
                            String candidate = line.isEmpty() ? word : line + " " + word;
                            if (font.width(candidate) <= maximumWidth) { line.setLength(0); line.append(candidate); continue; }
                            if (!line.isEmpty()) { output.add(line.toString()); line.setLength(0); }
                            int start = 0;
                            for (int end = 1; end <= word.length(); end++) {
                                if (font.width(word.substring(start, end)) > maximumWidth) {
                                    output.add(word.substring(start, Math.max(start + 1, end - 1)));
                                    start = Math.max(start + 1, end - 1); end = start;
                                }
                            }
                            if (start < word.length()) line.append(word.substring(start));
                        }
                        if (!line.isEmpty()) output.add(line.toString());
                    }

                    @Override public void text(String text, float anchorX, float y, int argb,
                            boolean shadow, UiTextAlign alignment) {
                        java.util.Objects.requireNonNull(text, "text");
                        java.util.Objects.requireNonNull(alignment, "alignment");
                        int width = font.width(text);
                        float x = switch (alignment) {
                            case LEFT -> anchorX;
                            case CENTER -> anchorX - width / 2f;
                            case RIGHT -> anchorX - width;
                        };
                        ${TEXT_DRAW}
                    }

                    @Override public void item(ItemStackRef stack, int x, int y, boolean decorations) {
                        net.minecraft.world.item.ItemStack nativeStack = nativeStack(stack);
                        if (nativeStack.isEmpty()) return;
                        ${ITEM_DRAW}
                    }

                    @Override public void tooltip(List<String> lines, int mouseX, int mouseY) {
                        java.util.Objects.requireNonNull(lines, "lines");
                        if (lines.isEmpty()) return;
                        if (lines.size() > 64) throw new IllegalArgumentException("Portable tooltip exceeds 64 lines");
                        int maximumWidth = 0;
                        for (String line : lines) {
                            java.util.Objects.requireNonNull(line, "tooltip line");
                            if (line.length() > 1024) {
                                throw new IllegalArgumentException("Portable tooltip line exceeds 1024 characters");
                            }
                            maximumWidth = Math.max(maximumWidth, font.width(line));
                        }
                        int width = maximumWidth + 8;
                        int height = lines.size() * font.lineHeight + 6;
                        int left = Math.max(2, Math.min(mouseX + 12, Math.max(2, frame.width() - width - 2)));
                        int top = Math.max(2, Math.min(mouseY - 12, Math.max(2, frame.height() - height - 2)));
                        fill(new UiRect(left, top, width, height), 0xF0100010);
                        fill(new UiRect(left, top, width, 1), 0xFF5000A0);
                        fill(new UiRect(left, top + height - 1, width, 1), 0xFF280050);
                        for (int index = 0; index < lines.size(); index++) {
                            text(lines.get(index), left + 4, top + 3 + index * font.lineHeight,
                                    0xFFFFFFFF, true, UiTextAlign.LEFT);
                        }
                    }

                    @Override public void itemTooltip(ItemStackRef stack, int x, int y) {
                        net.minecraft.world.item.ItemStack nativeStack = nativeStack(stack);
                        tooltip(List.of(nativeStack.isEmpty()
                                ? stack.item().id().toString() : nativeStack.getHoverName().getString()), x, y);
                    }

                    @Override public void livingEntity(EntityTypeRef type, UiRect bounds,
                            float pointerX, float pointerY) {
                        java.util.Objects.requireNonNull(type, "type");
                        java.util.Objects.requireNonNull(bounds, "bounds");
                        if (!Float.isFinite(pointerX) || !Float.isFinite(pointerY)) {
                            throw new IllegalArgumentException("Entity-preview pointer coordinates must be finite");
                        }
                        if (bounds.width() < 1 || bounds.height() < 1) return;
                        Minecraft client = Minecraft.getInstance();
                        if (client.level == null) return;
                        net.minecraft.world.entity.LivingEntity preview = entityPreviews.get(type.id());
                        if (preview == null || preview.level() != client.level) {
                            var id = ${ENTITY_ID};
                            if (!${ENTITY_REGISTRY}.containsKey(id)) {
                                throw new IllegalArgumentException("Unknown portable UI entity " + type.id());
                            }
                            var nativeType = ${ENTITY_LOOKUP};
                            var created = nativeType == null ? null : ${ENTITY_CREATE};
                            if (!(created instanceof net.minecraft.world.entity.LivingEntity living)) {
                                throw new IllegalArgumentException("Portable UI entity is not living: " + type.id());
                            }
                            preview = living;
                            entityPreviews.put(type.id(), preview);
                        }
                        int scale = Math.max(1, Math.min(bounds.width(), bounds.height()) / 2);
                        ${ENTITY_DRAW}
                    }

                    private net.minecraft.world.item.ItemStack nativeStack(ItemStackRef stack) {
                        java.util.Objects.requireNonNull(stack, "stack");
                        var id = ${ITEM_ID};
                        if (!${ITEM_REGISTRY}.containsKey(id)) {
                            throw new IllegalArgumentException("Unknown portable UI item " + stack.item().id());
                        }
                        ${NATIVE_STACK}
                    }

                    @Override public void pushClip(UiRect bounds) {
                        java.util.Objects.requireNonNull(bounds, "bounds");
                        UiRect clip = clips.isEmpty() ? bounds : clips.peek().intersect(bounds);
                        clips.push(clip);
                        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
                    }

                    @Override public void popClip() {
                        requireStack(clips.size(), "clip");
                        clips.pop();
                        graphics.disableScissor();
                        if (!clips.isEmpty()) {
                            UiRect clip = clips.peek();
                            graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
                        }
                    }

                    ${POSE}

                    @Override public void pushOpacity(float value) {
                        if (!Float.isFinite(value) || value < 0 || value > 1) {
                            throw new IllegalArgumentException("Opacity must be within 0..1");
                        }
                        opacities.push(opacity);
                        opacity *= value;
                    }

                    @Override public void popOpacity() {
                        requireStack(opacities.size(), "opacity"); opacity = opacities.pop();
                    }

                    @Override public void pushDepth(int value) {
                        depths.push(depth);
                        depth = Math.addExact(depth, value);
                    }

                    @Override public void popDepth() {
                        requireStack(depths.size(), "depth"); depth = depths.pop();
                    }

                    private int color(int argb) {
                        int alpha = Math.round(((argb >>> 24) & 255) * opacity);
                        return (argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
                    }

                    private static void requireStack(int size, String name) {
                        if (size == 0) throw new IllegalStateException("Portable UI " + name + " stack underflow");
                    }

                    private void finish() {
                        while (!clips.isEmpty()) popClip();
                        while (poseDepth > 0) popPose();
                        opacities.clear(); opacity = 1;
                        depths.clear(); depth = 0;
                    }
                }
            }
            """;

}

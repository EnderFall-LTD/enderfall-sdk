package uk.co.enderfall.sdk.bridge;

/** Emits the small native item shell that renders declarative portable tooltips. */
final class PortableItemSources {
    private PortableItemSources() { }

    static String emit(String target) {
        BlockEntityNativePolicy policy = BlockEntityNativePolicy.require(target);
        String shiftExpression = policy.unobfuscated()
                ? "net.minecraft.client.Minecraft.getInstance().hasShiftDown()"
                : "net.minecraft.client.gui.screens.Screen.hasShiftDown()";
        String method = policy.unobfuscated() ? modernMethod(shiftExpression)
                : policy.legacy() ? legacyMethod(shiftExpression) : componentEraMethod(shiftExpression);
        method += repairMethod(policy);
        method += configurePropertiesMethod(policy);
        String storage = policy.legacy() ? legacyStorage()
                : policy.unobfuscated() ? unobfuscatedStorage() : componentStorage();
        return """
                package uk.co.enderfall.sdk.runtime.item.nativebridge;

                import net.minecraft.ChatFormatting;
                import net.minecraft.network.chat.Component;
                import net.minecraft.world.item.Item;
                import net.minecraft.world.item.ItemStack;
                import net.minecraft.world.item.TooltipFlag;
                import uk.co.enderfall.sdk.api.item.TooltipLine;
                import uk.co.enderfall.sdk.api.item.TooltipVisibility;
                import uk.co.enderfall.sdk.api.item.ItemDataKey;
                import uk.co.enderfall.sdk.api.item.MutableItemData;
                import uk.co.enderfall.sdk.api.item.MutableItemStack;
                import uk.co.enderfall.sdk.api.item.RepairMaterial;
                import uk.co.enderfall.sdk.api.registry.ItemSpec;

                public final class PortableSdkItem extends Item {
                    private final ItemSpec spec;

                    public PortableSdkItem(Properties properties, ItemSpec spec) {
                        super(configureProperties(properties, spec));
                        this.spec = java.util.Objects.requireNonNull(spec, "spec");
                    }

                    /** Returns a validated data view only for SDK items with declared keys. */
                    public static MutableItemData data(ItemStack stack, boolean writable) {
                        if (!(stack.getItem() instanceof PortableSdkItem item) || item.spec.dataKeys().isEmpty()) {
                            return null;
                        }
                        return new NativeData(stack, item.spec, writable);
                    }

                    /** Returns a safe view of an SDK-owned stack, including server mutations. */
                    public static MutableItemStack stack(ItemStack stack, boolean writable) {
                        if (!(stack.getItem() instanceof PortableSdkItem item)) return null;
                        return new NativeStack(stack, item.spec, writable);
                    }

                %s
                    private void appendPortableTooltips(boolean shiftDown, boolean advanced,
                            java.util.function.Consumer<Component> output) {
                        for (TooltipLine line : spec.tooltipLines()) {
                            if (!visible(line.visibility(), shiftDown, advanced)) continue;
                            Component text = line.translated()
                                    ? Component.translatable(line.text()) : Component.literal(line.text());
                            output.accept(text.copy().withStyle(ChatFormatting.valueOf(line.color().name())));
                        }
                    }

                    private static boolean visible(TooltipVisibility visibility, boolean shiftDown,
                            boolean advanced) {
                        return switch (visibility) {
                            case ALWAYS -> true;
                            case SHIFT_DOWN -> shiftDown;
                            case SHIFT_UP -> !shiftDown;
                            case ADVANCED -> advanced;
                        };
                    }

                    private static final class NativeStack implements MutableItemStack {
                        private final ItemStack stack;
                        private final ItemSpec spec;
                        private final boolean writable;

                        private NativeStack(ItemStack stack, ItemSpec spec, boolean writable) {
                            this.stack = stack;
                            this.spec = spec;
                            this.writable = writable;
                        }

                        @Override public int count() { return stack.getCount(); }

                        @Override public int maxStackSize() { return spec.maxStackSize(); }

                        @Override public boolean damageable() { return spec.durability() > 0; }

                        @Override public int damage() { return damageable() ? stack.getDamageValue() : 0; }

                        @Override public int maxDamage() { return spec.durability(); }

                        @Override public boolean damage(int amount) {
                            requireWritable();
                            if (amount < 0) throw new IllegalArgumentException("Damage amount cannot be negative");
                            if (amount == 0 || !damageable() || stack.isEmpty()) return false;
                            long next = (long) stack.getDamageValue() + amount;
                            if (next >= spec.durability()) {
                                stack.shrink(1);
                                return true;
                            }
                            stack.setDamageValue((int) next);
                            return false;
                        }

                        @Override public int repair(int amount) {
                            requireWritable();
                            if (amount < 0) throw new IllegalArgumentException("Repair amount cannot be negative");
                            if (amount == 0 || !damageable() || stack.isEmpty()) return 0;
                            int repaired = Math.min(amount, stack.getDamageValue());
                            stack.setDamageValue(stack.getDamageValue() - repaired);
                            return repaired;
                        }

                        @Override public int consume(int amount) {
                            requireWritable();
                            if (amount < 0) throw new IllegalArgumentException("Consume amount cannot be negative");
                            int consumed = Math.min(amount, stack.getCount());
                            stack.shrink(consumed);
                            return consumed;
                        }

                        private void requireWritable() {
                            if (!writable) throw new IllegalStateException("Item stacks can only be changed on the server");
                        }
                    }

                    private static final class NativeData implements MutableItemData {
                        private final ItemStack stack;
                        private final ItemSpec spec;
                        private final boolean writable;

                        private NativeData(ItemStack stack, ItemSpec spec, boolean writable) {
                            this.stack = stack;
                            this.spec = spec;
                            this.writable = writable;
                        }

                        @Override public <T> java.util.Optional<T> get(ItemDataKey<T> key) {
                            require(key);
                            Object value = readValue(stack, key);
                            return value == null ? java.util.Optional.empty()
                                    : java.util.Optional.of(key.validate(value));
                        }

                        @Override public <T> void set(ItemDataKey<T> key, T value) {
                            requireWritable();
                            require(key);
                            writeValue(stack, key, key.validate(value));
                        }

                        @Override public void remove(ItemDataKey<?> key) {
                            requireWritable();
                            require(key);
                            removeValue(stack, key);
                        }

                        private void require(ItemDataKey<?> key) {
                            ItemDataKey<?> declared = spec.dataKey(key.id()).orElseThrow(() ->
                                    new IllegalArgumentException("Undeclared portable item data key " + key.id()));
                            if (!declared.equals(key)) {
                                throw new IllegalArgumentException("Portable item data declaration differs for " + key.id());
                            }
                        }

                        private void requireWritable() {
                            if (!writable) throw new IllegalStateException("Item data can only be changed on the server");
                        }
                    }

                %s
                }
                """.formatted(method, storage);
    }

    private static String legacyMethod(String shiftExpression) {
        return """
                    @Override
                    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level,
                            java.util.List<Component> lines, TooltipFlag flag) {
                        super.appendHoverText(stack, level, lines, flag);
                        appendPortableTooltips(%s, flag.isAdvanced(), lines::add);
                    }

                """.formatted(shiftExpression);
    }

    private static String componentEraMethod(String shiftExpression) {
        return """
                    @Override
                    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                            java.util.List<Component> lines, TooltipFlag flag) {
                        super.appendHoverText(stack, context, lines, flag);
                        appendPortableTooltips(%s, flag.isAdvanced(), lines::add);
                    }

                """.formatted(shiftExpression);
    }

    private static String modernMethod(String shiftExpression) {
        return """
                    @SuppressWarnings("deprecation")
                    @Override
                    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                            net.minecraft.world.item.component.TooltipDisplay display,
                            java.util.function.Consumer<Component> lines, TooltipFlag flag) {
                        super.appendHoverText(stack, context, display, lines, flag);
                        appendPortableTooltips(%s, flag.isAdvanced(), lines);
                    }

                """.formatted(shiftExpression);
    }

    private static String repairMethod(BlockEntityNativePolicy policy) {
        if (policy.modernRecipes()) return "";
        String nativeId = policy.unobfuscated()
                ? "net.minecraft.resources.Identifier.fromNamespaceAndPath(material.id().namespace(), material.id().path())"
                : policy.legacy()
                        ? "java.util.Objects.requireNonNull(net.minecraft.resources.ResourceLocation.tryParse(material.id().toString()))"
                        : "net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(material.id().namespace(), material.id().path())";
        String itemRegistry = policy.legacy() && !policy.fabric()
                ? "net.minecraftforge.registries.ForgeRegistries.ITEMS"
                : "net.minecraft.core.registries.BuiltInRegistries.ITEM";
        return """
                    @Override
                    public boolean isValidRepairItem(ItemStack stack, ItemStack candidate) {
                        if (super.isValidRepairItem(stack, candidate)) return true;
                        RepairMaterial material = spec.repairMaterial().orElse(null);
                        if (material == null) return false;
                        String candidateId = %s
                                .getKey(candidate.getItem()).toString();
                        if (material.kind() == RepairMaterial.Kind.ITEM) {
                            return candidateId.equals(material.id().toString());
                        }
                        return candidate.is(net.minecraft.tags.TagKey.create(
                                net.minecraft.core.registries.Registries.ITEM, %s));
                    }

                """.formatted(itemRegistry, nativeId);
    }

    private static String configurePropertiesMethod(BlockEntityNativePolicy policy) {
        if (!policy.modernRecipes()) {
            return """
                        private static Properties configureProperties(Properties properties, ItemSpec spec) {
                            return properties;
                        }

                    """;
        }
        String nativeId = policy.unobfuscated()
                ? "net.minecraft.resources.Identifier.fromNamespaceAndPath(material.id().namespace(), material.id().path())"
                : "net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(material.id().namespace(), material.id().path())";
        return """
                    private static Properties configureProperties(Properties properties, ItemSpec spec) {
                        RepairMaterial material = spec.repairMaterial().orElse(null);
                        if (material == null) return properties;
                        if (material.kind() == RepairMaterial.Kind.ITEM) {
                            return properties.repairable(net.minecraft.core.registries.BuiltInRegistries.ITEM
                                    .getValue(%s));
                        }
                        return properties.repairable(net.minecraft.tags.TagKey.create(
                                net.minecraft.core.registries.Registries.ITEM, %s));
                    }

                """.formatted(nativeId, nativeId);
    }

    private static String legacyStorage() {
        return """
                    private static Object readValue(ItemStack stack, ItemDataKey<?> key) {
                        net.minecraft.nbt.CompoundTag tag = stack.getTagElement("enderfall_sdk");
                        if (tag == null) return null;
                        String name = key.id().toString();
                        return switch (key.type()) {
                            case STRING -> tag.contains(name, net.minecraft.nbt.Tag.TAG_STRING) ? tag.getString(name) : null;
                            case BOOLEAN -> tag.contains(name, net.minecraft.nbt.Tag.TAG_BYTE) ? tag.getBoolean(name) : null;
                            case INTEGER -> tag.contains(name, net.minecraft.nbt.Tag.TAG_INT) ? tag.getInt(name) : null;
                            case LONG -> tag.contains(name, net.minecraft.nbt.Tag.TAG_LONG) ? tag.getLong(name) : null;
                            case DOUBLE -> tag.contains(name, net.minecraft.nbt.Tag.TAG_DOUBLE) ? tag.getDouble(name) : null;
                        };
                    }

                    private static void writeValue(ItemStack stack, ItemDataKey<?> key, Object value) {
                        net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTagElement("enderfall_sdk");
                        String name = key.id().toString();
                        switch (key.type()) {
                            case STRING -> tag.putString(name, (String) value);
                            case BOOLEAN -> tag.putBoolean(name, (Boolean) value);
                            case INTEGER -> tag.putInt(name, (Integer) value);
                            case LONG -> tag.putLong(name, (Long) value);
                            case DOUBLE -> tag.putDouble(name, (Double) value);
                        }
                    }

                    private static void removeValue(ItemStack stack, ItemDataKey<?> key) {
                        net.minecraft.nbt.CompoundTag tag = stack.getTagElement("enderfall_sdk");
                        if (tag != null) tag.remove(key.id().toString());
                    }
                """;
    }

    private static String componentStorage() {
        return """
                    private static net.minecraft.nbt.CompoundTag dataTag(ItemStack stack) {
                        net.minecraft.world.item.component.CustomData custom =
                                stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                        if (custom == null) return null;
                        net.minecraft.nbt.CompoundTag all = custom.copyTag();
                        return all.contains("enderfall_sdk", net.minecraft.nbt.Tag.TAG_COMPOUND)
                                ? all.getCompound("enderfall_sdk") : null;
                    }

                    private static Object readValue(ItemStack stack, ItemDataKey<?> key) {
                        net.minecraft.nbt.CompoundTag tag = dataTag(stack);
                        if (tag == null) return null;
                        String name = key.id().toString();
                        return switch (key.type()) {
                            case STRING -> tag.contains(name, net.minecraft.nbt.Tag.TAG_STRING) ? tag.getString(name) : null;
                            case BOOLEAN -> tag.contains(name, net.minecraft.nbt.Tag.TAG_BYTE) ? tag.getBoolean(name) : null;
                            case INTEGER -> tag.contains(name, net.minecraft.nbt.Tag.TAG_INT) ? tag.getInt(name) : null;
                            case LONG -> tag.contains(name, net.minecraft.nbt.Tag.TAG_LONG) ? tag.getLong(name) : null;
                            case DOUBLE -> tag.contains(name, net.minecraft.nbt.Tag.TAG_DOUBLE) ? tag.getDouble(name) : null;
                        };
                    }

                    private static void writeValue(ItemStack stack, ItemDataKey<?> key, Object value) {
                        net.minecraft.world.item.component.CustomData.update(
                                net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, all -> {
                            net.minecraft.nbt.CompoundTag tag = all.contains("enderfall_sdk", net.minecraft.nbt.Tag.TAG_COMPOUND)
                                    ? all.getCompound("enderfall_sdk") : new net.minecraft.nbt.CompoundTag();
                            String name = key.id().toString();
                            switch (key.type()) {
                                case STRING -> tag.putString(name, (String) value);
                                case BOOLEAN -> tag.putBoolean(name, (Boolean) value);
                                case INTEGER -> tag.putInt(name, (Integer) value);
                                case LONG -> tag.putLong(name, (Long) value);
                                case DOUBLE -> tag.putDouble(name, (Double) value);
                            }
                            all.put("enderfall_sdk", tag);
                        });
                    }

                    private static void removeValue(ItemStack stack, ItemDataKey<?> key) {
                        net.minecraft.world.item.component.CustomData.update(
                                net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, all -> {
                            if (!all.contains("enderfall_sdk", net.minecraft.nbt.Tag.TAG_COMPOUND)) return;
                            net.minecraft.nbt.CompoundTag tag = all.getCompound("enderfall_sdk");
                            tag.remove(key.id().toString());
                            all.put("enderfall_sdk", tag);
                        });
                    }
                """;
    }

    private static String unobfuscatedStorage() {
        return """
                    private static net.minecraft.nbt.CompoundTag dataTag(ItemStack stack) {
                        net.minecraft.world.item.component.CustomData custom =
                                stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                        if (custom == null) return null;
                        net.minecraft.nbt.CompoundTag all = custom.copyTag();
                        return all.getCompound("enderfall_sdk").orElse(null);
                    }

                    private static Object readValue(ItemStack stack, ItemDataKey<?> key) {
                        net.minecraft.nbt.CompoundTag tag = dataTag(stack);
                        if (tag == null) return null;
                        String name = key.id().toString();
                        return switch (key.type()) {
                            case STRING -> tag.getString(name).orElse(null);
                            case BOOLEAN -> tag.getBoolean(name).orElse(null);
                            case INTEGER -> tag.getInt(name).orElse(null);
                            case LONG -> tag.getLong(name).orElse(null);
                            case DOUBLE -> tag.getDouble(name).orElse(null);
                        };
                    }

                    private static void writeValue(ItemStack stack, ItemDataKey<?> key, Object value) {
                        net.minecraft.world.item.component.CustomData.update(
                                net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, all -> {
                            net.minecraft.nbt.CompoundTag tag = all.getCompoundOrEmpty("enderfall_sdk");
                            String name = key.id().toString();
                            switch (key.type()) {
                                case STRING -> tag.putString(name, (String) value);
                                case BOOLEAN -> tag.putBoolean(name, (Boolean) value);
                                case INTEGER -> tag.putInt(name, (Integer) value);
                                case LONG -> tag.putLong(name, (Long) value);
                                case DOUBLE -> tag.putDouble(name, (Double) value);
                            }
                            all.put("enderfall_sdk", tag);
                        });
                    }

                    private static void removeValue(ItemStack stack, ItemDataKey<?> key) {
                        net.minecraft.world.item.component.CustomData.update(
                                net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, all -> {
                            net.minecraft.nbt.CompoundTag tag = all.getCompound("enderfall_sdk").orElse(null);
                            if (tag == null) return;
                            tag.remove(key.id().toString());
                            all.put("enderfall_sdk", tag);
                        });
                    }
                """;
    }
}

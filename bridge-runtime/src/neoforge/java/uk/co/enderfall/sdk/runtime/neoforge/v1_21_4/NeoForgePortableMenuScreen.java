package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import uk.co.enderfall.sdk.api.ui.MenuButton;
import uk.co.enderfall.sdk.api.ui.MenuLabel;
import uk.co.enderfall.sdk.api.ui.MenuState;
import uk.co.enderfall.sdk.runtime.PortableMenuView;

/** Target-native renderer for the portable synchronized menu contract. */
final class NeoForgePortableMenuScreen extends Screen {
    private PortableMenuView view;
    private final Consumer<String> actionSender;
    private final Runnable closeSender;
    private final List<ButtonBinding> buttonBindings = new ArrayList<>();
    private boolean remoteClose;
    private boolean closeNotified;

    NeoForgePortableMenuScreen(PortableMenuView view, Consumer<String> actionSender, Runnable closeSender) {
        super(Component.literal(view.state().resolve(view.spec().title())));
        this.view = view;
        this.actionSender = actionSender;
        this.closeSender = closeSender;
    }

    @Override
    protected void init() {
        buttonBindings.clear();
        int left = (width - view.spec().width()) / 2;
        int top = (height - view.spec().height()) / 2;
        for (MenuButton definition : view.spec().buttons()) {
            Button button = Button.builder(Component.literal(view.state().resolve(definition.text())),
                            ignored -> actionSender.accept(definition.action()))
                    .bounds(left + definition.x(), top + definition.y(), definition.width(), definition.height())
                    .build();
            addRenderableWidget(button);
            buttonBindings.add(new ButtonBinding(definition, button));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xB0100D18);
        int left = (width - view.spec().width()) / 2;
        int top = (height - view.spec().height()) / 2;
        graphics.fill(left - 2, top - 2, left + view.spec().width() + 2,
                top + view.spec().height() + 2, 0xFF6C4CA3);
        graphics.fill(left, top, left + view.spec().width(), top + view.spec().height(),
                view.spec().backgroundColor());
        graphics.drawCenteredString(font, view.state().resolve(view.spec().title()),
                left + view.spec().width() / 2, top + 12, 0xFFFFFFFF);
        for (MenuLabel label : view.spec().labels()) {
            String text = view.state().resolve(label.text());
            if (label.centered()) {
                graphics.drawCenteredString(font, text, left + label.x(), top + label.y(), label.color());
            } else {
                graphics.drawString(font, text, left + label.x(), top + label.y(), label.color(), false);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // Screen.render invokes this after our foreground. The background was already drawn
    // first, so do not blur the panel again during widget rendering.
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }
    @Override
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
            binding.button().setMessage(Component.literal(state.resolve(binding.definition().text())));
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

    static void show(PortableMenuView view, Consumer<String> actionSender, Runnable closeSender) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.setScreen(new NeoForgePortableMenuScreen(view, actionSender, closeSender)));
    }

    static void update(long sessionId, MenuState state) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.screen instanceof NeoForgePortableMenuScreen screen && screen.sessionId() == sessionId) {
                screen.update(state);
            }
        });
    }

    static void close(long sessionId) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.screen instanceof NeoForgePortableMenuScreen screen && screen.sessionId() == sessionId) {
                screen.closeFromServer();
            }
        });
    }

    private record ButtonBinding(MenuButton definition, Button button) {
    }
}

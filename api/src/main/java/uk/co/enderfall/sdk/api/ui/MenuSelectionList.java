package uk.co.enderfall.sdk.api.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Paged, server-owned selection rows rendered without consumer client code. */
public final class MenuSelectionList {
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_.-]{0,39}");
    private final String key;
    private final int x;
    private final int y;
    private final int width;
    private final int rowHeight;
    private final int visibleRows;

    public MenuSelectionList(String key, int x, int y, int width, int rowHeight, int visibleRows) {
        this.key = Objects.requireNonNull(key, "key");
        if (!KEY.matcher(key).matches()) throw new IllegalArgumentException("Invalid selection-list key: " + key);
        if (x < 0 || y < 0 || width < 60 || width > 300) {
            throw new IllegalArgumentException("Selection-list position or width is invalid");
        }
        if (rowHeight < 14 || rowHeight > 24 || visibleRows < 1 || visibleRows > 8) {
            throw new IllegalArgumentException("Selection lists need 1-8 rows of 14-24 pixels");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.rowHeight = rowHeight;
        this.visibleRows = visibleRows;
    }

    public static MenuSelectionList of(String key, int x, int y, int width, int visibleRows) {
        return new MenuSelectionList(key, x, y, width, 20, visibleRows);
    }

    public String key() { return key; }
    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int rowHeight() { return rowHeight; }
    public int visibleRows() { return visibleRows; }
    public int height() { return visibleRows * rowHeight + 20; }

    /** Resolves an already server-validated action belonging to this list. */
    public Optional<MenuListAction> action(MenuActionContext context) {
        Objects.requireNonNull(context, "context");
        return resolve(context.action(), context.state());
    }

    List<MenuButton> buttons() {
        List<MenuButton> result = new ArrayList<>();
        for (int row = 0; row < visibleRows; row++) {
            result.add(new MenuButton(selectAction(row), "{" + labelKey(row) + "}", x,
                    y + row * rowHeight, width, rowHeight));
        }
        int half = width / 2;
        result.add(new MenuButton(previousAction(), "<", x, y + visibleRows * rowHeight, half, 20));
        result.add(new MenuButton(nextAction(), ">", x + half, y + visibleRows * rowHeight,
                width - half, 20));
        return List.copyOf(result);
    }

    boolean owns(String action) {
        if (previousAction().equals(action) || nextAction().equals(action)) return true;
        for (int row = 0; row < visibleRows; row++) if (selectAction(row).equals(action)) return true;
        return false;
    }

    boolean enabled(String action, MenuState state) {
        if (previousAction().equals(action)) return Boolean.parseBoolean(state.value(previousEnabledKey()));
        if (nextAction().equals(action)) return Boolean.parseBoolean(state.value(nextEnabledKey()));
        for (int row = 0; row < visibleRows; row++) {
            if (selectAction(row).equals(action)) {
                return !state.value(idKey(row)).isBlank()
                        && Boolean.parseBoolean(state.value(enabledKey(row)));
            }
        }
        return false;
    }

    Optional<MenuListAction> resolve(String action, MenuState state) {
        if (!owns(action) || !enabled(action, state)) return Optional.empty();
        if (previousAction().equals(action)) {
            return Optional.of(new MenuListAction(MenuListAction.Type.PREVIOUS_PAGE, -1, "", ""));
        }
        if (nextAction().equals(action)) {
            return Optional.of(new MenuListAction(MenuListAction.Type.NEXT_PAGE, -1, "", ""));
        }
        for (int row = 0; row < visibleRows; row++) {
            if (selectAction(row).equals(action)) {
                return Optional.of(new MenuListAction(MenuListAction.Type.SELECT, row,
                        state.value(idKey(row)), state.value(labelKey(row))));
            }
        }
        return Optional.empty();
    }

    String selectAction(int row) { return key + ".select." + row; }
    String previousAction() { return key + ".previous"; }
    String nextAction() { return key + ".next"; }
    String idKey(int row) { return key + ".row." + row + ".id"; }
    String labelKey(int row) { return key + ".row." + row + ".label"; }
    String enabledKey(int row) { return key + ".row." + row + ".enabled"; }
    String previousEnabledKey() { return key + ".previous.enabled"; }
    String nextEnabledKey() { return key + ".next.enabled"; }
    String pageKey() { return key + ".page"; }
    String pagesKey() { return key + ".pages"; }
    String selectedKey() { return key + ".selected"; }
}

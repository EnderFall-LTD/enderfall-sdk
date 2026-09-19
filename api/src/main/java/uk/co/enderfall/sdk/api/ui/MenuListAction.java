package uk.co.enderfall.sdk.api.ui;

import java.util.Objects;

/** Validated action from one portable selection list. */
public record MenuListAction(Type type, int visibleRow, String entryId, String label) {
    public MenuListAction {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(label, "label");
        if (type == Type.SELECT && (visibleRow < 0 || entryId.isBlank())) {
            throw new IllegalArgumentException("A selection action needs a visible row and entry ID");
        }
        if (type != Type.SELECT && (visibleRow != -1 || !entryId.isEmpty() || !label.isEmpty())) {
            throw new IllegalArgumentException("A paging action cannot contain an entry");
        }
    }

    public enum Type { SELECT, PREVIOUS_PAGE, NEXT_PAGE }
}

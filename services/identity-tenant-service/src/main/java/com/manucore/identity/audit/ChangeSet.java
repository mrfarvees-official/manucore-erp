package com.manucore.identity.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ChangeSet {

    private static final int MAX = 1000;

    private final List<FieldChange> changes = new ArrayList<>();

    /** Records a field only when its value differs. */
    public ChangeSet add(String field, Object from, Object to) {
        String before = text(from);
        String after = text(to);

        if (!Objects.equals(before, after)) {
            changes.add(new FieldChange(field, before, after));
        }

        return this;
    }

    /** Records that a field changed without storing its value (passwords). */
    public ChangeSet note(String field, String description) {
        changes.add(new FieldChange(field, null, description));

        return this;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public List<FieldChange> asList() {
        return List.copyOf(changes);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value);

        return text.length() > MAX ? text.substring(0, MAX - 1) + "…" : text;
    }
}
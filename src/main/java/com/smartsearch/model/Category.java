package com.smartsearch.model;

public enum Category {
    ALL("All"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    NEWS("News"),
    MAPS("Maps"),
    SHOPPING("Shopping");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Category fromString(String text) {
        if (text == null || text.isBlank()) {
            return ALL;
        }
        for (Category c : Category.values()) {
            if (c.name().equalsIgnoreCase(text.trim()) || c.displayName.equalsIgnoreCase(text.trim())) {
                return c;
            }
        }
        return ALL;
    }
}


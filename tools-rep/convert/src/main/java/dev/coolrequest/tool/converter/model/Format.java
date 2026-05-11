package dev.coolrequest.tool.converter.model;

public final class Format {
    private final String id;
    private final String name;
    private final String category;
    private final String description;

    public Format(String id, String name, String category, String description) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Format)) return false;
        Format format = (Format) o;
        return id.equals(format.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

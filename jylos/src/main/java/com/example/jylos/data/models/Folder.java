package com.example.jylos.data.models;

import java.io.Serializable;

import com.example.jylos.data.models.abstractLayers.CompositeModel;

/**
 * Represents a folder (or notebook) in the application.
 * This class extends CompositeModel to inherit hierarchical properties
 * and implements Serializable for persistence.
 */
public class Folder extends CompositeModel implements Serializable {
    private static final long serialVersionUID = 1L;

    public Folder(String id, String title) {
        super(id, title, null, null);
    }

    public Folder(String title) {
        super(title, null, null);
    }

    public Folder(String id, String title, String createdDate, String modifiedDate) {
        super(id, title, createdDate, modifiedDate);
    }

    public Folder(String title, String createdDate, String modifiedDate) {
        super(title, createdDate, modifiedDate);
    }

    public Boolean isEmpty() {
        return getChildren().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Folder folder = (Folder) o;
        if (getId() != null && folder.getId() != null) {
            return getId().equals(folder.getId());
        }
        return getTitle().equals(folder.getTitle());
    }

    @Override
    public int hashCode() {
        if (getId() != null) {
            return getId().hashCode();
        }
        return getTitle().hashCode();
    }

    @Override
    public String toString() {
        return "Folder{" +
                "id=" + getId() +
                ", title='" + getTitle() + '\'' +
                ", children=" + getChildren() + "}";
    }
}

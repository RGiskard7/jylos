package com.example.jylos.data.models;

import java.io.Serializable;

import com.example.jylos.data.models.abstractLayers.BaseModel;

/**
 * Represents a tag in the application.
 * A tag is used to categorize and organize notes.
 * It consists of an ID, title, and optional timestamps for creation and
 * modification.
 */
public class Tag extends BaseModel implements Serializable {
    private static final long serialVersionUID = 1L;

    public Tag(String id, String title) {
        super(id, title, null, null);
    }

    public Tag(String title) {
        super(title, null, null);
    }

    public Tag(String title, String createdDate, String modifiedDate) {
        super(title, createdDate, modifiedDate);
    }

    public Tag(String id, String title, String createdDate, String modifiedDate) {
        super(id, title, createdDate, modifiedDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Tag tag = (Tag) o;
        return getTitle().equals(tag.getTitle());
    }

    @Override
    public String toString() {
        return "Tag{" +
                "id=" + getId() +
                ", title='" + getTitle() + '\'' +
                '}';
    }
}
package com.example.jylos.data.models.abstractLayers;

import java.util.Objects;

/**
 * Abstract base class for models that include an ID, title, and timestamps.
 * This serves as a foundation for other data models in the application.
 */
public abstract class BaseModel {
	private String id;
	private String title;
	private String createdDate;
	private String modifiedDate;

	/**
	 * Constructs a BaseModel with all attributes.
	 *
	 * @param id           The unique identifier of the model.
	 * @param title        The title of the model.
	 * @param createdDate  The creation date of the model.
	 * @param modifiedDate The last modified date of the model.
	 */
	public BaseModel(String id, String title, String createdDate, String modifiedDate) {
		this.id = id;
		this.title = title;
		this.createdDate = createdDate;
		this.modifiedDate = modifiedDate;
	}

	/**
	 * Constructs a BaseModel without an ID, assuming it will be assigned later.
	 *
	 * @param title        The title of the model.
	 * @param createdDate  The creation date of the model.
	 * @param modifiedDate The last modified date of the model.
	 */

	public BaseModel(String title, String createdDate, String modifiedDate) {
		this(null, title, createdDate, modifiedDate);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(String createdDate) {
		this.createdDate = createdDate;
	}

	public String getModifiedDate() {
		return modifiedDate;
	}

	public void setModifiedDate(String modifiedDate) {
		this.modifiedDate = modifiedDate;
	}

	@Override
	public boolean equals(Object obj) { // Revisar
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BaseModel other = (BaseModel) obj;
		return Objects.equals(createdDate, other.createdDate) && Objects.equals(id, other.id)
				&& Objects.equals(modifiedDate, other.modifiedDate) && Objects.equals(title, other.title);
	}

	@Override
	public int hashCode() {
		return Objects.hash(title);
	}

	@Override
	public String toString() {
		return "Model{" +
				"id=" + id +
				", title='" + title + '\'' +
				'}';
	}
}

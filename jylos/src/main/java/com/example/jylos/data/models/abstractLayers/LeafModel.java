package com.example.jylos.data.models.abstractLayers;

import java.util.List;

import com.example.jylos.data.models.interfaces.Component;

/**
 * Abstract class representing a leaf node in a hierarchical structure.
 * A leaf node is a component that cannot have children.
 * This class extends {@link BaseModel} and implements {@link Component}.
 */
public abstract class LeafModel extends BaseModel implements Component {

	/**
	 * The parent component of this leaf model.
	 */
	private Component parent = null;

	/**
	 * Constructs a LeafModel with an ID, title, creation date, and modification
	 * date.
	 *
	 * @param id           The unique identifier of the model.
	 * @param title        The title of the model.
	 * @param createdDate  The creation date of the model.
	 * @param modifiedDate The last modified date of the model.
	 */
	public LeafModel(String id, String title, String createdDate, String modifiedDate) {
		super(id, title, createdDate, modifiedDate);
	}

	/**
	 * Constructs a LeafModel with a title, creation date, and modification date,
	 * assuming the ID will be assigned later.
	 *
	 * @param title        The title of the model.
	 * @param createdDate  The creation date of the model.
	 * @param modifiedDate The last modified date of the model.
	 */
	public LeafModel(String title, String createdDate, String modifiedDate) {
		super(title, createdDate, modifiedDate);
	}

	@Override
	public Component getParent() {
		return parent;
	}

	@Override
	public void setParent(Component parent) {
		this.parent = parent;

	}

	@Override
	public void add(Component component) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void addAll(List<Component> components) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setChildren(List<Component> components) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void remove(Component component) {
		throw new UnsupportedOperationException();

	}

	@Override
	public List<Component> getChildren() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getPath() {
		Component parentFolder = getParent();
		if (parentFolder == null) {
			return "/" + getTitle();
		} else {
			return parentFolder.getPath() + "/" + getTitle();
		}
	}
}
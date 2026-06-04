package com.example.jylos.data.models.abstractLayers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.jylos.data.models.interfaces.Component;

/**
 * Abstract class that represents a composite model in a hierarchical structure.
 * It extends {@link BaseModel} and implements {@link Component}, allowing
 * objects to have parent-child relationships.
 */
public abstract class CompositeModel extends BaseModel implements Component {

	/**
	 * The parent component of this composite model.
	 */
	private Component parent = null;

	/**
	 * A set containing the child components of this composite model.
	 */
	private Set<Component> children = new HashSet<>();

	/**
	 * Constructs a CompositeModel with an ID, title, creation date, and
	 * modification date.
	 *
	 * @param id           The unique identifier of the model.
	 * @param id           The unique identifier of the model.
	 * @param title        The title of the model.
	 * @param createdDate  The creation date of the model.
	 * @param modifiedDate The last modified date of the model.
	 */
	public CompositeModel(String id, String title, String createdDate, String modifiedDate) {
		super(id, title, createdDate, modifiedDate);
	}

	/**
	 * Constructs a CompositeModel with a title, creation date, and modification
	 * date, assuming the ID will be assigned later.
	 *
	 * @param title        The title of the model.
	 * @param createdDate  The creation date of the model.
	 * @param modifiedDate The last modified date of the model.
	 */
	public CompositeModel(String title, String createdDate, String modifiedDate) {
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
		if (component != null) {
			children.add(component);
		}
	}

	@Override
	public void addAll(List<Component> components) {
		if (components != null && !components.isEmpty()) {
			children.addAll(components);
		}
	}

	@Override
	public void setChildren(List<Component> components) {
		if (components != null) {
			this.children = new HashSet<>(components);
		}
	}

	@Override
	public void remove(Component component) {
		if (component != null) {
			children.remove(component);
		}
	}

	@Override
	public List<Component> getChildren() {
		return new ArrayList<>(children);
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
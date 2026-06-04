package com.example.jylos.data.models.interfaces;

import java.util.List;

/**
 * This interface defines the contract for hierarchical components in the
 * application.
 * It allows elements to have a parent-child relationship, supporting
 * hierarchical structures.
 */
public interface Component {

    /**
     * Retrieves the unique identifier of the component.
     *
     * @return The ID of the component.
     */
    public String getId();

    /**
     * Sets the unique identifier of the component.
     *
     * @param id The new ID to be assigned.
     */
    public void setId(String id);

    /**
     * Retrieves the title of the component.
     *
     * @return The title of the component.
     */
    public String getTitle();

    /**
     * Sets the title of the component.
     *
     * @param title The new title to be assigned.
     */
    public void setTitle(String title);

    /**
     * Retrieves the parent component in the hierarchy.
     *
     * @return The parent component, or null if this is a root element.
     */
    public Component getParent();

    /**
     * Sets the parent component in the hierarchy.
     *
     * @param parent The parent component to be assigned.
     */
    public void setParent(Component parent);

    /**
     * Adds a child component to this component.
     *
     * @param component The component to be added.
     */
    public void add(Component component);

    /**
     * Adds multiple child components to this component.
     *
     * @param components A list of components to be added.
     */
    public void addAll(List<Component> components);

    /**
     * Sets the children of this component, replacing existing ones.
     *
     * @param components A list of components to be set as children.
     */
    public void setChildren(List<Component> components);

    /**
     * Removes a child component from this component.
     *
     * @param component The component to be removed.
     */
    public void remove(Component component);

    /**
     * Retrieves the list of child components of this component.
     *
     * @return A list of child components.
     */

    public List<Component> getChildren();

    /**
     * Retrieves the hierarchical path of this component based on its parent
     * relationships.
     *
     * @return A string representing the component's path.
     */
    public String getPath();
}

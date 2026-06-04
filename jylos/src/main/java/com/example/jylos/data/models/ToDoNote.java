package com.example.jylos.data.models;

/**
 * Represents a specialized type of Note that includes To-Do attributes.
 * A ToDoNote extends a regular Note by adding due date and completion status.
 */
public class ToDoNote extends Note {
	private static final long serialVersionUID = 1L;
	private String toDoDue = null;
	private String toDoCompleted = null;

	/**
	 * Constructs a ToDoNote with all attributes.
	 *
	 * @param id               The unique identifier of the ToDoNote.
	 * @param title            The title of the ToDoNote.
	 * @param content          The content of the ToDoNote.
	 * @param createdDate      The creation date of the ToDoNote.
	 * @param modifiedDate     The last modification date of the ToDoNote.
	 * @param toDoDue          The due date of the ToDo task.
	 * @param toDoDueCompleted The completion date of the ToDo task.
	 */
	public ToDoNote(String id, String title, String content, String createdDate, String modifiedDate,
			String toDoDue, String toDoDueCompleted) {
		super(id, title, content, createdDate, modifiedDate);
		this.toDoDue = toDoDue;
		this.toDoCompleted = toDoDueCompleted;
	}

	/**
	 * Constructs a ToDoNote without To-Do specific attributes.
	 *
	 * @param id           The unique identifier of the ToDoNote.
	 * @param title        The title of the ToDoNote.
	 * @param content      The content of the ToDoNote.
	 * @param createdDate  The creation date of the ToDoNote.
	 * @param modifiedDate The last modification date of the ToDoNote.
	 */
	public ToDoNote(String id, String title, String content, String createdDate, String modifiedDate) {
		super(id, title, content, createdDate, modifiedDate);
	}

	public String getToDoDue() {
		return toDoDue;
	}

	public void setToDoDue(String toDoDue) {
		this.toDoDue = toDoDue;
	}

	public String getToDoCompleted() {
		return toDoCompleted;
	}

	public void setToDoCompleted(String toDoCompleted) {
		this.toDoCompleted = toDoCompleted;
	}
}
package com.example.jylos.data.dao.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.data.models.ToDoNote;
import com.example.jylos.exceptions.InvalidParameterException;

/**
 * SQLite implementation of the NoteDAO interface.
 * This class provides methods for interacting with notes in the SQLite
 * database,
 * including creation, retrieval, updating, deletion, and tag management.
 */
public class NoteDAOSQLite implements NoteDAO {

	// SQL Queries
	private static final String INSERT_NOTE_SQL = "INSERT INTO notes (note_id, title, content, created_date, modified_date, "
			+ "latitude, longitude, author, source_url, source, source_application, is_todo, todo_due, todo_completed, is_favorite, is_pinned, is_deleted, deleted_date, parent_id) "
			+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

	private static final String INSERT_TAG_NOTE_SQL = "INSERT INTO tagsNotes (id, tag_id, note_id, added_date) VALUES (?, ?, ?, ?)";

	private static final String SELECT_NOTE_BY_ID_SQL = "SELECT notes.* FROM notes LEFT JOIN folders ON notes.parent_id = folders.folder_id "
			+ "WHERE notes.note_id = ?";

	private static final String SELECT_NOTES_BY_FOLDER_ID_SQL = "SELECT * FROM notes WHERE parent_id = ? AND is_deleted = 0";

	private static final String SELECT_ALL_NOTES_SQL = "SELECT * FROM notes WHERE is_deleted = 0";

	private static final String SELECT_ALL_TAGS_NOTE_SQL = "SELECT DISTINCT tags.tag_id, title, created_date, modified_date "
			+ "FROM tagsNotes NATURAL JOIN tags WHERE note_id = ?";

	private static final String SELECT_NOTES_BY_TAG_ID_SQL = "SELECT DISTINCT notes.* FROM notes "
			+ "INNER JOIN tagsNotes ON notes.note_id = tagsNotes.note_id WHERE tagsNotes.tag_id = ? AND notes.is_deleted = 0";

	private static final String UPDATE_NOTE_SQL = "UPDATE notes SET title = ?, content = ?, modified_date = ?, is_favorite = ?, is_pinned = ?, parent_id = ? WHERE note_id = ?";

	private static final String SOFT_DELETE_NOTE_SQL = "UPDATE notes SET is_deleted = 1, deleted_date = ? WHERE note_id = ?";

	private static final String RESTORE_NOTE_SQL = "UPDATE notes SET is_deleted = 0, deleted_date = NULL WHERE note_id = ?";

	private static final String DELETE_NOTE_SQL = "DELETE FROM notes WHERE note_id = ?";

	private static final String SELECT_TRASH_NOTES_SQL = "SELECT * FROM notes WHERE is_deleted = 1";

	private static final String DELETE_TAG_NOTE_SQL = "DELETE FROM tagsNotes WHERE tag_id = ? AND note_id = ?";

	private static final Logger logger = LoggerConfig.getLogger(NoteDAOSQLite.class);
	private Connection connection;

	/**
	 * Constructs a NoteDAOSQLite with the given database connection.
	 *
	 * @param connection The database connection to be used.
	 */
	public NoteDAOSQLite(Connection connection) {
		this.connection = connection;
	}

	// CRUD Methods
	@Override
	public String createNote(Note note) {
		String newId = null;

		if (note == null) {
			throw new InvalidParameterException("Note object cannot be null");
		}

		if (note.getId() == null || note.getId().isEmpty()) {
			note.setId(UUID.randomUUID().toString());
		}
		newId = note.getId();

		try (PreparedStatement pstmt = connection.prepareStatement(INSERT_NOTE_SQL)) {

			pstmt.setString(1, newId);
			pstmt.setString(2, note.getTitle());
			pstmt.setString(3, note.getContent());
			pstmt.setString(4, DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
			pstmt.setString(5, note.getModifiedDate());
			pstmt.setDouble(6, note.getLatitude() != null ? note.getLatitude() : 0.0);
			pstmt.setDouble(7, note.getLongitude() != null ? note.getLongitude() : 0.0);
			pstmt.setString(8, note.getAuthor());
			pstmt.setString(9, note.getSourceUrl());
			pstmt.setString(10, note.getSource());
			pstmt.setString(11, note.getSourceApplication());

			if (note instanceof ToDoNote) {
				pstmt.setInt(12, 1); // is_todo
				pstmt.setString(13, ((ToDoNote) note).getToDoDue());
				pstmt.setString(14, ((ToDoNote) note).getToDoCompleted());
			} else {
				pstmt.setInt(12, 0);
				pstmt.setString(13, null);
				pstmt.setString(14, null);
			}

			pstmt.setInt(15, note.isFavorite() ? 1 : 0); // is_favorite
			pstmt.setInt(16, note.isPinned() ? 1 : 0); // is_pinned
			pstmt.setInt(17, note.isDeleted() ? 1 : 0); // is_deleted
			pstmt.setString(18, note.getDeletedDate()); // deleted_date
			pstmt.setString(19,
					(note.getParent() != null && !"ROOT".equals(note.getParent().getId())) ? note.getParent().getId()
							: null);

			pstmt.executeUpdate();

			connection.commit(); // Confirmar transacción
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error createNote(): " + e.getMessage(), e);
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				logger.log(Level.SEVERE, "Error rolling back transaction: " + rollbackEx.getMessage(), rollbackEx);
			}
			return null;
		}

		return newId; // Retorna el ID de la nueva nota
	}

	@Override
	public Note getNoteById(String id) {
		Note note = null;

		if (id == null || id.isEmpty()) {
			throw new IllegalArgumentException("Note ID cannot be null or empty");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(SELECT_NOTE_BY_ID_SQL)) {
			pstmt.setString(1, id);

			try (ResultSet rs = pstmt.executeQuery()) {
				// Process the result set
				if (rs.next()) {
					note = mapResultSetToNote(rs);
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error getNoteById(): " + e.getMessage(), e);
		}

		return note;
	}

	@Override
	public void updateNote(Note note) {
		if (note == null) {
			throw new IllegalArgumentException("Note object cannot be null");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(UPDATE_NOTE_SQL)) {
			pstmt.setString(1, note.getTitle());
			pstmt.setString(2, note.getContent());
			pstmt.setString(3, DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
			pstmt.setInt(4, note.isFavorite() ? 1 : 0);
			pstmt.setInt(5, note.isPinned() ? 1 : 0);
			pstmt.setString(6,
					(note.getParent() != null && !"ROOT".equals(note.getParent().getId())) ? note.getParent().getId()
							: null);
			pstmt.setString(7, note.getId());
			pstmt.executeUpdate();
			connection.commit();

		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error updateNote(): " + e.getMessage(), e);
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				logger.log(Level.SEVERE, "Error rolling back transaction: " + rollbackEx.getMessage(), rollbackEx);
			}
		}
	}

	@Override
	public void deleteNote(String id) {
		if (id == null || id.isEmpty()) {
			throw new IllegalArgumentException("Note ID cannot be null or empty");
		}

		try {
			softDeleteNoteWithoutCommit(id);
			connection.commit();
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error deleteNote() (Soft Delete): " + e.getMessage(), e);
			rollbackQuietly();
		}
	}

	void softDeleteNoteWithoutCommit(String id) throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement(SOFT_DELETE_NOTE_SQL)) {
			pstmt.setString(1, DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
			pstmt.setString(2, id);
			pstmt.executeUpdate();
		}
	}

	void restoreNoteWithoutCommit(String id) throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement(RESTORE_NOTE_SQL)) {
			pstmt.setString(1, id);
			pstmt.executeUpdate();
		}
	}

	private void rollbackQuietly() {
		try {
			connection.rollback();
		} catch (SQLException rollbackEx) {
			logger.log(Level.SEVERE, "Error rolling back transaction: " + rollbackEx.getMessage(), rollbackEx);
		}
	}

	@Override
	public void permanentlyDeleteNote(String id) {
		if (id == null || id.isEmpty()) {
			throw new IllegalArgumentException("Note ID cannot be null or empty");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(DELETE_NOTE_SQL)) {
			pstmt.setString(1, id);
			pstmt.executeUpdate();
			connection.commit();

		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error permanentlyDeleteNote(): " + e.getMessage(), e);
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				logger.log(Level.SEVERE, "Error rolling back transaction: " + rollbackEx.getMessage(), rollbackEx);
			}
		}
	}

	@Override
	public void restoreNote(String id) {
		if (id == null || id.isEmpty()) {
			throw new IllegalArgumentException("Note ID cannot be null or empty");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(RESTORE_NOTE_SQL)) {
			pstmt.setString(1, id);
			pstmt.executeUpdate();
			connection.commit();

		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error restoreNote(): " + e.getMessage(), e);
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				logger.log(Level.SEVERE, "Error rolling back transaction: " + rollbackEx.getMessage(), rollbackEx);
			}
		}
	}

	@Override
	public List<Note> fetchTrashNotes() {
		List<Note> list = new ArrayList<>();
		try (Statement stmt = connection.createStatement()) {
			try (ResultSet rs = stmt.executeQuery(SELECT_TRASH_NOTES_SQL)) {
				while (rs.next()) {
					list.add(mapResultSetToNote(rs));
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error fetchTrashNotes(): " + e.getMessage(), e);
		}
		return list;
	}

	// Retrieval Methods
	@Override
	public List<Note> fetchNotesByFolderId(String folderId) {
		List<Note> list = new ArrayList<>();

		String sql = (folderId == null || folderId.isEmpty() || "ROOT".equals(folderId))
				? "SELECT * FROM notes WHERE (parent_id IS NULL OR parent_id = '') AND is_deleted = 0"
				: SELECT_NOTES_BY_FOLDER_ID_SQL;

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			if (!sql.contains("IS NULL")) {
				pstmt.setString(1, folderId);
			}

			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					list.add(mapResultSetToNote(rs));
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error fetchNotesByFolderId(): " + e.getMessage(), e);
		}

		return list;
	}

	@Override
	public void fetchNotesByFolderId(Folder folder) {
		if (folder == null) {
			throw new IllegalArgumentException("Folder object can't be null");
		}

		List<Note> notes = fetchNotesByFolderId(folder.getId());
		for (Note note : notes) {
			folder.add(note);
			note.setParent(folder);
		}
	}

	@Override
	public List<Note> fetchAllNotes() {
		List<Note> list = new ArrayList<>();

		try (Statement stmt = connection.createStatement()) {
			try (ResultSet rs = stmt.executeQuery(SELECT_ALL_NOTES_SQL)) {
				while (rs.next()) {
					list.add(mapResultSetToNote(rs));
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error fetchAllNotes(): " + e.getMessage(), e);
		}

		return list;
	}

	@Override
	public Folder getFolderOfNote(String noteId) {
		FolderDAO folderDAO = new FolderDAOSQLite(connection);
		return folderDAO.getFolderByNoteId(noteId);
	}

	// Tag Management Methods
	@Override
	public void addTag(String noteId, String tagId) {
		if (noteId == null || noteId.isEmpty() || tagId == null || tagId.isEmpty()) {
			throw new IllegalArgumentException("Note ID and tag ID must not be null or empty");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(INSERT_TAG_NOTE_SQL)) {
			pstmt.setString(1, UUID.randomUUID().toString()); // tagsNotes ID
			pstmt.setString(2, tagId);
			pstmt.setString(3, noteId);
			pstmt.setString(4, DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
			pstmt.executeUpdate();
			connection.commit();

		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error addTag(): " + e.getMessage(), e);
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				logger.log(Level.SEVERE, "Error rolling back transaction: " + rollbackEx.getMessage(), rollbackEx);
			}
		}
	}

	@Override
	public void addTag(Note note, Tag tag) {
		if (note == null || tag == null) {
			throw new InvalidParameterException("Note object or tag object are null");
		}

		addTag(note.getId(), tag.getId());
	}

	@Override
	public void removeTag(String noteId, String tagId) {
		if (tagId == null || tagId.isEmpty() || noteId == null || noteId.isEmpty()) {
			throw new IllegalArgumentException("Note ID and tag ID must not be null or empty");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(DELETE_TAG_NOTE_SQL)) {
			pstmt.setString(1, tagId);
			pstmt.setString(2, noteId);
			pstmt.executeUpdate();
			connection.commit();

		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error removeTag(): " + e.getMessage(), e);
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				logger.log(Level.SEVERE, "Error rolling back transaction: " + rollbackEx.getMessage(), rollbackEx);
			}
		}
	}

	@Override
	public void removeTag(Note note, Tag tag) {
		if (note == null || tag == null) {
			throw new InvalidParameterException("Note object or tag object are null");
		}

		removeTag(note.getId(), tag.getId());
	}

	@Override
	public List<Tag> fetchTags(String noteId) {
		if (noteId == null || noteId.isEmpty()) {
			throw new InvalidParameterException("Invalid note ID");
		}

		List<Tag> list = new ArrayList<>();

		try (PreparedStatement pstmt = connection.prepareStatement(SELECT_ALL_TAGS_NOTE_SQL)) {
			pstmt.setString(1, noteId);

			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String id = rs.getString("tag_id");
					String title = rs.getString("title");
					String createdDate = rs.getString("created_date");
					String modifiedDate = rs.getString("modified_date");

					list.add(new Tag(id, title, createdDate, modifiedDate));
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error fetchTags(): " + e.getMessage(), e);
		}

		return list;
	}

	@Override
	public void loadTags(Note note) {
		if (note == null) {
			throw new InvalidParameterException("Note object cannot be null");
		}

		List<Tag> tags = fetchTags(note.getId());
		note.addAllTags(tags);
	}

	@Override
	public List<Note> fetchNotesByTagId(String tagId) {
		if (tagId == null || tagId.isEmpty()) {
			throw new InvalidParameterException("Invalid tag ID");
		}

		List<Note> list = new ArrayList<>();

		try (PreparedStatement pstmt = connection.prepareStatement(SELECT_NOTES_BY_TAG_ID_SQL)) {
			pstmt.setString(1, tagId);

			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					list.add(mapResultSetToNote(rs));
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error fetchNotesByTagId(): " + e.getMessage(), e);
		}

		return list;
	}

	// Helper Methods (protected/private)
	public Note mapResultSetToNote(ResultSet rs) throws SQLException {
		Note note = null;

		if (rs != null) {
			String noteId = rs.getString("note_id");
			String title = rs.getString("title");
			String content = rs.getString("content");
			String createdDate = rs.getString("created_date");
			String modifiedDate = rs.getString("modified_date");
			double latitude = rs.getDouble("latitude");
			double longitude = rs.getDouble("longitude");
			String author = rs.getString("author");
			String sourceUrl = rs.getString("source_url");
			int isToDo = rs.getInt("is_todo");
			String toDoDue = rs.getString("todo_due");
			String toDoCompleted = rs.getString("todo_completed");
			String source = rs.getString("source");
			String sourceApplication = rs.getString("source_application");
			int isFavorite = 0;
			int isPinned = 0;
			int isDeleted = 0;
			String deletedDate = null;
			try {
				isFavorite = rs.getInt("is_favorite");
				isPinned = rs.getInt("is_pinned");
				isDeleted = rs.getInt("is_deleted");
				deletedDate = rs.getString("deleted_date");
			} catch (SQLException e) {
				// Columns might not exist in older databases
				logger.warning("Optional columns not found, using defaults: " + e.getMessage());
			}

			if (isToDo == 1) {
				note = new ToDoNote(noteId, title, content, createdDate, modifiedDate, toDoDue, toDoCompleted);
			} else {
				note = new Note(noteId, title, content, createdDate, modifiedDate);
			}

			note.setLatitude(latitude);
			note.setLongitude(longitude);
			note.setAuthor(author);
			note.setSourceUrl(sourceUrl);
			note.setSource(source);
			note.setSourceApplication(sourceApplication);
			note.setFavorite(isFavorite == 1);
			note.setPinned(isPinned == 1);
			note.setDeleted(isDeleted == 1);
			note.setDeletedDate(deletedDate);

			// Populate parent placeholder for hierarchy reconstruction
			String parentId = rs.getString("parent_id");
			if (parentId != null && !parentId.isEmpty()) {
				note.setParent(new com.example.jylos.data.models.Folder(parentId, ""));
			}

			return note;
		}
		return null;
	}
}

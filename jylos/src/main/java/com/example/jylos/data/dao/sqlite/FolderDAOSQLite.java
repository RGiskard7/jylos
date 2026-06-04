package com.example.jylos.data.dao.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.exceptions.DataAccessException;
import com.example.jylos.exceptions.InvalidParameterException;

/**
 * SQLite implementation of the FolderDAO interface.
 * This class provides methods for interacting with folders in the SQLite
 * database,
 * including creation, retrieval, updating, deletion, and hierarchical
 * management.
 */
public class FolderDAOSQLite implements FolderDAO {

	// SQL Queries
	private static final String INSERT_FOLDER_SQL = "INSERT INTO folders (folder_id, parent_id, title, created_date) VALUES (?, ?, ?, ?)";

	private static final String SELECT_EXIST_TITLE = "SELECT COUNT(*) FROM folders WHERE title = ? AND is_deleted = 0";

	private static final String SELECT_FOLDER_BY_ID_SQL = "SELECT * FROM folders WHERE folder_id = ?";

	private static final String SELECT_FOLDER_BY_NOTE_ID_SQL = "SELECT folder_id, folders.title, folders.created_date, "
			+ "folders.modified_date FROM notes INNER JOIN folders ON notes.parent_id = folders.folder_id WHERE note_id = ? AND folders.is_deleted = 0";

	private static final String SELECT_ALL_FOLDERS_SQL = "SELECT * FROM folders WHERE is_deleted = 0";

	private static final String SELECT_PARENT_FOLDER_SQL = "SELECT parent_id FROM folders WHERE folder_id = ?";

	private static final String SELECT_SUBFOLDERS_SQL = "SELECT * FROM folders WHERE parent_id = ? AND is_deleted = 0";

	private static final String SELECT_SUBFOLDERS_ROOT_SQL = "SELECT * FROM folders WHERE parent_id IS NULL AND is_deleted = 0";

	private static final String UPDATE_FOLDER_SQL = "UPDATE folders SET title = ?, modified_date = ? WHERE folder_id = ?";

	private static final String UPDATE_FOLDER_MODIFIED_DATE_SQL = "UPDATE folders SET modified_date = ? WHERE folder_id = ?";

	private static final String UPDATE_FOLDER_ADD_NOTE_SQL = "UPDATE notes SET parent_id = ?, modified_date = ? WHERE note_id = ?";

	private static final String UPDATE_FOLDER_REMOVE_NOTE_SQL = "UPDATE notes SET parent_id = NULL, modified_date = ? WHERE note_id = ? AND parent_id = ?";

	private static final String UPDATE_FOLDER_ADD_SUBFOLDER_SQL = "UPDATE folders SET parent_id = ?, modified_date = ? WHERE folder_id = ?";

	private static final String UPDATE_FOLDER_REMOVE_SUBFOLDER_SQL = "UPDATE folders SET parent_id = NULL, modified_date = ? WHERE folder_id = ? AND parent_id = ?";

	private static final String SOFT_DELETE_FOLDER_SQL = "UPDATE folders SET is_deleted = 1, deleted_date = ? WHERE folder_id = ?";

	private static final String RESTORE_FOLDER_SQL = "UPDATE folders SET is_deleted = 0, deleted_date = NULL WHERE folder_id = ?";

	private static final String SELECT_TRASH_FOLDERS_SQL = "SELECT * FROM folders WHERE is_deleted = 1";

	private static final String DELETE_FOLDER_SQL = "DELETE FROM folders WHERE folder_id = ?";

	private static final Logger logger = LoggerConfig.getLogger(FolderDAOSQLite.class);
	private Connection connection;

	/**
	 * Constructs a FolderDAOSQLite with the given database connection.
	 *
	 * @param connection The database connection to be used.
	 */
	public FolderDAOSQLite(Connection connection) {
		this.connection = connection;
	}

	// CRUD Methods
	@Override
	public String createFolder(Folder folder) {
		String newId = null;

		if (folder == null) {
			throw new InvalidParameterException("Folder object cannot be null");
		}

		if (folder.getId() == null || folder.getId().isEmpty()) {
			folder.setId(UUID.randomUUID().toString());
		}
		newId = folder.getId();

		try (PreparedStatement pstmt = connection.prepareStatement(INSERT_FOLDER_SQL)) {

			pstmt.setString(1, newId);
			if (folder.getParent() != null && folder.getParent().getId() != null
					&& !"ROOT".equals(folder.getParent().getId())) {
				pstmt.setString(2, folder.getParent().getId());
			} else {
				pstmt.setNull(2, java.sql.Types.VARCHAR);
			}
			pstmt.setString(3, folder.getTitle());
			pstmt.setString(4, DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
			pstmt.executeUpdate();

			connection.commit();

		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error createFolder(): " + e.getMessage(), e);
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				logger.log(Level.SEVERE, "Error rolling back transaction: " + rollbackEx.getMessage(), rollbackEx);
			}
			return null;
		}

		return newId;
	}

	@Override
	public Folder getFolderById(String id) {
		Folder folder = null;

		if (id == null || id.isEmpty()) {
			throw new IllegalArgumentException("Folder ID cannot be null or empty");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(SELECT_FOLDER_BY_ID_SQL)) {
			pstmt.setString(1, id);

			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					folder = mapResultSetToFolder(rs);
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error getFolderById: " + e.getMessage(), e);
		}

		return folder;
	}

	@Override
	public void updateFolder(Folder folder) {
		if (folder == null) {
			throw new InvalidParameterException("Folder object cannot be null");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(UPDATE_FOLDER_SQL)) {
			pstmt.setString(1, folder.getTitle());
			pstmt.setString(2, DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
			pstmt.setString(3, folder.getId());
			pstmt.executeUpdate();
			connection.commit();

		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error updateFolder(): " + e.getMessage(), e);
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				logger.log(Level.SEVERE, "Error rolling back transaction: " + rollbackEx.getMessage(), rollbackEx);
			}
		}
	}

	@Override
	public void deleteFolder(String id) {
		if (id == null || id.isEmpty()) {
			throw new IllegalArgumentException("Folder ID cannot be null or empty");
		}

		NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);
		try {
			deleteFolderInTransaction(id, noteDAO);
			connection.commit();
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error deleteFolder(): " + e.getMessage(), e);
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				logger.log(Level.SEVERE, "Error rolling back transaction: " + rollbackEx.getMessage(), rollbackEx);
			}
		}
	}

	private void deleteFolderInTransaction(String id, NoteDAOSQLite noteDAO) throws SQLException {
		for (Folder sub : fetchSubFoldersImplementation(id)) {
			deleteFolderInTransaction(sub.getId(), noteDAO);
		}
		for (Note note : noteDAO.fetchNotesByFolderId(id)) {
			noteDAO.softDeleteNoteWithoutCommit(note.getId());
		}
		try (PreparedStatement pstmt = connection.prepareStatement(SOFT_DELETE_FOLDER_SQL)) {
			pstmt.setString(1, DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
			pstmt.setString(2, id);
			pstmt.executeUpdate();
		}
	}

	// Retrieval Methods
	@Override
	public Folder getFolderByNoteId(String noteId) {
		Folder folder = null;

		if (noteId == null || noteId.isEmpty()) {
			throw new IllegalArgumentException("Note ID cannot be null or empty");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(SELECT_FOLDER_BY_NOTE_ID_SQL)) {
			pstmt.setString(1, noteId);

			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					folder = mapResultSetToFolder(rs);
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error getFolderByNoteId(): " + e.getMessage(), e);
		}

		return folder;
	}

	@Override
	public List<Folder> fetchAllFoldersAsList() {
		List<Folder> list = new ArrayList<>();

		try (Statement stmt = connection.createStatement()) {
			try (ResultSet rs = stmt.executeQuery(SELECT_ALL_FOLDERS_SQL)) {
				while (rs.next()) {
					list.add(mapResultSetToFolder(rs));
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error fetchAllFoldersAsList(): " + e.getMessage(), e);
		}

		return list;
	}

	@Override
	public Folder fetchAllFoldersAsTree() {
		Folder rootFolder = new Folder("ROOT", null, null);
		// Root folder has no ID or maybe a special one. "ROOT" is title.
		// loadSubFolders(rootFolder) handles null/empty ID as fetching root folders.
		// But Wait, loadSubFoldersHelper checks folder.getId() != null ?
		// SELECT_SUBFOLDERS_SQL : SELECT_SUBFOLDERS_ROOT_SQL;
		// rootFolder created here has null ID? new Folder(String id, String title) or
		// (String title).
		// Folder("ROOT", null, null) calls super("ROOT", null, null) which sets
		// title="ROOT". ID is null.
		// So loadSubFolders(rootFolder) will fetch root folders.
		loadSubFolders(rootFolder);
		return rootFolder;
	}

	// Relationship Management Methods
	@Override
	public void addNote(Folder folder, Note note) {
		if (folder == null || note == null) {
			throw new IllegalArgumentException("Folder object or note object can't be null");
		}

		addNote(folder.getId(), note.getId());
		folder.add(note);
		note.setParent(folder);
	}

	@Override
	public void removeNote(Folder folder, Note note) {
		if (folder == null || note == null) {
			throw new IllegalArgumentException("Note object and folder object can't be null");
		}

		removeNote(folder.getId(), note.getId());
		folder.remove(note);
		note.setParent(null);
	}

	@Override
	public void addSubFolder(Folder parentFolder, Folder subFolder) {
		if (parentFolder == null || subFolder == null
				|| (parentFolder.getId() != null && parentFolder.getId().equals(subFolder.getId()))) {
			throw new IllegalArgumentException(
					"Parent folder object or subfolder object can't be null and can't have the same ID");
		}

		addSubFolder(parentFolder.getId(), subFolder.getId());
		subFolder.setParent(parentFolder);
		parentFolder.add(subFolder);
	}

	@Override
	public void removeSubFolder(Folder parent, Folder subFolder) {
		if (parent == null || subFolder == null
				|| (parent.getId() != null && parent.getId().equals(subFolder.getId()))) {
			throw new IllegalArgumentException(
					"Parent folder object or subfolder object can't be null and can't have the same ID");
		}

		removeSubFolder(parent.getId(), subFolder.getId());
		parent.remove(subFolder);
		subFolder.setParent(null);
	}

	@Override
	public void loadSubFolders(Folder folder, int maxDepth) {
		if (folder == null) {
			throw new InvalidParameterException("Parent folder object is null");
		}

		if (maxDepth < 0) {
			throw new IllegalArgumentException("Maximum depth can't be negative");
		}

		loadSubFoldersHelper(folder, 0, maxDepth);
	}

	@Override
	public void loadSubFolders(Folder folder) {
		loadSubFolders(folder, Integer.MAX_VALUE);
	}

	@Override
	public void loadParentFolders(Folder folder, int maxDepth) {
		if (folder == null) {
			throw new InvalidParameterException("Folder object is null");
		}
		if (maxDepth < 0) {
			throw new IllegalArgumentException("Maximum depth can't be negative");
		}
		loadParentFoldersHelper(folder, 0, maxDepth);
	}

	@Override
	public void loadParentFolders(Folder folder) {
		loadParentFolders(folder, Integer.MAX_VALUE);
	}

	@Override
	public void loadParentFolder(Folder folder) {
		loadParentFolders(folder, 1);
	}

	@Override
	public Folder getParentFolder(String folderId) {
		Folder parentFolder = null;

		if (folderId == null || folderId.isEmpty()) {
			throw new InvalidParameterException("Invalid folder ID");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(SELECT_PARENT_FOLDER_SQL)) {
			pstmt.setString(1, folderId);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					String parentId = rs.getString("parent_id");
					if (parentId != null) {
						parentFolder = getFolderById(parentId);
					}
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error getParentFolder: " + e.getMessage(), e);
		}

		return parentFolder;
	}

	@Override
	public Folder getParentFolder(Folder folder) {
		if (folder == null) {
			throw new InvalidParameterException("Folder object is null or don't have parent folder");
		}

		return getParentFolder(folder.getId());
	}

	@Override
	public String getPathFolder(String idFolder) {
		if (idFolder == null || idFolder.isEmpty()) {
			throw new InvalidParameterException("Invalid folder ID");
		}

		Folder folder = getFolderById(idFolder);
		if (folder == null) {
			throw new InvalidParameterException("Folder not found");
		}

		Folder parentFolder = getParentFolder(idFolder);
		if (parentFolder == null) {
			return "/" + folder.getTitle();
		} else {
			return getPathFolder(parentFolder.getId()) + "/" + folder.getTitle();
		}
	}

	@Override
	public boolean existsByTitle(String title) {
		if (title == null) {
			throw new IllegalArgumentException("Title can't be null");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(SELECT_EXIST_TITLE)) {
			pstmt.setString(1, title);

			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					int countTitle = rs.getInt(1);
					if (countTitle > 0)
						return true;
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error existsByTitle: " + e.getMessage(), e);
		}

		return false;
	}

	// Helper Methods (protected/private)
	protected void addNote(String folderId, String noteId) {
		if (folderId == null || folderId.isEmpty() || noteId == null || noteId.isEmpty()) {
			throw new IllegalArgumentException("Folder ID and note ID must not be null or empty");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(UPDATE_FOLDER_ADD_NOTE_SQL)) {
			if ("ROOT".equals(folderId)) {
				pstmt.setNull(1, java.sql.Types.VARCHAR);
			} else {
				pstmt.setString(1, folderId);
			}
			pstmt.setString(2, DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
			pstmt.setString(3, noteId);
			pstmt.executeUpdate();
			connection.commit();

			if (!"ROOT".equals(folderId)) {
				updateModifiedDateFolder(folderId);
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error addNote(): " + e.getMessage(), e);
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				logger.log(Level.SEVERE, "Error rolling back transaction: " + rollbackEx.getMessage(), rollbackEx);
			}
		}
	}

	@Override
	public void loadNotes(Folder folder) {
		if (folder == null) {
			throw new InvalidParameterException("Parent folder object is null");
		}

		if (!folder.isEmpty()) {
			for (Component subFolder : folder.getChildren()) {
				if (subFolder instanceof Folder) {
					loadNotes((Folder) subFolder);
				}
			}
		}

		NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);
		noteDAO.fetchNotesByFolderId(folder);
	}

	protected void removeNote(String folderId, String noteId) {
		if (folderId == null || folderId.isEmpty() || noteId == null || noteId.isEmpty()) {
			throw new IllegalArgumentException("Note ID and folder ID must not be null or empty");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(UPDATE_FOLDER_REMOVE_NOTE_SQL)) {
			pstmt.setString(1, DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
			pstmt.setString(2, noteId);
			pstmt.setString(3, folderId);
			pstmt.executeUpdate();
			connection.commit();

			updateModifiedDateFolder(folderId);
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error removeNote(): " + e.getMessage(), e);
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				logger.log(Level.SEVERE, "Error rolling back transaction: " + rollbackEx.getMessage(), rollbackEx);
			}
		}
	}

	protected void addSubFolder(String parentId, String subFolderId) {
		if (parentId == null || parentId.isEmpty() || subFolderId == null || subFolderId.isEmpty()
				|| parentId.equals(subFolderId)) {
			throw new IllegalArgumentException(
					"Parent folder ID and subfolder ID must not be null or empty and can't be the same");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(UPDATE_FOLDER_ADD_SUBFOLDER_SQL)) {
			pstmt.setString(1, parentId);
			pstmt.setString(2, DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
			pstmt.setString(3, subFolderId);
			pstmt.executeUpdate();
			connection.commit();

			updateModifiedDateFolder(parentId);
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error addSubFolder(): " + e.getMessage(), e);
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				logger.log(Level.SEVERE, "Error rolling back transaction: " + rollbackEx.getMessage(), rollbackEx);
			}
		}
	}

	protected void removeSubFolder(String parentId, String subFolderId) {
		if (parentId == null || parentId.isEmpty() || subFolderId == null || subFolderId.isEmpty()
				|| parentId.equals(subFolderId)) {
			throw new IllegalArgumentException(
					"Parent folder ID and subfolder ID must not be null or empty and can't be the same");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(UPDATE_FOLDER_REMOVE_SUBFOLDER_SQL)) {
			pstmt.setString(1, DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
			pstmt.setString(2, subFolderId);
			pstmt.setString(3, parentId);
			pstmt.executeUpdate();
			connection.commit();

			updateModifiedDateFolder(parentId);
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "removeSubFolder(): " + e.getMessage(), e);
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				logger.log(Level.SEVERE, "Error rolling back transaction: " + rollbackEx.getMessage(), rollbackEx);
			}
		}
	}

	private void loadSubFoldersHelper(Folder folder, int currentDepth, int maxDepth) {
		if (currentDepth > maxDepth)
			return;

		String query = (folder.getId() != null && !folder.getId().isEmpty()) ? SELECT_SUBFOLDERS_SQL
				: SELECT_SUBFOLDERS_ROOT_SQL;

		try (PreparedStatement pstmt = connection.prepareStatement(query)) {
			if (folder.getId() != null && !folder.getId().isEmpty()) {
				pstmt.setString(1, folder.getId());
			}

			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					Folder subFolder = mapResultSetToFolder(rs);
					folder.add(subFolder);
					subFolder.setParent(folder);
					loadSubFoldersHelper(subFolder, currentDepth + 1, maxDepth);
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error loadSubFoldersHelper(): " + e.getMessage(), e);
			throw new DataAccessException("Failed to retrieve subfolders", e);
		}
	}

	private void loadParentFoldersHelper(Folder folder, int currentDepth, int maxDepth) {
		if (currentDepth > maxDepth)
			return;

		try (PreparedStatement pstmt = connection.prepareStatement(SELECT_PARENT_FOLDER_SQL)) {
			pstmt.setString(1, folder.getId());
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					String parentId = rs.getString("parent_id");
					if (parentId != null) {
						Folder parentFolder = getFolderById(parentId);
						folder.setParent(parentFolder);
						loadParentFoldersHelper(parentFolder, currentDepth + 1, maxDepth);
					}
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error loadParentFoldersHelper: " + e.getMessage(), e);
		}
	}

	protected Folder mapResultSetToFolder(ResultSet rs) throws SQLException {
		Folder folder = null;

		if (rs != null) {
			String folderId = rs.getString("folder_id");
			String title = rs.getString("title");
			String createdDate = rs.getString("created_date");
			String modifiedDate = rs.getString("modified_date");

			folder = new Folder(folderId, title, createdDate, modifiedDate);
		}

		return folder;
	}

	private void updateModifiedDateFolder(String idFolder) {
		if (idFolder == null || idFolder.isEmpty()) {
			throw new InvalidParameterException("Invalid folder ID");
		}

		try (PreparedStatement pstmt = connection.prepareStatement(UPDATE_FOLDER_MODIFIED_DATE_SQL)) {
			pstmt.setString(1, DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
			pstmt.setString(2, idFolder);
			pstmt.executeUpdate();
			connection.commit();

		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error updateModifiedDateFolder(): " + e.getMessage(), e);
		}
	}

	@Override
	public Folder fetchTrashFolders() {
		// Create a virtual root for the trash
		Folder trashRoot = new Folder(".trash", "Trash", null);

		// Map for folders: ID -> Folder
		Map<String, Folder> folderMap = new HashMap<>();
		// Map for hierarchy: FolderID -> ParentID
		Map<String, String> parentMap = new HashMap<>();

		try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(SELECT_TRASH_FOLDERS_SQL)) {
			while (rs.next()) {
				Folder f = mapResultSetToFolder(rs);
				folderMap.put(f.getId(), f);

				// Re-extract parent_id for the map
				String pid = rs.getString("parent_id");
				if (pid != null) {
					parentMap.put(f.getId(), pid);
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error fetching trash folders: " + e.getMessage(), e);
		}

		// Reconstruct Hierarchy
		for (Folder f : folderMap.values()) {
			String pid = parentMap.get(f.getId());
			// Check if parent is also deleted (exists in map)
			if (pid != null && folderMap.containsKey(pid)) {
				Folder parent = folderMap.get(pid);
				parent.add(f);
				f.setParent(parent);
			} else {
				// Orphan in trash context (parent is alive or null)
				trashRoot.add(f);
				f.setParent(trashRoot);
			}
		}

		return trashRoot;
	}

	@Override
	public void restoreFolder(String id) {
		if (id == null || id.isEmpty()) {
			return;
		}

		NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);
		try {
			restoreFolderInTransaction(id, noteDAO);
			connection.commit();
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error restoreFolder: " + e.getMessage(), e);
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				logger.log(Level.SEVERE, "Error rolling back transaction: " + rollbackEx.getMessage(), rollbackEx);
			}
		}
	}

	private void restoreFolderInTransaction(String id, NoteDAOSQLite noteDAO) throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement(RESTORE_FOLDER_SQL)) {
			pstmt.setString(1, id);
			pstmt.executeUpdate();
		}
		for (Folder sub : fetchDeletedSubFolders(id)) {
			restoreFolderInTransaction(sub.getId(), noteDAO);
		}
		String queryDeletedNotes = "SELECT note_id FROM notes WHERE parent_id = ? AND is_deleted = 1";
		try (PreparedStatement p = connection.prepareStatement(queryDeletedNotes)) {
			p.setString(1, id);
			try (ResultSet rs = p.executeQuery()) {
				while (rs.next()) {
					noteDAO.restoreNoteWithoutCommit(rs.getString("note_id"));
				}
			}
		}
	}

	@Override
	public void permanentlyDeleteFolder(String id) {
		if (id == null || id.isEmpty()) {
			return;
		}

		NoteDAO noteDAO = new NoteDAOSQLite(connection);

		// 1. Recursively hard delete subfolders
		List<Folder> deletedSubs = fetchDeletedSubFolders(id);
		for (Folder sub : deletedSubs) {
			permanentlyDeleteFolder(sub.getId());
		}

		// 2. Permanently delete notes in this folder
		String queryNotes = "SELECT note_id FROM notes WHERE parent_id = ?";
		try (PreparedStatement p = connection.prepareStatement(queryNotes)) {
			p.setString(1, id);
			try (ResultSet rs = p.executeQuery()) {
				while (rs.next()) {
					noteDAO.permanentlyDeleteNote(rs.getString("note_id"));
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error permanently deleting notes: " + e.getMessage(), e);
		}

		// 3. Hard delete this folder
		try (PreparedStatement pstmt = connection.prepareStatement(DELETE_FOLDER_SQL)) {
			pstmt.setString(1, id);
			pstmt.executeUpdate();
			connection.commit();
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error permanentlyDeleteFolder(): " + e.getMessage(), e);
		}
	}

	private List<Folder> fetchSubFoldersImplementation(String parentId) {
		List<Folder> list = new ArrayList<>();
		try (PreparedStatement p = connection.prepareStatement(SELECT_SUBFOLDERS_SQL)) {
			p.setString(1, parentId);
			try (ResultSet rs = p.executeQuery()) {
				while (rs.next())
					list.add(mapResultSetToFolder(rs));
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error fetchSubFoldersImplementation: " + e.getMessage(), e);
		}
		return list;
	}

	private List<Folder> fetchDeletedSubFolders(String parentId) {
		String sql = "SELECT * FROM folders WHERE parent_id = ? AND is_deleted = 1";
		List<Folder> list = new ArrayList<>();
		try (PreparedStatement p = connection.prepareStatement(sql)) {
			p.setString(1, parentId);
			try (ResultSet rs = p.executeQuery()) {
				while (rs.next())
					list.add(mapResultSetToFolder(rs));
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error fetchDeletedSubFolders: " + e.getMessage(), e);
		}
		return list;
	}
}

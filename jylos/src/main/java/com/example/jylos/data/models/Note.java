package com.example.jylos.data.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.jylos.data.models.abstractLayers.LeafModel;

/**
 * Represents a note in the application.
 * A note has a title, content, optional metadata such as location, author, and
 * source information,
 * and can be associated with multiple tags.
 */
public class Note extends LeafModel implements Serializable {
	private static final long serialVersionUID = 1L;
	private String content;
	// private List<Tag> tags = new ArrayList<>();

	private Set<Tag> tags = new HashSet<>();

	private Double latitude = 0.0;
	private Double longitude = 0.0;
	private String author = null;
	private String sourceUrl = null;
	private String source = null;
	private String sourceApplication = null;
	private boolean isFavorite = false;
	private boolean isPinned = false;
	private boolean isDeleted = false;
	private String deletedDate = null;

	/**
	 * Optional workflow status (e.g. {@code todo}/{@code doing}/{@code done}) used by
	 * the Kanban board. A first-class field so it persists in both SQLite (dedicated
	 * column) and the filesystem vault ({@code status:} frontmatter). {@code null} or
	 * blank means "no status".
	 */
	private String status = null;

	/**
	 * When true, the note body is stored encrypted at rest (AES-256 via
	 * {@code EncryptionService}). Persisted in both SQLite (column) and the vault
	 * ({@code private:} frontmatter); only the body is encrypted, not the metadata.
	 */
	private boolean isPrivate = false;

	/**
	 * Arbitrary YAML frontmatter properties not covered by the fixed Note schema.
	 * Preserved in insertion order (LinkedHashMap) to produce stable YAML output.
	 * Examples: {@code aliases}, {@code date}, {@code priority}, user-defined fields.
	 */
	private Map<String, String> customProperties = new LinkedHashMap<>();

	/**
	 * Outgoing internal-link targets ({@code [[wiki]]} / {@code [label](note)}),
	 * extracted once when the note is read and cached here so the backlink index
	 * never needs a second full-file read per note (a costly operation on large or
	 * cloud-backed vaults, e.g. iCloud-offloaded files).
	 *
	 * <p>Transient: derived from {@link #content}, not part of the persisted model.
	 * For lightweight (list) reads it reflects the indexed content head; for a full
	 * read it covers the whole body. {@code null} means "not indexed yet".</p>
	 */
	private transient List<String> linkTargets = null;

	public Note(String title, String content) {
		super(title, null, null);
		this.content = content;
	}

	public Note(String id, String title, String content) {
		super(id, title, null, null);
		this.content = content;
	}

	public Note(String title, String content, String createdDate, String modifiedDate) {
		super(title, createdDate, modifiedDate);
		this.content = content;
	}

	public Note(String id, String title, String content, String createdDate, String modifiedDate) {
		super(id, title, createdDate, modifiedDate);
		this.content = content;
	}

	public Note(String title, String content, String createdDate, String modifiedDate, Double latitude,
			Double longitude,
			String author, String source_url, String source, String source_application) {
		super(title, createdDate, modifiedDate);

		this.content = content;
		this.latitude = latitude;
		this.longitude = longitude;
		this.author = author;
		this.sourceUrl = source_url;
		this.source = source;
		this.sourceApplication = source_application;
	}

	public Note(String id, String title, String content, String createdDate, String modifiedDate, Double latitude,
			Double longitude,
			String author, String source_url, String source, String source_application) {
		this(title, content, createdDate, modifiedDate, latitude, longitude, author, source_url, source,
				source_application);
		setId(id);
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
		// Any cached link index is derived from the content; mutating the content
		// invalidates it so consumers re-derive (see linkTargets / getLinkTargets()).
		this.linkTargets = null;
	}

	/*
	 * public List<Tag> getTags() {
	 * return tags;
	 * }
	 * 
	 * public void setTags(List<Tag> tags) {
	 * this.tags = tags;
	 * }
	 */

	public void addTag(Tag tag) {
		if (tag != null) {
			tags.add(tag);
		}
	}

	public void addAllTags(List<Tag> tags) {
		if (tags != null && !tags.isEmpty()) {
			this.tags.addAll(tags);
		}
	}

	public void setTags(List<Tag> tags) {
		if (tags != null) {
			this.tags = new HashSet<>(tags);
		}
	}

	public void removeTag(Tag tag) {
		if (tag != null) {
			tags.remove(tag);
		}
	}

	public List<Tag> getTags() {
		return new ArrayList<>(tags);
	}

	public Double getLatitude() {
		return latitude;
	}

	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}

	public Double getLongitude() {
		return longitude;
	}

	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getSourceApplication() {
		return sourceApplication;
	}

	public void setSourceApplication(String sourceApplication) {
		this.sourceApplication = sourceApplication;
	}

	public boolean isFavorite() {
		return isFavorite;
	}

	public void setFavorite(boolean isFavorite) {
		this.isFavorite = isFavorite;
	}

	public boolean isPinned() {
		return isPinned;
	}

	public void setPinned(boolean isPinned) {
		this.isPinned = isPinned;
	}

	public boolean isDeleted() {
		return isDeleted;
	}

	public void setDeleted(boolean isDeleted) {
		this.isDeleted = isDeleted;
	}

	public String getDeletedDate() {
		return deletedDate;
	}

	public void setDeletedDate(String deletedDate) {
		this.deletedDate = deletedDate;
	}

	/** Workflow status for the Kanban board ({@code null}/blank = no status). */
	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	/** Whether this note's body is encrypted at rest. */
	public boolean isPrivate() {
		return isPrivate;
	}

	public void setPrivate(boolean isPrivate) {
		this.isPrivate = isPrivate;
	}

	/** Returns a live, mutable view of the custom YAML properties. */
	public Map<String, String> getCustomProperties() {
		return customProperties;
	}

	/** Replaces all custom YAML properties; preserves insertion order. */
	public void setCustomProperties(Map<String, String> customProperties) {
		this.customProperties = customProperties != null
				? new LinkedHashMap<>(customProperties)
				: new LinkedHashMap<>();
	}

	/**
	 * Cached outgoing internal-link targets for this note, or {@code null} if the
	 * note has not been indexed yet. See {@link #linkTargets}.
	 */
	public List<String> getLinkTargets() {
		return linkTargets;
	}

	/** Sets the cached outgoing internal-link targets (see {@link #linkTargets}). */
	public void setLinkTargets(List<String> linkTargets) {
		this.linkTargets = linkTargets;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Note note = (Note) o;
		if (getId() != null && note.getId() != null) {
			return getId().equals(note.getId());
		}
		return getTitle().equals(note.getTitle());
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
		return "Note{"
				+ "id='" + getId() + '\'' +
				"title='" + getTitle() + '\'' +
				"content='" + content + '\'' +
				'}';
	}
}

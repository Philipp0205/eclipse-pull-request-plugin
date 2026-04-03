package org.eclipse.egit.pullrequest.internal.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Model class for a pull request comment from Bitbucket Data Center
 */
public class PullRequestComment {

	private long id;

	private int version;

	private String text;

	private String authorName;

	private String authorDisplayName;

	private String authorEmail;

	private String authorAvatarUrl;

	private Date createdDate;

	private Date updatedDate;

	private String state;

	private String severity;

	// Comment anchor information (null for general PR comments)
	private String path;

	private String srcPath;

	private Integer line; // null for file-level comments

	private String lineType; // ADDED, REMOVED, CONTEXT

	private String fileType; // FROM, TO

	// Thread structure
	private List<PullRequestComment> replies = new ArrayList<>();

	/**
	 * ID of the parent comment this comment replies to. Used by GitHub for
	 * thread reconstruction. Value is -1 for root comments (not a reply).
	 */
	private long inReplyToId = -1;

	/**
	 * Whether this is a review comment (inline/code comment) or an issue
	 * comment (general PR comment). Used by GitHub to determine which API
	 * endpoint to use for editing/deleting.
	 */
	private boolean reviewComment;

	/**
	 * GitHub GraphQL thread ID (node_id). Required for resolving/unresolving
	 * review threads via GraphQL API. This is the global node ID that
	 * identifies the review thread in GitHub's GraphQL schema.
	 */
	private String threadId;

	/**
	 * @return the comment ID
	 */
	public long getId() {
		return id;
	}

	/**
	 * @param id
	 *            the comment ID
	 */
	public void setId(long id) {
		this.id = id;
	}

	/**
	 * @return the comment version
	 */
	public int getVersion() {
		return version;
	}

	/**
	 * @param version
	 *            the comment version
	 */
	public void setVersion(int version) {
		this.version = version;
	}

	/**
	 * @return the comment text
	 */
	public String getText() {
		return text;
	}

	/**
	 * @param text
	 *            the comment text
	 */
	public void setText(String text) {
		this.text = text;
	}

	/**
	 * @return the author username
	 */
	public String getAuthorName() {
		return authorName;
	}

	/**
	 * @param authorName
	 *            the author username
	 */
	public void setAuthorName(String authorName) {
		this.authorName = authorName;
	}

	/**
	 * @return the author display name
	 */
	public String getAuthorDisplayName() {
		return authorDisplayName;
	}

	/**
	 * @param authorDisplayName
	 *            the author display name
	 */
	public void setAuthorDisplayName(String authorDisplayName) {
		this.authorDisplayName = authorDisplayName;
	}

	/**
	 * @return the author email
	 */
	public String getAuthorEmail() {
		return authorEmail;
	}

	/**
	 * @param authorEmail
	 *            the author email
	 */
	public void setAuthorEmail(String authorEmail) {
		this.authorEmail = authorEmail;
	}

	/**
	 * @return the author avatar URL
	 */
	public String getAuthorAvatarUrl() {
		return authorAvatarUrl;
	}

	/**
	 * @param authorAvatarUrl
	 *            the author avatar URL
	 */
	public void setAuthorAvatarUrl(String authorAvatarUrl) {
		this.authorAvatarUrl = authorAvatarUrl;
	}

	/**
	 * @return the creation date
	 */
	public Date getCreatedDate() {
		return createdDate;
	}

	/**
	 * @param createdDate
	 *            the creation date
	 */
	public void setCreatedDate(Date createdDate) {
		this.createdDate = createdDate;
	}

	/**
	 * @return the last update date
	 */
	public Date getUpdatedDate() {
		return updatedDate;
	}

	/**
	 * @param updatedDate
	 *            the last update date
	 */
	public void setUpdatedDate(Date updatedDate) {
		this.updatedDate = updatedDate;
	}

	/**
	 * @return the comment state (OPEN, RESOLVED)
	 */
	public String getState() {
		return state;
	}

	/**
	 * @param state
	 *            the comment state
	 */
	public void setState(String state) {
		this.state = state;
	}

	/**
	 * @return the comment severity (NORMAL, BLOCKER)
	 */
	public String getSeverity() {
		return severity;
	}

	/**
	 * @param severity
	 *            the comment severity
	 */
	public void setSeverity(String severity) {
		this.severity = severity;
	}

	/**
	 * @return the file path (null for general PR comments)
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @param path
	 *            the file path
	 */
	public void setPath(String path) {
		this.path = path;
	}

	/**
	 * @return the source path for renamed/moved files
	 */
	public String getSrcPath() {
		return srcPath;
	}

	/**
	 * @param srcPath
	 *            the source path
	 */
	public void setSrcPath(String srcPath) {
		this.srcPath = srcPath;
	}

	/**
	 * @return the line number (null for file-level or general comments)
	 */
	public Integer getLine() {
		return line;
	}

	/**
	 * @param line
	 *            the line number
	 */
	public void setLine(Integer line) {
		this.line = line;
	}

	/**
	 * @return the line type (ADDED, REMOVED, CONTEXT)
	 */
	public String getLineType() {
		return lineType;
	}

	/**
	 * @param lineType
	 *            the line type
	 */
	public void setLineType(String lineType) {
		this.lineType = lineType;
	}

	/**
	 * @return the file type/side (FROM for left/old, TO for right/new)
	 */
	public String getFileType() {
		return fileType;
	}

	/**
	 * @param fileType
	 *            the file type/side
	 */
	public void setFileType(String fileType) {
		this.fileType = fileType;
	}

	/**
	 * @return the list of replies to this comment
	 */
	public List<PullRequestComment> getReplies() {
		return replies;
	}

	/**
	 * @param replies
	 *            the list of replies
	 */
	public void setReplies(List<PullRequestComment> replies) {
		this.replies = replies;
	}

	/**
	 * @return the ID of the parent comment this replies to, or -1 if this is a
	 *         root comment
	 */
	public long getInReplyToId() {
		return inReplyToId;
	}

	/**
	 * @param inReplyToId
	 *            the ID of the parent comment
	 */
	public void setInReplyToId(long inReplyToId) {
		this.inReplyToId = inReplyToId;
	}

	/**
	 * @return true if this is a general PR comment (no file/line anchor)
	 */
	public boolean isGeneralComment() {
		return path == null;
	}

	/**
	 * @return true if this is a file-level comment (has path but no line)
	 */
	public boolean isFileLevelComment() {
		return path != null && line == null;
	}

	/**
	 * @return true if this is an inline comment (has path and line)
	 */
	public boolean isInlineComment() {
		return path != null && line != null;
	}

	/**
	 * @return true if this is a review comment (inline/code comment), false if
	 *         it's an issue comment (general PR comment). Used by GitHub to
	 *         determine API endpoint.
	 */
	public boolean isReviewComment() {
		return reviewComment;
	}

	/**
	 * @param reviewComment
	 *            true if this is a review comment, false if it's an issue
	 *            comment
	 */
	public void setReviewComment(boolean reviewComment) {
		this.reviewComment = reviewComment;
	}

	/**
	 * @return the GitHub GraphQL thread ID (node_id), or null if not available
	 */
	public String getThreadId() {
		return threadId;
	}

	/**
	 * @param threadId
	 *            the GitHub GraphQL thread ID (node_id)
	 */
	public void setThreadId(String threadId) {
		this.threadId = threadId;
	}
}


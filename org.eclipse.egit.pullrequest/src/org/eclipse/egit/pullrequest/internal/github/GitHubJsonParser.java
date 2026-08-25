package org.eclipse.egit.pullrequest.internal.github;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.model.ChangedFile;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.egit.pullrequest.internal.model.PullRequestCommit;

/**
 * Parser for GitHub API JSON responses
 */
class GitHubJsonParser {

	private static final String ISO8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'"; //$NON-NLS-1$

	/**
	 * Parses a list of pull requests from GitHub API JSON
	 *
	 * @param json
	 *            the JSON response
	 * @return list of pull requests
	 */
	static List<PullRequest> parsePullRequests(String json) {
		List<PullRequest> result = new ArrayList<>();
		if (json == null || json.trim().isEmpty() || json.trim().equals("[]")) { //$NON-NLS-1$
			return result;
		}

		// Parse array of pull requests
		json = json.trim();
		if (!json.startsWith("[")) { //$NON-NLS-1$
			return result;
		}

		// Parse array with string boundary tracking
		int depth = 0;
		int start = 1; // Skip opening [
		boolean inString = false;
		boolean escaped = false;

		for (int i = 1; i < json.length(); i++) {
			char c = json.charAt(i);
			
			// Handle escape sequences
			if (escaped) {
				escaped = false;
				continue;
			}
			if (c == '\\') {
				escaped = true;
				continue;
			}
			
			// Track string boundaries
			if (c == '"') {
				inString = !inString;
				continue;
			}
			
			// Only process structural characters when NOT inside a string
			if (!inString) {
				if (c == '{') {
					depth++;
				} else if (c == '}') {
					depth--;
					if (depth == 0) {
						// End of an object
						String prJson = json.substring(start, i + 1);
						PullRequest pr = parseSinglePullRequestObject(prJson);
						if (pr != null) {
							result.add(pr);
						}
						// Skip comma and whitespace
						while (i + 1 < json.length()
								&& (json.charAt(i + 1) == ',' || Character
										.isWhitespace(json.charAt(i + 1)))) {
							i++;
						}
						start = i + 1;
					}
				}
			}
		}

		return result;
	}

	/**
	 * Parses a single pull request from GitHub API JSON
	 *
	 * @param json
	 *            the JSON response
	 * @return the pull request
	 */
	static PullRequest parseSinglePullRequest(String json) {
		if (json == null || json.trim().isEmpty()) {
			return null;
		}
		return parseSinglePullRequestObject(json.trim());
	}

	/**
	 * Parses a single pull request object from JSON
	 *
	 * @param json
	 *            the JSON string to parse
	 * @return the parsed pull request
	 */
	private static PullRequest parseSinglePullRequestObject(String json) {
		PullRequest pr = new PullRequest();

		// Extract fields
		pr.setId(extractLong(json, "number")); //$NON-NLS-1$
		pr.setTitle(extractString(json, "title")); //$NON-NLS-1$
		pr.setDescription(extractString(json, "body")); //$NON-NLS-1$

		// Map GitHub state to Bitbucket-style state
		String state = extractString(json, "state"); //$NON-NLS-1$
		boolean merged = extractBoolean(json, "merged"); //$NON-NLS-1$

		if ("closed".equals(state) && merged) { //$NON-NLS-1$
			pr.setState("MERGED"); //$NON-NLS-1$
			pr.setOpen(false);
			pr.setClosed(true);
		} else if ("closed".equals(state)) { //$NON-NLS-1$
			pr.setState("DECLINED"); //$NON-NLS-1$
			pr.setOpen(false);
			pr.setClosed(true);
		} else {
			pr.setState("OPEN"); //$NON-NLS-1$
			pr.setOpen(true);
			pr.setClosed(false);
		}

		pr.setCreatedDate(extractDate(json, "created_at")); //$NON-NLS-1$
		pr.setUpdatedDate(extractDate(json, "updated_at")); //$NON-NLS-1$

		// Parse branch references
		String headJson = extractObject(json, "head"); //$NON-NLS-1$
		if (headJson != null) {
			pr.setFromRef(parseRef(headJson));
		}

		String baseJson = extractObject(json, "base"); //$NON-NLS-1$
		if (baseJson != null) {
			pr.setToRef(parseRef(baseJson));
		}

		// Parse author
		String userJson = extractObject(json, "user"); //$NON-NLS-1$
		if (userJson != null) {
			PullRequest.PullRequestParticipant author = new PullRequest.PullRequestParticipant();
			PullRequest.User user = new PullRequest.User();
			user.setName(extractString(userJson, "login")); //$NON-NLS-1$
			user.setDisplayName(extractString(userJson, "name", //$NON-NLS-1$
					extractString(userJson, "login"))); //$NON-NLS-1$
			user.setAvatarUrl(extractString(userJson, "avatar_url")); //$NON-NLS-1$
			author.setUser(user);
			author.setRole("AUTHOR"); //$NON-NLS-1$
			pr.setAuthor(author);
		}

		// Parse comment count (sum of issue comments + review comments)
		int issueComments = extractInt(json, "comments"); //$NON-NLS-1$
		int reviewComments = extractInt(json, "review_comments"); //$NON-NLS-1$
		pr.setCommentCount(issueComments + reviewComments);

		// Parse individual and team reviewers
		List<PullRequest.PullRequestParticipant> reviewers = new ArrayList<>();
		String requestedReviewersJson = extractArray(json,
				"requested_reviewers"); //$NON-NLS-1$
		if (requestedReviewersJson != null) {
			reviewers.addAll(parseReviewers(requestedReviewersJson));
		}
		String requestedTeamsJson = extractArray(json,
				"requested_teams"); //$NON-NLS-1$
		if (requestedTeamsJson != null) {
			reviewers.addAll(parseReviewers(requestedTeamsJson));
		}
		if (!reviewers.isEmpty()) {
			pr.setReviewers(reviewers);
		}

		// Parse links
		PullRequest.PullRequestLinks links = new PullRequest.PullRequestLinks();
		PullRequest.Link[] self = new PullRequest.Link[1];
		self[0] = new PullRequest.Link();
		self[0].setHref(extractString(json, "html_url")); //$NON-NLS-1$
		links.setSelf(self);
		pr.setLinks(links);

		return pr;
	}

	/**
	 * Parses a branch reference from JSON
	 *
	 * @param json
	 *            the JSON string to parse
	 * @return the parsed pull request reference
	 */
	private static PullRequest.PullRequestRef parseRef(String json) {
		PullRequest.PullRequestRef ref = new PullRequest.PullRequestRef();
		ref.setId(extractString(json, "ref")); //$NON-NLS-1$
		ref.setDisplayId(extractString(json, "ref")); //$NON-NLS-1$
		ref.setLatestCommit(extractString(json, "sha")); //$NON-NLS-1$

		String repoJson = extractObject(json, "repo"); //$NON-NLS-1$
		if (repoJson != null) {
			PullRequest.Repository repo = new PullRequest.Repository();
			repo.setSlug(extractString(repoJson, "name")); //$NON-NLS-1$
			repo.setName(extractString(repoJson, "full_name")); //$NON-NLS-1$
			repo.setCloneUrl(extractString(repoJson, "clone_url")); //$NON-NLS-1$

			String ownerJson = extractObject(repoJson, "owner"); //$NON-NLS-1$
			if (ownerJson != null) {
				PullRequest.Project project = new PullRequest.Project();
				project.setKey(extractString(ownerJson, "login")); //$NON-NLS-1$
				project.setName(extractString(ownerJson, "login")); //$NON-NLS-1$
				repo.setProject(project);
			}

			ref.setRepository(repo);
		}

		return ref;
	}

	/**
	 * Parses changed files from GitHub API JSON
	 *
	 * @param json
	 *            the JSON response
	 * @return list of changed files
	 */
	static List<ChangedFile> parseChangedFiles(String json) {
		List<ChangedFile> result = new ArrayList<>();

		if (json == null || json.trim().isEmpty() || json.trim().equals("[]")) { //$NON-NLS-1$
			return result;
		}

		json = json.trim();
		if (!json.startsWith("[")) { //$NON-NLS-1$
			Activator.logInfo("GitHubJsonParser.parseChangedFiles: JSON does not start with '[', got: " + (json.length() > 50 ? json.substring(0, 50) : json)); //$NON-NLS-1$
			return result;
		}

		// Parse array
		int depth = 0;
		int start = 1;
		boolean inString = false;
		boolean escaped = false;

		for (int i = 1; i < json.length(); i++) {
			char c = json.charAt(i);
			
			// Handle escape sequences
			if (escaped) {
				escaped = false;
				continue;
			}
			if (c == '\\') {
				escaped = true;
				continue;
			}
			
			// Track string boundaries
			if (c == '"') {
				inString = !inString;
				continue;
			}
			
			// Only process structural characters when NOT inside a string
			if (!inString) {
				if (c == '{') {
					depth++;
				} else if (c == '}') {
					depth--;
					if (depth == 0) {
						String fileJson = json.substring(start, i + 1);
						ChangedFile file = parseChangedFile(fileJson);
						if (file != null) {
							result.add(file);
						}
						while (i + 1 < json.length()
								&& (json.charAt(i + 1) == ',' || Character
										.isWhitespace(json.charAt(i + 1)))) {
							i++;
						}
						start = i + 1;
					}
				}
			}
		}

		return result;
	}

	/**
	 * Parses a single changed file from JSON
	 *
	 * @param json
	 *            the JSON string to parse
	 * @return the parsed changed file
	 */
	private static ChangedFile parseChangedFile(String json) {
		ChangedFile file = new ChangedFile();

		String filename = extractString(json, "filename"); //$NON-NLS-1$
		String status = extractString(json, "status"); //$NON-NLS-1$
		String previousFilename = extractString(json, "previous_filename"); //$NON-NLS-1$

		// Handle missing filename
		if (filename == null || filename.isEmpty()) {
			filename = "unknown"; //$NON-NLS-1$
		}

		// Create path object
		ChangedFile.Path path = new ChangedFile.Path();
		path.setToString(filename);
		path.setName(filename.contains("/") //$NON-NLS-1$
				? filename.substring(filename.lastIndexOf('/') + 1)
				: filename);
		if (filename.contains(".")) { //$NON-NLS-1$
			path.setExtension(
					filename.substring(filename.lastIndexOf('.') + 1));
		}
		file.setPath(path);

		// Map GitHub status to Bitbucket type
		if ("added".equals(status)) { //$NON-NLS-1$
			file.setType("ADD"); //$NON-NLS-1$
		} else if ("removed".equals(status)) { //$NON-NLS-1$
			file.setType("DELETE"); //$NON-NLS-1$
		} else if ("modified".equals(status)) { //$NON-NLS-1$
			file.setType("MODIFY"); //$NON-NLS-1$
		} else if ("renamed".equals(status)) { //$NON-NLS-1$
			file.setType("MOVE"); //$NON-NLS-1$
			if (previousFilename != null && !previousFilename.isEmpty()) {
				ChangedFile.Path srcPath = new ChangedFile.Path();
				srcPath.setToString(previousFilename);
				srcPath.setName(previousFilename.contains("/") //$NON-NLS-1$
						? previousFilename
								.substring(previousFilename.lastIndexOf('/') + 1)
						: previousFilename);
				file.setSrcPath(srcPath);
		}
		}

		// Extract patch field if present
		String patch = extractString(json, "patch"); //$NON-NLS-1$
		if (patch != null) {
			file.setPatch(patch);
		}

		return file;
	}

	/**
	 * Parses comments from GitHub API JSON (both review and issue comments)
	 *
	 * @param reviewCommentsJson
	 *            the review comments JSON
	 * @param issueCommentsJson
	 *            the issue comments JSON
	 * @return list of comments
	 */
	static List<PullRequestComment> parseComments(String reviewCommentsJson,
			String issueCommentsJson) {
		List<PullRequestComment> result = new ArrayList<>();

		// Parse review comments (inline code comments)
		if (reviewCommentsJson != null && !reviewCommentsJson.trim().isEmpty()
				&& !reviewCommentsJson.trim().equals("[]")) { //$NON-NLS-1$
			List<PullRequestComment> reviewComments = parseCommentArray(
					reviewCommentsJson, true);
			// Group review comments into threads based on in_reply_to_id
			// This reconstructs GitHub's flat threading model where all replies
			// point to the root comment
			reviewComments = groupIntoThreads(reviewComments);
			result.addAll(reviewComments);
		}

		// Parse issue comments (general PR comments)
		// Issue comments don't support threading, so add them as-is
		if (issueCommentsJson != null && !issueCommentsJson.trim().isEmpty()
				&& !issueCommentsJson.trim().equals("[]")) { //$NON-NLS-1$
			result.addAll(parseCommentArray(issueCommentsJson, false));
		}

		return result;
	}

	/**
	 * Counts the number of elements in a JSON array
	 *
	 * @param json
	 *            the JSON array string
	 * @return the count of array elements, or 0 if not a valid array
	 */
	static int countArrayElements(String json) {
		if (json == null || json.trim().isEmpty()
				|| json.trim().equals("[]")) { //$NON-NLS-1$
			return 0;
		}

		json = json.trim();
		if (!json.startsWith("[")) { //$NON-NLS-1$
			return 0;
		}

		int count = 0;
		int depth = 0;
		boolean inString = false;
		boolean escaped = false;

		for (int i = 1; i < json.length(); i++) {
			char c = json.charAt(i);

			// Handle escape sequences
			if (escaped) {
				escaped = false;
				continue;
			}
			if (c == '\\') {
				escaped = true;
				continue;
			}

			// Track string boundaries
			if (c == '"') {
				inString = !inString;
				continue;
			}

			// Only process structural characters when NOT inside a string
			if (!inString) {
				if (c == '{') {
					depth++;
				} else if (c == '}') {
					depth--;
					if (depth == 0) {
						count++;
					}
				}
			}
		}

		return count;
	}

	/**
	 * Parses review comments from GitHub API JSON
	 *
	 * @param json
	 *            the JSON response containing an array of review comments
	 * @return list of review comments
	 */
	static List<PullRequestComment> parseReviewComments(String json) {
		if (json == null || json.trim().isEmpty() || json.trim().equals("[]")) { //$NON-NLS-1$
			return new ArrayList<>();
		}
		return parseCommentArray(json, true);
	}

	/**
	 * Parses an array of comments
	 *
	 * @param json
	 *            the JSON string to parse
	 * @param isReviewComment
	 *            true if parsing review comments, false for issue comments
	 * @return the list of parsed comments
	 */
	private static List<PullRequestComment> parseCommentArray(String json,
			boolean isReviewComment) {
		List<PullRequestComment> result = new ArrayList<>();
		json = json.trim();
		if (!json.startsWith("[")) { //$NON-NLS-1$
			return result;
		}

		int depth = 0;
		int start = 1;
		boolean inString = false;
		boolean escaped = false;

		for (int i = 1; i < json.length(); i++) {
			char c = json.charAt(i);
			
			// Handle escape sequences
			if (escaped) {
				escaped = false;
				continue;
			}
			if (c == '\\') {
				escaped = true;
				continue;
			}
			
			// Track string boundaries
			if (c == '"') {
				inString = !inString;
				continue;
			}
			
			// Only process structural characters when NOT inside a string
			if (!inString) {
				if (c == '{') {
					depth++;
				} else if (c == '}') {
					depth--;
					if (depth == 0) {
						String commentJson = json.substring(start, i + 1);
						PullRequestComment comment = parseComment(commentJson,
								isReviewComment);
						if (comment != null) {
							result.add(comment);
						}
						while (i + 1 < json.length()
								&& (json.charAt(i + 1) == ',' || Character
										.isWhitespace(json.charAt(i + 1)))) {
							i++;
						}
						start = i + 1;
					}
				}
			}
		}

		return result;
	}

	/**
	 * Parses a single comment from JSON
	 *
	 * @param json
	 *            the JSON string to parse
	 * @return the parsed comment or null if JSON is empty
	 */
	static PullRequestComment parseSingleComment(String json) {
		if (json == null || json.trim().isEmpty()) {
			return null;
		}
		// Determine if it's a review comment by checking for "path" field
		boolean isReviewComment = json.contains("\"path\""); //$NON-NLS-1$
		return parseComment(json.trim(), isReviewComment);
	}

	/**
	 * Parses a comment object
	 *
	 * @param json
	 *            the JSON string to parse
	 * @param isReviewComment
	 *            true if parsing a review comment, false for issue comment
	 * @return the parsed comment
	 */
	private static PullRequestComment parseComment(String json,
			boolean isReviewComment) {
		PullRequestComment comment = new PullRequestComment();

		comment.setId(extractLong(json, "id")); //$NON-NLS-1$
		comment.setText(extractString(json, "body")); //$NON-NLS-1$
		comment.setCreatedDate(extractDate(json, "created_at")); //$NON-NLS-1$
		comment.setUpdatedDate(extractDate(json, "updated_at")); //$NON-NLS-1$

		// Parse author
		String userJson = extractObject(json, "user"); //$NON-NLS-1$
		if (userJson != null) {
			comment.setAuthorName(extractString(userJson, "login")); //$NON-NLS-1$
			comment.setAuthorDisplayName(extractString(userJson, "login")); //$NON-NLS-1$
			comment.setAuthorAvatarUrl(extractString(userJson, "avatar_url")); //$NON-NLS-1$
		}

		// GitHub doesn't have comment state or severity
		comment.setState("OPEN"); //$NON-NLS-1$
		comment.setSeverity("NORMAL"); //$NON-NLS-1$
		
		// Set the review comment flag so edit/delete operations use correct API endpoint
		comment.setReviewComment(isReviewComment);

		if (isReviewComment) {
			// Extract node_id for GraphQL thread resolution
			// GitHub requires the node_id to query for the review thread ID
			String nodeId = extractString(json, "node_id"); //$NON-NLS-1$
			Activator.logInfo("parseComment: extracted node_id=" + nodeId //$NON-NLS-1$
					+ " for comment id=" + comment.getId()); //$NON-NLS-1$
			if (nodeId != null && !nodeId.isEmpty()) {
				comment.setThreadId(nodeId);
			}
			
			// Review comments have file and line information
			comment.setPath(extractString(json, "path")); //$NON-NLS-1$
			Integer line = extractInteger(json, "line"); //$NON-NLS-1$
			
			// Fall back to original_line when line is null (outdated comments)
			if (line == null) {
				line = extractInteger(json, "original_line"); //$NON-NLS-1$
			}
			comment.setLine(line);

			// GitHub uses "side" field: LEFT (old) or RIGHT (new)
			String side = extractString(json, "side"); //$NON-NLS-1$
			if ("LEFT".equals(side)) { //$NON-NLS-1$
				comment.setFileType("FROM"); //$NON-NLS-1$
			} else {
				comment.setFileType("TO"); //$NON-NLS-1$
			}

			// Extract in_reply_to_id for thread reconstruction
			// GitHub review comments include this field when they are replies
			long inReplyToId = extractLong(json, "in_reply_to_id"); //$NON-NLS-1$
			if (inReplyToId > 0) {
				comment.setInReplyToId(inReplyToId);
			}
		}

		return comment;
	}

	/**
	 * Groups comments into threads based on in_reply_to_id. GitHub's threading
	 * model is flat: all replies in a thread point to the root comment via
	 * in_reply_to_id.
	 *
	 * @param comments
	 *            flat list of comments
	 * @return list of root comments with replies nested in getReplies()
	 */
	private static List<PullRequestComment> groupIntoThreads(
			List<PullRequestComment> comments) {
		if (comments == null || comments.isEmpty()) {
			return comments;
		}

		// Build map of all comments by ID for quick lookup
		Map<Long, PullRequestComment> commentMap = new HashMap<>();
		for (PullRequestComment comment : comments) {
			commentMap.put(comment.getId(), comment);
		}

		// Separate root comments from replies
		List<PullRequestComment> roots = new ArrayList<>();
		for (PullRequestComment comment : comments) {
			long replyToId = comment.getInReplyToId();
			if (replyToId <= 0) {
				// Root comment (not a reply)
				roots.add(comment);
			} else {
				// Reply comment - add to parent's replies list
				PullRequestComment parent = commentMap.get(replyToId);
				if (parent != null) {
					parent.getReplies().add(comment);
				} else {
					// Orphaned reply (parent not in this result set)
					// Treat as root to avoid losing the comment
					roots.add(comment);
				}
			}
		}

		// Sort replies chronologically within each thread
		for (PullRequestComment root : roots) {
			List<PullRequestComment> replies = root.getReplies();
			if (!replies.isEmpty()) {
				replies.sort((r1, r2) -> {
					if (r1.getCreatedDate() == null
							|| r2.getCreatedDate() == null) {
						return 0;
					}
					return r1.getCreatedDate().compareTo(r2.getCreatedDate());
				});
			}
		}

		return roots;
	}

	/**
	 * Parses file content from GitHub API JSON response
	 *
	 * @param json
	 *            the JSON response
	 * @return the decoded file content
	 */
	static byte[] parseFileContent(String json) {
		// GitHub returns file content base64-encoded in the "content" field
		String content = extractString(json, "content"); //$NON-NLS-1$
		if (content == null || content.isEmpty()) {
			return new byte[0];
		}

		// Remove whitespace/newlines that GitHub adds
		content = content.replaceAll("\\s+", ""); //$NON-NLS-1$ //$NON-NLS-2$

		try {
			return Base64.getDecoder().decode(content);
		} catch (IllegalArgumentException e) {
			return new byte[0];
		}
	}

	/**
	 * Parses the current user from GitHub API JSON
	 *
	 * @param json
	 *            the JSON response
	 * @return the username
	 */
	static String parseCurrentUser(String json) {
		return extractString(json, "login"); //$NON-NLS-1$
	}

	// Helper methods for JSON parsing

	static String extractString(String json, String key) {
		return extractString(json, key, null);
	}

	static String extractString(String json, String key,
			String defaultValue) {
		String pattern = "\"" + key + "\":"; //$NON-NLS-1$ //$NON-NLS-2$
		int colonIndex = json.indexOf(pattern);
		if (colonIndex == -1) {
			return defaultValue;
		}

		// Skip whitespace after colon
		int startIndex = colonIndex + pattern.length();
		while (startIndex < json.length()
				&& Character.isWhitespace(json.charAt(startIndex))) {
			startIndex++;
		}

		if (startIndex >= json.length()) {
			return defaultValue;
		}

		// Check for null value
		if (json.startsWith("null", startIndex)) { //$NON-NLS-1$
			return defaultValue;
		}

		// Must be a string value
		if (json.charAt(startIndex) != '"') {
			return defaultValue;
		}

		// Find closing quote (handling escaped quotes)
		int endIndex = startIndex + 1;
		while (endIndex < json.length()) {
			char c = json.charAt(endIndex);
			if (c == '"' && json.charAt(endIndex - 1) != '\\') {
				break;
			}
			endIndex++;
		}

		if (endIndex >= json.length()) {
			return defaultValue;
		}

		String value = json.substring(startIndex + 1, endIndex);
		// Unescape JSON string
		return value.replace("\\\"", "\"") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\\\\", "\\") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\\n", "\n") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\\r", "\r") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\\t", "\t"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	static long extractLong(String json, String key) {
		String value = extractNumberString(json, key);
		if (value == null) {
			return 0;
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static int extractInt(String json, String key) {
		String value = extractNumberString(json, key);
		if (value == null) {
			return 0;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static Integer extractInteger(String json, String key) {
		String value = extractNumberString(json, key);
		if (value == null) {
			return null;
		}
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static boolean extractBoolean(String json, String key) {
		String pattern = "\"" + key + "\":"; //$NON-NLS-1$ //$NON-NLS-2$
		int colonIndex = json.indexOf(pattern);
		if (colonIndex == -1) {
			return false;
		}

		int startIndex = colonIndex + pattern.length();
		while (startIndex < json.length()
				&& Character.isWhitespace(json.charAt(startIndex))) {
			startIndex++;
		}

		return json.startsWith("true", startIndex); //$NON-NLS-1$
	}

	private static String extractNumberString(String json, String key) {
		String pattern = "\"" + key + "\":"; //$NON-NLS-1$ //$NON-NLS-2$
		int colonIndex = json.indexOf(pattern);
		if (colonIndex == -1) {
			return null;
		}

		int startIndex = colonIndex + pattern.length();
		while (startIndex < json.length()
				&& Character.isWhitespace(json.charAt(startIndex))) {
			startIndex++;
		}

		if (startIndex >= json.length()) {
			return null;
		}

		// Check for null
		if (json.startsWith("null", startIndex)) { //$NON-NLS-1$
			return null;
		}

		// Extract number
		int endIndex = startIndex;
		while (endIndex < json.length()) {
			char c = json.charAt(endIndex);
			if (!Character.isDigit(c) && c != '.' && c != '-' && c != 'e'
					&& c != 'E' && c != '+') {
				break;
			}
			endIndex++;
		}

		if (endIndex == startIndex) {
			return null;
		}

		return json.substring(startIndex, endIndex);
	}

	private static Date extractDate(String json, String key) {
		String dateStr = extractString(json, key);
		if (dateStr == null) {
			return null;
		}

		try {
			SimpleDateFormat sdf = new SimpleDateFormat(ISO8601_FORMAT);
			sdf.setTimeZone(TimeZone.getTimeZone("UTC")); //$NON-NLS-1$
			return sdf.parse(dateStr);
		} catch (ParseException e) {
			return null;
		}
	}

	static String extractObject(String json, String key) {
		String pattern = "\"" + key + "\":"; //$NON-NLS-1$ //$NON-NLS-2$
		int colonIndex = json.indexOf(pattern);
		if (colonIndex == -1) {
			return null;
		}

		int startIndex = colonIndex + pattern.length();
		while (startIndex < json.length()
				&& Character.isWhitespace(json.charAt(startIndex))) {
			startIndex++;
		}

		if (startIndex >= json.length()) {
			return null;
		}

		// Check for null
		if (json.startsWith("null", startIndex)) { //$NON-NLS-1$
			return null;
		}

		// Must start with {
		if (json.charAt(startIndex) != '{') {
			return null;
		}

		// Find matching closing brace
		int depth = 0;
		int endIndex = startIndex;
		while (endIndex < json.length()) {
			char c = json.charAt(endIndex);
			if (c == '{') {
				depth++;
			} else if (c == '}') {
				depth--;
				if (depth == 0) {
					break;
				}
			}
			endIndex++;
		}

		if (endIndex >= json.length()) {
			return null;
		}

		return json.substring(startIndex, endIndex + 1);
	}

	/**
	 * Extracts an array value from JSON
	 *
	 * @param json
	 *            the JSON string
	 * @param key
	 *            the key to extract
	 * @return the array JSON string including brackets, or null if not found
	 */
	static String extractArray(String json, String key) {
		String pattern = "\"" + key + "\":"; //$NON-NLS-1$ //$NON-NLS-2$
		int colonIndex = json.indexOf(pattern);
		if (colonIndex == -1) {
			return null;
		}

		int startIndex = colonIndex + pattern.length();
		while (startIndex < json.length()
				&& Character.isWhitespace(json.charAt(startIndex))) {
			startIndex++;
		}

		if (startIndex >= json.length()) {
			return null;
		}

		// Check for null
		if (json.startsWith("null", startIndex)) { //$NON-NLS-1$
			return null;
		}

		// Must start with [
		if (json.charAt(startIndex) != '[') {
			return null;
		}

		// Find matching closing bracket
		int depth = 0;
		int endIndex = startIndex;
		boolean inString = false;
		boolean escaped = false;

		while (endIndex < json.length()) {
			char c = json.charAt(endIndex);

			if (escaped) {
				escaped = false;
				endIndex++;
				continue;
			}

			if (c == '\\') {
				escaped = true;
				endIndex++;
				continue;
			}

			if (c == '"') {
				inString = !inString;
			}

			if (!inString) {
				if (c == '[') {
					depth++;
				} else if (c == ']') {
					depth--;
					if (depth == 0) {
						break;
					}
				}
			}

			endIndex++;
		}

		if (endIndex >= json.length()) {
			return null;
		}

		return json.substring(startIndex, endIndex + 1);
	}

	/**
	 * Parses an array of reviewers from GitHub's requested_reviewers JSON
	 *
	 * @param arrayJson
	 *            the JSON array string
	 * @return list of reviewer participants
	 */
	private static List<PullRequest.PullRequestParticipant> parseReviewers(
			String arrayJson) {
		List<PullRequest.PullRequestParticipant> reviewers = new ArrayList<>();

		if (arrayJson == null || arrayJson.trim().isEmpty()
				|| arrayJson.trim().equals("[]")) { //$NON-NLS-1$
			return reviewers;
		}

		arrayJson = arrayJson.trim();
		if (!arrayJson.startsWith("[")) { //$NON-NLS-1$
			return reviewers;
		}

		// Parse array
		int depth = 0;
		int start = 1;
		boolean inString = false;
		boolean escaped = false;

		for (int i = 1; i < arrayJson.length(); i++) {
			char c = arrayJson.charAt(i);

			// Handle escape sequences
			if (escaped) {
				escaped = false;
				continue;
			}
			if (c == '\\') {
				escaped = true;
				continue;
			}

			// Track string boundaries
			if (c == '"') {
				inString = !inString;
				continue;
			}

			// Only process structural characters when NOT inside a string
			if (!inString) {
				if (c == '{') {
					depth++;
				} else if (c == '}') {
					depth--;
					if (depth == 0) {
						String reviewerJson = arrayJson.substring(start, i + 1);
						PullRequest.PullRequestParticipant reviewer = parseReviewer(
								reviewerJson);
						if (reviewer != null) {
							reviewers.add(reviewer);
						}
						while (i + 1 < arrayJson.length()
								&& (arrayJson.charAt(i + 1) == ','
										|| Character.isWhitespace(
												arrayJson.charAt(i + 1)))) {
							i++;
						}
						start = i + 1;
					}
				}
			}
		}

		return reviewers;
	}

	/**
	 * Parses a single reviewer participant from GitHub JSON
	 *
	 * @param json
	 *            the JSON object representing a user or team
	 * @return the reviewer participant
	 */
	private static PullRequest.PullRequestParticipant parseReviewer(
			String json) {
		PullRequest.PullRequestParticipant reviewer = new PullRequest.PullRequestParticipant();

		// Create user object
		PullRequest.User user = new PullRequest.User();
		user.setName(extractString(json, "login")); //$NON-NLS-1$

		// For teams, use slug as name. GitHub's requested_teams payload does
		// not always include a type field.
		String type = extractString(json, "type"); //$NON-NLS-1$
		if ("Team".equals(type) || user.getName() == null) { //$NON-NLS-1$
			String slug = extractString(json, "slug"); //$NON-NLS-1$
			if (slug != null) {
				user.setName(slug);
			}
		}

		user.setDisplayName(extractString(json, "name", //$NON-NLS-1$
				extractString(json, "login"))); //$NON-NLS-1$
		user.setEmailAddress(extractString(json, "email")); //$NON-NLS-1$
		user.setAvatarUrl(extractString(json, "avatar_url")); //$NON-NLS-1$
		reviewer.setUser(user);
		reviewer.setRole("REVIEWER"); //$NON-NLS-1$

		// GitHub requested_reviewers are always pending (not yet approved)
		reviewer.setApproved(false);

		return reviewer;
	}

	/**
	 * Parses a list of commits from GitHub API JSON
	 *
	 * @param json
	 *            the JSON response (array of commits)
	 * @return list of pull request commits
	 */
	static List<PullRequestCommit> parseCommits(String json) {
		List<PullRequestCommit> result = new ArrayList<>();
		if (json == null || json.trim().isEmpty()
				|| json.trim().equals("[]")) { //$NON-NLS-1$
			return result;
		}

		// Parse array of commits
		json = json.trim();
		if (!json.startsWith("[")) { //$NON-NLS-1$
			return result;
		}

		// Parse array with string boundary tracking
		int depth = 0;
		int start = 1; // Skip opening [
		boolean inString = false;
		boolean escaped = false;

		for (int i = 1; i < json.length(); i++) {
			char c = json.charAt(i);

			// Handle escape sequences
			if (escaped) {
				escaped = false;
				continue;
			}
			if (c == '\\') {
				escaped = true;
				continue;
			}

			// Track string boundaries
			if (c == '"') {
				inString = !inString;
				continue;
			}

			// Only process structural characters when NOT inside a string
			if (!inString) {
				if (c == '{') {
					depth++;
				} else if (c == '}') {
					depth--;
					if (depth == 0) {
						// End of an object
						String commitJson = json.substring(start, i + 1);
						PullRequestCommit commit = parseCommit(commitJson);
						if (commit != null) {
							result.add(commit);
						}
						// Skip comma and whitespace
						while (i + 1 < json.length() && (json.charAt(i + 1) == ','
								|| Character.isWhitespace(json.charAt(i + 1)))) {
							i++;
						}
						start = i + 1;
					}
				}
			}
		}

		return result;
	}

	/**
	 * Parses a single commit from GitHub API JSON
	 *
	 * @param json
	 *            the JSON commit object
	 * @return the parsed commit, or null if parsing failed
	 */
	static PullRequestCommit parseCommit(String json) {
		if (json == null || json.trim().isEmpty()) {
			return null;
		}

		// Extract sha
		String sha = extractString(json, "sha"); //$NON-NLS-1$
		if (sha == null) {
			return null;
		}

		// Extract commit object
		String commitObject = extractObject(json, "commit"); //$NON-NLS-1$
		if (commitObject == null) {
			return null;
		}

		// Extract message from commit object
		String message = extractString(commitObject, "message"); //$NON-NLS-1$

		// Extract author object from commit object
		String authorObject = extractObject(commitObject, "author"); //$NON-NLS-1$
		String authorName = null;
		String authorEmail = null;
		long authorDate = 0;

		if (authorObject != null) {
			authorName = extractString(authorObject, "name"); //$NON-NLS-1$
			authorEmail = extractString(authorObject, "email"); //$NON-NLS-1$
			Date date = extractDate(authorObject, "date"); //$NON-NLS-1$
			if (date != null) {
				authorDate = date.getTime();
			}
		}

		// Extract parents array
		List<String> parents = new ArrayList<>();
		String parentsArray = extractArray(json, "parents"); //$NON-NLS-1$
		if (parentsArray != null && !parentsArray.equals("[]")) { //$NON-NLS-1$
			// Parse array elements
			int depth = 0;
			int start = 1; // Skip opening [
			boolean inString = false;
			boolean escaped = false;

			for (int i = 1; i < parentsArray.length(); i++) {
				char c = parentsArray.charAt(i);

				if (escaped) {
					escaped = false;
					continue;
				}
				if (c == '\\') {
					escaped = true;
					continue;
				}

				if (c == '"') {
					inString = !inString;
					continue;
				}

				if (!inString) {
					if (c == '{') {
						depth++;
					} else if (c == '}') {
						depth--;
						if (depth == 0) {
							String parentObject = parentsArray.substring(start,
									i + 1);
							String parentSha = extractString(parentObject,
									"sha"); //$NON-NLS-1$
							if (parentSha != null) {
								parents.add(parentSha);
							}
							while (i + 1 < parentsArray.length()
									&& (parentsArray.charAt(i + 1) == ','
											|| Character.isWhitespace(
													parentsArray.charAt(i + 1)))) {
								i++;
							}
							start = i + 1;
						}
					}
				}
			}
		}

		return new PullRequestCommit(sha, message, authorName, authorEmail,
				authorDate, parents);
	}
}

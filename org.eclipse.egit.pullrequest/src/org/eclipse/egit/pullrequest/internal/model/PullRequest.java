/*******************************************************************************
 * Copyright (C) 2026, Eclipse EGit contributors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.pullrequest.internal.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Domain model representing a Bitbucket Data Center pull request
 */
public class PullRequest {

	private long id;
	
	private int version;
	
	private String title;
	
	private String description;
	
	private String state;
	
	private boolean open;
	
	private boolean closed;
	
	private Date createdDate;
	
	private Date updatedDate;
	
	private PullRequestRef fromRef;
	
	private PullRequestRef toRef;
	
	private PullRequestParticipant author;
	
	private PullRequestLinks links;
	
	private int commentCount;

	private boolean isDraft;

	private String[] labels;

	private List<PullRequestParticipant> reviewers;
	
	/**
	 * @return the pull request ID
	 */
	public long getId() {
		return id;
	}
	
	/**
	 * @param id the pull request ID
	 */
	public void setId(long id) {
		this.id = id;
	}
	
	/**
	 * @return the version
	 */
	public int getVersion() {
		return version;
	}
	
	/**
	 * @param version the version
	 */
	public void setVersion(int version) {
		this.version = version;
	}
	
	/**
	 * @return the title
	 */
	public String getTitle() {
		return title;
	}
	
	/**
	 * @param title the title
	 */
	public void setTitle(String title) {
		this.title = title;
	}
	
	/**
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}
	
	/**
	 * @param description the description
	 */
	public void setDescription(String description) {
		this.description = description;
	}
	
	/**
	 * @return the state (OPEN, MERGED, DECLINED)
	 */
	public String getState() {
		return state;
	}
	
	/**
	 * @param state the state
	 */
	public void setState(String state) {
		this.state = state;
	}
	
	/**
	 * @return whether the PR is open
	 */
	public boolean isOpen() {
		return open;
	}
	
	/**
	 * @param open whether the PR is open
	 */
	public void setOpen(boolean open) {
		this.open = open;
	}
	
	/**
	 * @return whether the PR is closed
	 */
	public boolean isClosed() {
		return closed;
	}
	
	/**
	 * @param closed whether the PR is closed
	 */
	public void setClosed(boolean closed) {
		this.closed = closed;
	}
	
	/**
	 * @return the creation date
	 */
	public Date getCreatedDate() {
		return createdDate;
	}
	
	/**
	 * @param createdDate the creation date
	 */
	public void setCreatedDate(Date createdDate) {
		this.createdDate = createdDate;
	}
	
	/**
	 * @return the last updated date
	 */
	public Date getUpdatedDate() {
		return updatedDate;
	}
	
	/**
	 * @param updatedDate the last updated date
	 */
	public void setUpdatedDate(Date updatedDate) {
		this.updatedDate = updatedDate;
	}
	
	/**
	 * @return the source branch reference
	 */
	public PullRequestRef getFromRef() {
		return fromRef;
	}
	
	/**
	 * @param fromRef the source branch reference
	 */
	public void setFromRef(PullRequestRef fromRef) {
		this.fromRef = fromRef;
	}
	
	/**
	 * @return the destination branch reference
	 */
	public PullRequestRef getToRef() {
		return toRef;
	}
	
	/**
	 * @param toRef the destination branch reference
	 */
	public void setToRef(PullRequestRef toRef) {
		this.toRef = toRef;
	}
	
	/**
	 * @return the author
	 */
	public PullRequestParticipant getAuthor() {
		return author;
	}
	
	/**
	 * @param author the author
	 */
	public void setAuthor(PullRequestParticipant author) {
		this.author = author;
	}
	
	/**
	 * @return the links
	 */
	public PullRequestLinks getLinks() {
		return links;
	}
	
	/**
	 * @param links the links
	 */
	public void setLinks(PullRequestLinks links) {
		this.links = links;
	}
	
	/**
	 * @return the comment count
	 */
	public int getCommentCount() {
		return commentCount;
	}
	
	/**
	 * @param commentCount the comment count
	 */
	public void setCommentCount(int commentCount) {
		this.commentCount = commentCount;
	}

	/**
	 * @return whether the PR is a draft
	 */
	public boolean isDraft() {
		return isDraft;
	}

	/**
	 * @param isDraft whether the PR is a draft
	 */
	public void setDraft(boolean isDraft) {
		this.isDraft = isDraft;
	}

	/**
	 * @return the labels associated with this PR
	 */
	public String[] getLabels() {
		return labels;
	}

	/**
	 * @param labels the labels to set
	 */
	public void setLabels(String[] labels) {
		this.labels = labels;
	}

	/**
	 * Gets the list of reviewers for this pull request. Reviewers are
	 * participants who have been explicitly added to review the PR, distinct
	 * from the author or other participants.
	 *
	 * @return an unmodifiable list of reviewers, never null
	 */
	public List<PullRequestParticipant> getReviewers() {
		if (reviewers == null) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(reviewers);
	}

	/**
	 * Sets the list of reviewers for this pull request. The provided list is
	 * copied defensively to prevent external modification.
	 *
	 * @param reviewers
	 *            the list of reviewers to set, may be null
	 */
	public void setReviewers(List<PullRequestParticipant> reviewers) {
		if (reviewers == null) {
			this.reviewers = null;
		} else {
			this.reviewers = new ArrayList<>(reviewers);
		}
	}
	
	/**
	 * Represents a branch reference in a pull request
	 */
	public static class PullRequestRef {
		private String id;
		private String displayId;
		private Repository repository;
		private String latestCommit;
		
		/**
		 * @return the ref ID
		 */
		public String getId() {
			return id;
		}
		
		/**
		 * @param id the ref ID
		 */
		public void setId(String id) {
			this.id = id;
		}
		
		/**
		 * @return the display ID
		 */
		public String getDisplayId() {
			return displayId;
		}
		
		/**
		 * @param displayId the display ID
		 */
		public void setDisplayId(String displayId) {
			this.displayId = displayId;
		}
		
		/**
		 * @return the repository
		 */
		public Repository getRepository() {
			return repository;
		}
		
		/**
		 * @param repository the repository
		 */
		public void setRepository(Repository repository) {
			this.repository = repository;
		}
		
		/**
		 * @return the latest commit SHA
		 */
		public String getLatestCommit() {
			return latestCommit;
		}
		
		/**
		 * @param latestCommit the latest commit SHA
		 */
		public void setLatestCommit(String latestCommit) {
			this.latestCommit = latestCommit;
		}
	}
	
	/**
	 * Represents a repository in a pull request reference
	 */
	public static class Repository {
		private String slug;
		private String name;
		private Project project;
		private String cloneUrl;
		
		/**
		 * @return the repository slug
		 */
		public String getSlug() {
			return slug;
		}
		
		/**
		 * @param slug the repository slug
		 */
		public void setSlug(String slug) {
			this.slug = slug;
		}
		
		/**
		 * @return the repository name
		 */
		public String getName() {
			return name;
		}
		
		/**
		 * @param name the repository name
		 */
		public void setName(String name) {
			this.name = name;
		}
		
		/**
		 * @return the project
		 */
		public Project getProject() {
			return project;
		}
		
		/**
		 * @param project the project
		 */
		public void setProject(Project project) {
			this.project = project;
		}
		
		/**
		 * Get the clone URL for this repository. This is used for fork-based
		 * pull requests where the source branch is in a different repository.
		 *
		 * @return the HTTPS clone URL, or null if not available
		 */
		public String getCloneUrl() {
			return cloneUrl;
		}
		
		/**
		 * Set the clone URL for this repository.
		 *
		 * @param cloneUrl
		 *            the HTTPS clone URL
		 */
		public void setCloneUrl(String cloneUrl) {
			this.cloneUrl = cloneUrl;
		}
	}
	
	/**
	 * Represents a project in a repository
	 */
	public static class Project {
		private String key;
		private String name;
		
		/**
		 * @return the project key
		 */
		public String getKey() {
			return key;
		}
		
		/**
		 * @param key the project key
		 */
		public void setKey(String key) {
			this.key = key;
		}
		
		/**
		 * @return the project name
		 */
		public String getName() {
			return name;
		}
		
		/**
		 * @param name the project name
		 */
		public void setName(String name) {
			this.name = name;
		}
	}
	
	/**
	 * Represents a participant (author or reviewer) in a pull request
	 */
	public static class PullRequestParticipant {
		private User user;
		private String role;
		private boolean approved;
		
		/**
		 * @return the user
		 */
		public User getUser() {
			return user;
		}
		
		/**
		 * @param user the user
		 */
		public void setUser(User user) {
			this.user = user;
		}
		
		/**
		 * @return the role
		 */
		public String getRole() {
			return role;
		}
		
		/**
		 * @param role the role
		 */
		public void setRole(String role) {
			this.role = role;
		}
		
		/**
		 * @return whether the participant has approved
		 */
		public boolean isApproved() {
			return approved;
		}
		
		/**
		 * @param approved whether the participant has approved
		 */
		public void setApproved(boolean approved) {
			this.approved = approved;
		}
	}
	
	/**
	 * Represents a user
	 */
	public static class User {
		private String name;
		private String emailAddress;
		private String displayName;
		
		/**
		 * @return the user name
		 */
		public String getName() {
			return name;
		}
		
		/**
		 * @param name the user name
		 */
		public void setName(String name) {
			this.name = name;
		}
		
		/**
		 * @return the email address
		 */
		public String getEmailAddress() {
			return emailAddress;
		}
		
		/**
		 * @param emailAddress the email address
		 */
		public void setEmailAddress(String emailAddress) {
			this.emailAddress = emailAddress;
		}
		
		/**
		 * @return the display name
		 */
		public String getDisplayName() {
			return displayName;
		}
		
		/**
		 * @param displayName the display name
		 */
		public void setDisplayName(String displayName) {
			this.displayName = displayName;
		}
	}
	
	/**
	 * Represents links associated with a pull request
	 */
	public static class PullRequestLinks {
		private Link[] self;
		
		/**
		 * @return the self links
		 */
		public Link[] getSelf() {
			return self;
		}
		
		/**
		 * @param self the self links
		 */
		public void setSelf(Link[] self) {
			this.self = self;
		}
	}
	
	/**
	 * Represents a link
	 */
	public static class Link {
		private String href;
		
		/**
		 * @return the href
		 */
		public String getHref() {
			return href;
		}
		
		/**
		 * @param href the href
		 */
		public void setHref(String href) {
			this.href = href;
		}
	}
}

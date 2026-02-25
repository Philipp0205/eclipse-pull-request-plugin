# Issue #12: Repository Browser View

## Overview

Add a new Eclipse view that lists repositories accessible to the current user,
allowing them to browse, search, filter, and select a repository to configure
the plugin against. This replaces the current manual preference-page approach
where users must type in project keys and repository slugs by hand.

- **GitHub**: `GET /user/repos` (lists repositories for the authenticated user,
  paginated, supports `type`, `sort`, `direction` params).
- **Bitbucket**: `GET /rest/api/1.0/repos` (lists all repositories the user
  has access to, paginated, supports `name` filter).

## Dependencies

- None. Can be implemented independently.
- **Complements preferences page**: After selecting a repository from the
  browser, the plugin should update `PRPreferences` values automatically.

## Implementation Order

No ordering constraints. Can be done in any position.

---

## Step 1: Create `RepositoryInfo` model class

**File** (new): `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/model/RepositoryInfo.java`

```java
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

/**
 * Lightweight model representing a repository returned by the
 * repository listing API. Used by the Repository Browser view
 * to display available repositories.
 */
public class RepositoryInfo {

	private String name;

	private String fullName;

	private String description;

	private String owner;

	private String cloneUrl;

	private boolean isPrivate;

	private boolean isFork;

	// Bitbucket-specific
	private String projectKey;

	private String slug;

	/**
	 * @return the repository name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            the repository name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the full name (e.g., "owner/repo" for GitHub,
	 *         "PROJECT/repo" for Bitbucket)
	 */
	public String getFullName() {
		return fullName;
	}

	/**
	 * @param fullName
	 *            the full name
	 */
	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	/**
	 * @return the repository description, or null
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @param description
	 *            the repository description
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * @return the owner username or project key
	 */
	public String getOwner() {
		return owner;
	}

	/**
	 * @param owner
	 *            the owner
	 */
	public void setOwner(String owner) {
		this.owner = owner;
	}

	/**
	 * @return the HTTPS clone URL
	 */
	public String getCloneUrl() {
		return cloneUrl;
	}

	/**
	 * @param cloneUrl
	 *            the clone URL
	 */
	public void setCloneUrl(String cloneUrl) {
		this.cloneUrl = cloneUrl;
	}

	/**
	 * @return whether this is a private repository
	 */
	public boolean isPrivate() {
		return isPrivate;
	}

	/**
	 * @param isPrivate
	 *            whether this is a private repository
	 */
	public void setPrivate(boolean isPrivate) {
		this.isPrivate = isPrivate;
	}

	/**
	 * @return whether this is a fork
	 */
	public boolean isFork() {
		return isFork;
	}

	/**
	 * @param isFork
	 *            whether this is a fork
	 */
	public void setFork(boolean isFork) {
		this.isFork = isFork;
	}

	/**
	 * @return the Bitbucket project key, or null for GitHub
	 */
	public String getProjectKey() {
		return projectKey;
	}

	/**
	 * @param projectKey
	 *            the Bitbucket project key
	 */
	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}

	/**
	 * @return the Bitbucket repository slug, or null for GitHub
	 */
	public String getSlug() {
		return slug;
	}

	/**
	 * @param slug
	 *            the Bitbucket repository slug
	 */
	public void setSlug(String slug) {
		this.slug = slug;
	}
}
```

---

## Step 2: Add `listRepositories()` to `IPullRequestClient`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/client/IPullRequestClient.java`

Add after `getCapabilities()` (after line 331):

```java
/**
 * Lists repositories accessible to the authenticated user.
 *
 * @param filter
 *            optional name filter string, or null for all
 * @param limit
 *            the maximum number of results
 * @param start
 *            the start index for pagination
 * @return list of repository info objects
 * @throws IOException
 *             if the request fails
 */
@NonNull
List<RepositoryInfo> listRepositories(
		@Nullable String filter, int limit, int start)
		throws IOException;
```

Add import:
```java
import org.eclipse.egit.pullrequest.internal.model.RepositoryInfo;
```

---

## Step 3: Implement in `GitHubClient`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/github/GitHubClient.java`

```java
@Override
@NonNull
public List<RepositoryInfo> listRepositories(
		@Nullable String filter, int limit, int start)
		throws IOException {
	// GitHub: GET /user/repos?per_page={limit}&page={page}
	// &sort=updated&direction=desc
	int page = (start / Math.max(limit, 1)) + 1;
	String path = "/user/repos?per_page=" + limit //$NON-NLS-1$
			+ "&page=" + page //$NON-NLS-1$
			+ "&sort=updated&direction=desc"; //$NON-NLS-1$

	String response = doGet(path);
	List<RepositoryInfo> repos = GitHubJsonParser
			.parseRepositories(response);

	// Client-side filter if a filter string is provided
	if (filter != null && !filter.isEmpty()) {
		String lowerFilter = filter.toLowerCase();
		repos.removeIf(r -> {
			String name = r.getFullName();
			return name == null || !name.toLowerCase()
					.contains(lowerFilter);
		});
	}
	return repos;
}
```

---

## Step 4: Implement in `BitbucketClient`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketClient.java`

```java
@Override
@NonNull
public List<RepositoryInfo> listRepositories(
		@Nullable String filter, int limit, int start)
		throws IOException {
	// Bitbucket: GET /rest/api/1.0/repos?limit={limit}&start={start}
	// Optionally: &name={filter}
	StringBuilder url = new StringBuilder();
	url.append(serverUrl).append(API_BASE_PATH);
	url.append("/repos?limit=").append(limit); //$NON-NLS-1$
	url.append("&start=").append(start); //$NON-NLS-1$

	if (filter != null && !filter.isEmpty()) {
		url.append("&name="); //$NON-NLS-1$
		url.append(java.net.URLEncoder.encode(
				filter, "UTF-8")); //$NON-NLS-1$
	}

	String response = executeGet(url.toString());
	return BitbucketJsonParser
			.parseRepositories(response);
}
```

---

## Step 5: Add parsing to both JSON parsers

### 5a. `GitHubJsonParser`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/github/GitHubJsonParser.java`

```java
/**
 * Parses a JSON array of repository objects from the GitHub
 * user/repos endpoint.
 *
 * @param json
 *            the JSON array string
 * @return list of repository info objects
 */
public static List<RepositoryInfo> parseRepositories(
		String json) {
	List<RepositoryInfo> repos = new ArrayList<>();
	if (json == null || json.isEmpty()) {
		return repos;
	}

	int idx = 0;
	while (true) {
		int objStart = json.indexOf('{', idx);
		if (objStart == -1) {
			break;
		}
		int objEnd = findMatchingBrace(json, objStart);
		if (objEnd == -1) {
			break;
		}
		String obj = json.substring(
				objStart, objEnd + 1);

		RepositoryInfo repo = new RepositoryInfo();
		repo.setName(
				extractStringValue(obj, "name")); //$NON-NLS-1$
		repo.setFullName(
				extractStringValue(
						obj, "full_name")); //$NON-NLS-1$
		repo.setDescription(
				extractStringValue(
						obj, "description")); //$NON-NLS-1$
		repo.setCloneUrl(
				extractStringValue(
						obj, "clone_url")); //$NON-NLS-1$
		repo.setPrivate(
				extractBooleanValue(
						obj, "private")); //$NON-NLS-1$
		repo.setFork(
				extractBooleanValue(
						obj, "fork")); //$NON-NLS-1$

		// Extract owner login
		int ownerIdx = obj.indexOf("\"owner\""); //$NON-NLS-1$
		if (ownerIdx != -1) {
			int braceStart = obj.indexOf('{', ownerIdx);
			if (braceStart != -1) {
				int braceEnd = findMatchingBrace(
						obj, braceStart);
				if (braceEnd != -1) {
					String ownerObj = obj.substring(
							braceStart, braceEnd + 1);
					repo.setOwner(extractStringValue(
							ownerObj, "login")); //$NON-NLS-1$
				}
			}
		}

		repos.add(repo);
		idx = objEnd + 1;
	}
	return repos;
}
```

### 5b. `BitbucketJsonParser`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketJsonParser.java`

```java
/**
 * Parses a paginated response of repository objects from the
 * Bitbucket repos endpoint.
 *
 * @param json
 *            the JSON response string (with "values" array)
 * @return list of repository info objects
 */
public static List<RepositoryInfo> parseRepositories(
		String json) {
	List<RepositoryInfo> repos = new ArrayList<>();
	if (json == null || json.isEmpty()) {
		return repos;
	}

	// Find the "values" array
	int valuesIdx = json.indexOf("\"values\""); //$NON-NLS-1$
	if (valuesIdx == -1) {
		return repos;
	}
	int arrStart = json.indexOf('[', valuesIdx);
	if (arrStart == -1) {
		return repos;
	}
	int arrEnd = findMatchingBracket(json, arrStart);
	String valuesArr = json.substring(
			arrStart + 1, arrEnd);

	int idx = 0;
	while (true) {
		int objStart = valuesArr.indexOf('{', idx);
		if (objStart == -1) {
			break;
		}
		int objEnd = findMatchingBrace(
				valuesArr, objStart);
		if (objEnd == -1) {
			break;
		}
		String obj = valuesArr.substring(
				objStart, objEnd + 1);

		RepositoryInfo repo = new RepositoryInfo();
		repo.setName(
				extractStringValue(obj, "name")); //$NON-NLS-1$
		repo.setSlug(
				extractStringValue(obj, "slug")); //$NON-NLS-1$
		repo.setDescription(
				extractStringValue(
						obj, "description")); //$NON-NLS-1$
		repo.setPrivate(!extractBooleanValue(
				obj, "public")); //$NON-NLS-1$
		repo.setFork(extractBooleanValue(
				obj, "forkable")); //$NON-NLS-1$

		// Extract project key
		int projIdx = obj.indexOf("\"project\""); //$NON-NLS-1$
		if (projIdx != -1) {
			int braceStart = obj.indexOf('{', projIdx);
			if (braceStart != -1) {
				int braceEnd = findMatchingBrace(
						obj, braceStart);
				if (braceEnd != -1) {
					String projObj = obj.substring(
							braceStart, braceEnd + 1);
					String key = extractStringValue(
							projObj, "key"); //$NON-NLS-1$
					repo.setProjectKey(key);
					repo.setOwner(key);
				}
			}
		}

		repo.setFullName(
				repo.getProjectKey() + "/" //$NON-NLS-1$
						+ repo.getSlug());

		// Extract clone URL from links
		int linksIdx = obj.indexOf("\"links\""); //$NON-NLS-1$
		if (linksIdx != -1) {
			int cloneIdx = obj.indexOf(
					"\"clone\"", linksIdx); //$NON-NLS-1$
			if (cloneIdx != -1) {
				int cloneArrStart = obj.indexOf(
						'[', cloneIdx);
				if (cloneArrStart != -1) {
					// Find https clone URL
					String httpsUrl = extractCloneUrl(
							obj, cloneArrStart, "http"); //$NON-NLS-1$
					repo.setCloneUrl(httpsUrl);
				}
			}
		}

		repos.add(repo);
		idx = objEnd + 1;
	}
	return repos;
}

/**
 * Extracts a clone URL of the given type from a Bitbucket
 * clone links array.
 */
private static String extractCloneUrl(String json,
		int arrStart, String namePrefix) {
	int searchIdx = arrStart;
	while (true) {
		int objStart = json.indexOf('{', searchIdx);
		if (objStart == -1) {
			break;
		}
		int objEnd = findMatchingBrace(json, objStart);
		if (objEnd == -1) {
			break;
		}
		String obj = json.substring(
				objStart, objEnd + 1);
		String name = extractStringValue(
				obj, "name"); //$NON-NLS-1$
		if (name != null
				&& name.startsWith(namePrefix)) {
			return extractStringValue(
					obj, "href"); //$NON-NLS-1$
		}
		searchIdx = objEnd + 1;
	}
	return null;
}
```

---

## Step 6: Add NLS strings

**File**: `PRText.java` — add before `static {}`:

```java
/** */
public static String RepositoryBrowser_ViewName;

/** */
public static String RepositoryBrowser_SearchHint;

/** */
public static String RepositoryBrowser_RefreshAction;

/** */
public static String RepositoryBrowser_RefreshTooltip;

/** */
public static String RepositoryBrowser_SelectAction;

/** */
public static String RepositoryBrowser_SelectTooltip;

/** */
public static String RepositoryBrowser_LoadingJob;

/** */
public static String RepositoryBrowser_LoadError;

/** */
public static String RepositoryBrowser_NoRepositories;

/** */
public static String RepositoryBrowser_ColumnName;

/** */
public static String RepositoryBrowser_ColumnOwner;

/** */
public static String RepositoryBrowser_ColumnDescription;

/** */
public static String RepositoryBrowser_ConfiguredMessage;
```

**File**: `prtext.properties` — append:

```properties
RepositoryBrowser_ViewName=Repository Browser
RepositoryBrowser_SearchHint=Type to search repositories...
RepositoryBrowser_RefreshAction=Refresh
RepositoryBrowser_RefreshTooltip=Refresh repository list
RepositoryBrowser_SelectAction=Select Repository
RepositoryBrowser_SelectTooltip=Use this repository for pull request reviews
RepositoryBrowser_LoadingJob=Loading repositories
RepositoryBrowser_LoadError=Failed to load repositories
RepositoryBrowser_NoRepositories=No repositories found
RepositoryBrowser_ColumnName=Name
RepositoryBrowser_ColumnOwner=Owner
RepositoryBrowser_ColumnDescription=Description
RepositoryBrowser_ConfiguredMessage=Repository configured: {0}
```

---

## Step 7: Create `RepositoryBrowserView`

**File** (new): `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/RepositoryBrowserView.java`

```java
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
package org.eclipse.egit.pullrequest.internal.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRPreferences;
import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestClientFactory;
import org.eclipse.egit.pullrequest.internal.client.PullRequestProviderType;
import org.eclipse.egit.pullrequest.internal.model.RepositoryInfo;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

/**
 * A view that lists repositories accessible to the current user,
 * allowing search/filter and selection to configure the plugin.
 */
public class RepositoryBrowserView extends ViewPart {

	/**
	 * The view ID as registered in plugin.xml.
	 */
	public static final String VIEW_ID =
			"org.eclipse.egit.pullrequest.RepositoryBrowserView"; //$NON-NLS-1$

	private TableViewer tableViewer;

	private Text searchText;

	private List<RepositoryInfo> allRepositories =
			new ArrayList<>();

	private IPullRequestClient client;

	@Override
	public void createPartControl(Composite parent) {
		GridLayoutFactory.fillDefaults().margins(5, 5)
				.applyTo(parent);

		// Search bar
		searchText = new Text(parent,
				SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH);
		searchText.setMessage(
				PRText.RepositoryBrowser_SearchHint);
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(searchText);

		// Table
		tableViewer = new TableViewer(parent,
				SWT.FULL_SELECTION | SWT.BORDER
						| SWT.V_SCROLL | SWT.H_SCROLL);
		Table table = tableViewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		GridDataFactory.fillDefaults().grab(true, true)
				.applyTo(table);

		createColumns();

		tableViewer.setContentProvider(
				ArrayContentProvider.getInstance());
		tableViewer.setComparator(new ViewerComparator());

		// Double-click to select
		tableViewer.addDoubleClickListener(
				event -> selectRepository());

		// Search filter
		searchText.addModifyListener(
				(ModifyListener) e -> filterRepositories());

		// Toolbar actions
		createToolbarActions();

		// Initial load
		initializeClient();
		loadRepositories(null);
	}

	private void createColumns() {
		// Name column
		TableViewerColumn nameCol =
				new TableViewerColumn(tableViewer, SWT.NONE);
		nameCol.getColumn().setText(
				PRText.RepositoryBrowser_ColumnName);
		nameCol.getColumn().setWidth(200);
		nameCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((RepositoryInfo) element)
						.getFullName();
			}
		});

		// Owner column
		TableViewerColumn ownerCol =
				new TableViewerColumn(tableViewer, SWT.NONE);
		ownerCol.getColumn().setText(
				PRText.RepositoryBrowser_ColumnOwner);
		ownerCol.getColumn().setWidth(120);
		ownerCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((RepositoryInfo) element).getOwner();
			}
		});

		// Description column
		TableViewerColumn descCol =
				new TableViewerColumn(tableViewer, SWT.NONE);
		descCol.getColumn().setText(
				PRText.RepositoryBrowser_ColumnDescription);
		descCol.getColumn().setWidth(300);
		descCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				String desc = ((RepositoryInfo) element)
						.getDescription();
				return desc != null ? desc : ""; //$NON-NLS-1$
			}
		});
	}

	private void createToolbarActions() {
		IToolBarManager toolbar = getViewSite()
				.getActionBars().getToolBarManager();

		Action refreshAction = new Action(
				PRText.RepositoryBrowser_RefreshAction) {
			@Override
			public void run() {
				loadRepositories(searchText.getText());
			}
		};
		refreshAction.setToolTipText(
				PRText.RepositoryBrowser_RefreshTooltip);

		Action selectAction = new Action(
				PRText.RepositoryBrowser_SelectAction) {
			@Override
			public void run() {
				selectRepository();
			}
		};
		selectAction.setToolTipText(
				PRText.RepositoryBrowser_SelectTooltip);

		toolbar.add(refreshAction);
		toolbar.add(new Separator());
		toolbar.add(selectAction);
	}

	private void initializeClient() {
		try {
			client = PullRequestClientFactory
					.createClient();
		} catch (Exception e) {
			Activator.logError(
					"Failed to create client", e); //$NON-NLS-1$
		}
	}

	private void loadRepositories(String filter) {
		if (client == null) {
			return;
		}
		Job job = new Job(
				PRText.RepositoryBrowser_LoadingJob) {
			@Override
			protected IStatus run(
					IProgressMonitor monitor) {
				try {
					List<RepositoryInfo> repos =
							client.listRepositories(
									filter, 100, 0);
					Display.getDefault().asyncExec(() -> {
						allRepositories.clear();
						allRepositories.addAll(repos);
						tableViewer.setInput(
								allRepositories);
					});
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							PRText.RepositoryBrowser_LoadError,
							e);
					return new Status(IStatus.ERROR,
							Activator.PLUGIN_ID,
							PRText.RepositoryBrowser_LoadError,
							e);
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}

	private void filterRepositories() {
		String filter = searchText.getText()
				.toLowerCase().trim();
		if (filter.isEmpty()) {
			tableViewer.setInput(allRepositories);
		} else {
			List<RepositoryInfo> filtered =
					new ArrayList<>();
			for (RepositoryInfo r : allRepositories) {
				String name = r.getFullName();
				String desc = r.getDescription();
				if ((name != null && name.toLowerCase()
						.contains(filter))
						|| (desc != null && desc
								.toLowerCase()
								.contains(filter))) {
					filtered.add(r);
				}
			}
			tableViewer.setInput(filtered);
		}
	}

	private void selectRepository() {
		IStructuredSelection sel =
				tableViewer.getStructuredSelection();
		if (sel.isEmpty()) {
			return;
		}
		RepositoryInfo repo =
				(RepositoryInfo) sel.getFirstElement();
		applyRepositoryToPreferences(repo);
	}

	/**
	 * Writes the selected repository's details into the plugin
	 * preferences, so the rest of the plugin uses it.
	 */
	private void applyRepositoryToPreferences(
			RepositoryInfo repo) {
		IPreferenceStore store = Activator.getDefault()
				.getPreferenceStore();

		PullRequestProviderType providerType =
				client.getProviderType();
		store.setValue(
				PRPreferences.PULLREQUEST_PROVIDER_TYPE,
				providerType.name());

		if (providerType == PullRequestProviderType.GITHUB) {
			store.setValue(PRPreferences.GITHUB_OWNER,
					repo.getOwner());
			store.setValue(PRPreferences.GITHUB_REPO,
					repo.getName());
		} else if (providerType
				== PullRequestProviderType.BITBUCKET) {
			store.setValue(
					PRPreferences.BITBUCKET_PROJECT_KEY,
					repo.getProjectKey());
			store.setValue(
					PRPreferences.BITBUCKET_REPO_SLUG,
					repo.getSlug());
		}

		Activator.logInfo(
				String.format(
						PRText.RepositoryBrowser_ConfiguredMessage,
						repo.getFullName()));
	}

	@Override
	public void setFocus() {
		if (searchText != null
				&& !searchText.isDisposed()) {
			searchText.setFocus();
		}
	}
}
```

---

## Step 8: Register in `plugin.xml`

**File**: `org.eclipse.egit.pullrequest/plugin.xml`

Add inside the `<extension point="org.eclipse.ui.views">` block,
after the existing `PullRequestCommentsView` (after line 79):

```xml
<view
      allowMultiple="false"
      category="org.eclipse.egit.pullrequest.PullRequestCategory"
      class="org.eclipse.egit.pullrequest.internal.ui.RepositoryBrowserView"
      icon="icons/obj16/gitrepository.png"
      id="org.eclipse.egit.pullrequest.RepositoryBrowserView"
      name="%RepositoryBrowserView">
</view>
```

**File**: `org.eclipse.egit.pullrequest/plugin.properties`

Add:

```properties
RepositoryBrowserView=Repository Browser
```

---

## Step 9: Add to perspective

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestPerspectiveFactory.java`

Add a show-view shortcut after line 56:

```java
layout.addShowViewShortcut(RepositoryBrowserView.VIEW_ID);
```

Add import:
```java
// Already in same package, no import needed
```

---

## Step 10: Add tests

**File**: `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/github/GitHubJsonParserTest.java`

```java
@Test
public void testParseRepositories() {
	String json = "[{\"name\":\"my-repo\","
			+ "\"full_name\":\"user/my-repo\","
			+ "\"description\":\"A test repo\","
			+ "\"private\":true,\"fork\":false,"
			+ "\"clone_url\":\"https://github.com/user/my-repo.git\","
			+ "\"owner\":{\"login\":\"user\"}}]";
	List<RepositoryInfo> repos = GitHubJsonParser
			.parseRepositories(json);
	assertThat(repos, hasSize(1));
	assertThat(repos.get(0).getName(),
			equalTo("my-repo"));
	assertThat(repos.get(0).getFullName(),
			equalTo("user/my-repo"));
	assertThat(repos.get(0).getOwner(),
			equalTo("user"));
	assertThat(repos.get(0).isPrivate(),
			equalTo(true));
	assertThat(repos.get(0).getCloneUrl(),
			equalTo("https://github.com/user/my-repo.git"));
}
```

**File**: `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketClientTest.java`

```java
@Test
public void testParseRepositories() {
	String json = "{\"values\":[{\"name\":\"my-repo\","
			+ "\"slug\":\"my-repo\","
			+ "\"description\":\"A BB repo\","
			+ "\"public\":true,\"forkable\":true,"
			+ "\"project\":{\"key\":\"PROJ\","
			+ "\"name\":\"Project\"}}]}";
	List<RepositoryInfo> repos = BitbucketJsonParser
			.parseRepositories(json);
	assertThat(repos, hasSize(1));
	assertThat(repos.get(0).getName(),
			equalTo("my-repo"));
	assertThat(repos.get(0).getSlug(),
			equalTo("my-repo"));
	assertThat(repos.get(0).getProjectKey(),
			equalTo("PROJ"));
	assertThat(repos.get(0).getFullName(),
			equalTo("PROJ/my-repo"));
	assertThat(repos.get(0).isPrivate(),
			equalTo(false));
}
```

---

## Verification

1. `mvn clean verify -DskipTests` — build succeeds
2. `cd org.eclipse.egit.pullrequest.test && mvn test` — all tests pass
3. Manual: Open the Repository Browser view from Window > Show View,
   verify repositories load, search filter works, double-click selects
   a repository and updates preferences

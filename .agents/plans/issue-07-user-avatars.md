# Issue #7: User Avatars

## Overview

Display real user avatar images (from GitHub/Bitbucket) in the inline comment
composites and the overview view reviewer sidebar, instead of initials-only
circles. Requires:

1. Adding `avatarUrl` to the `User` model and `authorAvatarUrl` to
   `PullRequestComment`.
2. Parsing avatar URLs from both providers' JSON responses.
3. An asynchronous image cache (`AvatarCache`) that downloads avatar images
   in the background using Eclipse Jobs, stores them as SWT `Image` objects,
   and disposes them on shutdown.
4. Updating `ExpandedCommentComposite.createAvatarCanvas()` and
   `PullRequestOverviewView.createAvatarCircle()` to use real images when
   available, falling back to initials.

## Dependencies

- None. Can be implemented independently.

## Implementation Order

No ordering constraints. Can be done in any position.

---

## Step 1: Add `avatarUrl` to `PullRequest.User`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/model/PullRequest.java`

In the `User` inner class (line 538), add a new field after `displayName`
(line 541):

```java
private String avatarUrl;
```

Add getter/setter after `setDisplayName()` (after line 583):

```java
/**
 * @return the avatar URL, or null if not available
 */
public String getAvatarUrl() {
	return avatarUrl;
}

/**
 * @param avatarUrl
 *            the avatar URL
 */
public void setAvatarUrl(String avatarUrl) {
	this.avatarUrl = avatarUrl;
}
```

---

## Step 2: Add `authorAvatarUrl` to `PullRequestComment`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/model/PullRequestComment.java`

Add field after `authorEmail` (after line 33):

```java
private String authorAvatarUrl;
```

Add getter/setter after `setAuthorEmail()` (after line 164):

```java
/**
 * @return the author's avatar URL, or null if not available
 */
public String getAuthorAvatarUrl() {
	return authorAvatarUrl;
}

/**
 * @param authorAvatarUrl
 *            the author's avatar URL
 */
public void setAuthorAvatarUrl(String authorAvatarUrl) {
	this.authorAvatarUrl = authorAvatarUrl;
}
```

---

## Step 3: Parse avatar URLs in `GitHubJsonParser`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/github/GitHubJsonParser.java`

### 3a. In the method that parses PR JSON (user objects)

Wherever a `User` object is created from JSON containing
`"avatar_url":"..."`, extract it:

```java
// After extracting displayName/login for User:
String avatarUrl = extractStringValue(
		userObj, "avatar_url"); //$NON-NLS-1$
user.setAvatarUrl(avatarUrl);
```

### 3b. In the method that parses comment JSON

Wherever `PullRequestComment` author fields are populated:

```java
// After setting authorEmail:
String avatarUrl = extractStringValue(
		userObj, "avatar_url"); //$NON-NLS-1$
comment.setAuthorAvatarUrl(avatarUrl);
```

GitHub user objects always contain `avatar_url`. Both PR participants
and comment authors use the same user object structure.

---

## Step 4: Parse avatar URLs in `BitbucketJsonParser`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketJsonParser.java`

Bitbucket Data Center user objects contain an `"avatarUrl"` field
(note: camelCase, not snake_case like GitHub).

### 4a. In user parsing

```java
// After extracting displayName:
String avatarUrl = extractStringValue(
		userObj, "avatarUrl"); //$NON-NLS-1$
user.setAvatarUrl(avatarUrl);
```

### 4b. In comment parsing

```java
// After setting authorEmail:
String avatarUrl = extractStringValue(
		authorObj, "avatarUrl"); //$NON-NLS-1$
comment.setAuthorAvatarUrl(avatarUrl);
```

**Note**: Bitbucket's `avatarUrl` may be a relative path (e.g.,
`/users/jdoe/avatar.png`). The cache should handle prepending the
server URL when the URL doesn't start with `http`.

---

## Step 5: Create `AvatarCache` utility

**File** (new): `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/AvatarCache.java`

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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Display;

/**
 * Asynchronous cache for user avatar images. Downloads images in
 * background Jobs and notifies a callback when the image is ready.
 * All cached {@link Image} objects are disposed when
 * {@link #dispose()} is called.
 *
 * <p>
 * Usage:
 * <pre>
 * Image img = AvatarCache.getDefault().getAvatar(
 *     avatarUrl, size, widget::redraw);
 * </pre>
 * Returns {@code null} on the first call (image is being fetched),
 * then returns the cached image on subsequent calls.
 * </p>
 */
public class AvatarCache {

	private static AvatarCache instance;

	private final Map<String, Image> cache =
			new ConcurrentHashMap<>();

	private final Map<String, Boolean> pending =
			new ConcurrentHashMap<>();

	/**
	 * @return the singleton instance
	 */
	public static synchronized AvatarCache getDefault() {
		if (instance == null) {
			instance = new AvatarCache();
		}
		return instance;
	}

	/**
	 * Returns a cached avatar image, or {@code null} if the image
	 * is not yet loaded. If not cached, triggers an asynchronous
	 * download and invokes the callback when ready.
	 *
	 * @param avatarUrl
	 *            the avatar URL (may be null)
	 * @param size
	 *            desired image size in pixels (square)
	 * @param onLoaded
	 *            callback invoked on the UI thread when the
	 *            image is ready, or {@code null}
	 * @return the avatar image, or {@code null} if not yet loaded
	 *         or URL is null
	 */
	public Image getAvatar(String avatarUrl, int size,
			Runnable onLoaded) {
		if (avatarUrl == null || avatarUrl.isEmpty()) {
			return null;
		}

		String key = avatarUrl + "#" + size; //$NON-NLS-1$
		Image cached = cache.get(key);
		if (cached != null && !cached.isDisposed()) {
			return cached;
		}

		// Already fetching?
		if (pending.containsKey(key)) {
			return null;
		}
		pending.put(key, Boolean.TRUE);

		// Fetch in background
		Job fetchJob = new Job("Fetching avatar") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					ImageData data = downloadImage(avatarUrl);
					if (data == null) {
						return Status.OK_STATUS;
					}
					// Scale to desired size
					ImageData scaled = data.scaledTo(
							size, size);
					Display display = Display.getDefault();
					if (display.isDisposed()) {
						return Status.OK_STATUS;
					}
					display.asyncExec(() -> {
						if (display.isDisposed()) {
							return;
						}
						Image img = new Image(
								display, scaled);
						cache.put(key, img);
						pending.remove(key);
						if (onLoaded != null) {
							onLoaded.run();
						}
					});
				} catch (Exception e) {
					Activator.logWarning(
							"Failed to fetch avatar: " //$NON-NLS-1$
									+ avatarUrl);
					pending.remove(key);
				}
				return Status.OK_STATUS;
			}
		};
		fetchJob.setSystem(true);
		fetchJob.schedule();
		return null;
	}

	/**
	 * Downloads an image from the given URL.
	 *
	 * @param urlStr
	 *            the image URL
	 * @return the image data, or {@code null} on failure
	 */
	private ImageData downloadImage(String urlStr) {
		HttpURLConnection conn = null;
		try {
			URL url = new URL(urlStr);
			conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(10000);
			conn.setReadTimeout(10000);
			conn.setRequestProperty("Accept", //$NON-NLS-1$
					"image/*"); //$NON-NLS-1$

			int code = conn.getResponseCode();
			if (code != 200) {
				return null;
			}
			try (InputStream is = conn.getInputStream()) {
				return new ImageData(is);
			}
		} catch (Exception e) {
			return null;
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	/**
	 * Disposes all cached images. Call from
	 * {@code Activator.stop()}.
	 */
	public void dispose() {
		for (Image img : cache.values()) {
			if (img != null && !img.isDisposed()) {
				img.dispose();
			}
		}
		cache.clear();
		pending.clear();
	}
}
```

---

## Step 6: Register disposal in `Activator`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/Activator.java`

In the `stop(BundleContext)` method, add before `super.stop(context)`:

```java
AvatarCache.getDefault().dispose();
```

Add the import:

```java
import org.eclipse.egit.pullrequest.internal.ui.AvatarCache;
```

---

## Step 7: Update `ExpandedCommentComposite.createAvatarCanvas()`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/ExpandedCommentComposite.java`

Modify `createAvatarCanvas()` at line 471 to accept an avatar URL and
attempt to load a real image:

```java
private Canvas createAvatarCanvas(Composite parent,
		String authorName, String avatarUrl) {
	Canvas canvas = new Canvas(parent, SWT.DOUBLE_BUFFERED);
	canvas.setBackground(parent.getBackground());

	String initials = getInitials(authorName);

	// Try loading real avatar
	Image[] avatarHolder = new Image[1];
	if (avatarUrl != null && !avatarUrl.isEmpty()) {
		avatarHolder[0] = AvatarCache.getDefault()
				.getAvatar(avatarUrl, AVATAR_SIZE, () -> {
					if (!canvas.isDisposed()) {
						avatarHolder[0] = AvatarCache
								.getDefault().getAvatar(
										avatarUrl, AVATAR_SIZE,
										null);
						canvas.redraw();
					}
				});
	}

	canvas.addPaintListener(e -> {
		GC gc = e.gc;
		gc.setAntialias(SWT.ON);

		Image avatar = avatarHolder[0];
		if (avatar != null && !avatar.isDisposed()) {
			// Draw circular clipped image
			gc.setClipping(0, 0, AVATAR_SIZE, AVATAR_SIZE);
			gc.drawImage(avatar, 0, 0);
			gc.setClipping((org.eclipse.swt.graphics.Region) null);
		} else {
			// Fallback: initials circle
			gc.setBackground(avatarBgColor);
			gc.fillOval(0, 0,
					AVATAR_SIZE - 1, AVATAR_SIZE - 1);
			gc.setForeground(getDisplay()
					.getSystemColor(SWT.COLOR_WHITE));
			Font avatarFont = smallFont;
			gc.setFont(avatarFont);
			org.eclipse.swt.graphics.Point ext =
					gc.textExtent(initials);
			int x = (AVATAR_SIZE - ext.x) / 2;
			int y = (AVATAR_SIZE - ext.y) / 2;
			gc.drawText(initials, x, y, true);
		}
	});

	return canvas;
}
```

### 7a. Update callers of `createAvatarCanvas()`

In `renderComment()` at line 331, change:

```java
// Before:
Canvas avatarCanvas = createAvatarCanvas(headerRow, author);

// After:
Canvas avatarCanvas = createAvatarCanvas(
		headerRow, author,
		comment.getAuthorAvatarUrl());
```

In `renderReply()`, the call passes through `renderComment()` so it
is automatically handled.

---

## Step 8: Update `PullRequestOverviewView.createAvatarCircle()`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestOverviewView.java`

In `createAvatarCircle()` at line 759, add avatar image loading:

```java
private void createAvatarCircle(Composite parent,
		PullRequest.PullRequestParticipant reviewer) {
	// ... existing container setup ...

	// Avatar canvas
	Canvas canvas = new Canvas(avatarContainer, SWT.NONE);
	int avatarSize = 40;
	GridDataFactory.fillDefaults()
			.hint(avatarSize, avatarSize)
			.applyTo(canvas);

	String displayName = reviewer.getUser().getDisplayName();
	if (displayName == null) {
		displayName = reviewer.getUser().getName();
	}
	String initials = getInitials(displayName);
	Color bgColor = getAvatarColor(displayName);
	boolean approved = reviewer.isApproved();

	// Try loading real avatar image
	String avatarUrl = reviewer.getUser().getAvatarUrl();
	Image[] avatarHolder = new Image[1];
	if (avatarUrl != null && !avatarUrl.isEmpty()) {
		avatarHolder[0] = AvatarCache.getDefault()
				.getAvatar(avatarUrl, avatarSize, () -> {
					if (!canvas.isDisposed()) {
						avatarHolder[0] = AvatarCache
								.getDefault().getAvatar(
										avatarUrl, avatarSize,
										null);
						canvas.redraw();
					}
				});
	}

	canvas.addPaintListener((PaintEvent e) -> {
		Image avatar = avatarHolder[0];
		if (avatar != null && !avatar.isDisposed()) {
			e.gc.setAntialias(SWT.ON);
			e.gc.drawImage(avatar, 0, 0);
			// Draw approved indicator
			if (approved) {
				e.gc.setForeground(Display.getCurrent()
						.getSystemColor(
								SWT.COLOR_DARK_GREEN));
				e.gc.setLineWidth(3);
				e.gc.drawOval(1, 1,
						avatarSize - 2, avatarSize - 2);
			}
		} else {
			paintAvatar(e, initials, bgColor,
					approved, avatarSize);
		}
	});

	// ... rest of method (name label) ...
}
```

---

## Step 9: Add tests

**File**: `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/github/GitHubJsonParserTest.java`

```java
@Test
public void testParseUserAvatarUrl() {
	String json = "{\"user\":{\"login\":\"octocat\","
			+ "\"avatar_url\":\"https://avatars.github.com/u/1\"}}";
	// Extract from nested user object
	int userIdx = json.indexOf("\"user\"");
	int braceStart = json.indexOf('{', userIdx);
	int braceEnd = GitHubJsonParser.findMatchingBrace(
			json, braceStart);
	String userObj = json.substring(
			braceStart, braceEnd + 1);
	String avatarUrl = GitHubJsonParser
			.extractStringValue(userObj, "avatar_url");
	assertThat(avatarUrl,
			equalTo("https://avatars.github.com/u/1"));
}
```

**File**: `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketClientTest.java`

```java
@Test
public void testParseUserAvatarUrl() {
	String json = "{\"name\":\"jdoe\","
			+ "\"displayName\":\"John Doe\","
			+ "\"avatarUrl\":\"/users/jdoe/avatar.png\"}";
	String avatarUrl = BitbucketJsonParser
			.extractStringValue(json, "avatarUrl");
	assertThat(avatarUrl,
			equalTo("/users/jdoe/avatar.png"));
}
```

---

## Step 10: Handle Bitbucket relative avatar URLs

In `AvatarCache.downloadImage()`, or alternatively in
`BitbucketClient` when populating the model, prepend the server URL
to relative paths:

In `BitbucketJsonParser` where avatar URL is set:

```java
String avatarUrl = extractStringValue(
		userObj, "avatarUrl"); //$NON-NLS-1$
// avatarUrl may be relative; caller should resolve
// against server URL if needed
user.setAvatarUrl(avatarUrl);
```

In `BitbucketClient`, add a helper after parsing:

```java
/**
 * Resolves a potentially relative avatar URL against the
 * server URL.
 */
private String resolveAvatarUrl(String avatarUrl) {
	if (avatarUrl == null) {
		return null;
	}
	if (avatarUrl.startsWith("http://") //$NON-NLS-1$
			|| avatarUrl.startsWith("https://")) { //$NON-NLS-1$
		return avatarUrl;
	}
	return serverUrl + avatarUrl;
}
```

Call `resolveAvatarUrl()` after parsing each user/comment to fix up
the URL before returning the model objects.

---

## Verification

1. `mvn clean verify -DskipTests` — build succeeds
2. `cd org.eclipse.egit.pullrequest.test && mvn test` — all tests pass
3. Manual: Open a PR with comments from users who have avatars, verify
   real images appear in inline comments and overview reviewer circles.
   Verify initials fallback for users without avatars.

# Plan: Show actual user avatars in inline comment display (#7)

**Issue**: https://github.com/Philipp0205/eclipse-pull-request-plugin/issues/7
**Labels**: enhancement
**Complexity**: Medium

## Overview

Inline comments currently display colored circles with initials (drawn via
`ExpandedCommentComposite.createAvatarCanvas()`). This plan replaces those
with real avatar images fetched from GitHub's `avatar_url` and Bitbucket DC's
avatar link, with an async download + disk cache so the UI never blocks.

## Current State

| Component | What exists today |
|-----------|------------------|
| `PullRequestComment` model | `authorName`, `authorDisplayName`, `authorEmail` — **no avatar URL** |
| `PullRequest.User` model | `name`, `emailAddress`, `displayName` — **no avatar URL** |
| `GitHubJsonParser.parseComment()` | Parses `user.login` but **ignores `avatar_url`** |
| `BitbucketJsonParser.parseComment()` | Parses `author.{name,displayName,emailAddress}` — **no avatar link** |
| `ExpandedCommentComposite` | `createAvatarCanvas()` draws initials in a blue circle (28×28) |
| Image caching | None exists for remote images |

## Technical Approach

### Layer 1 — Model: add `authorAvatarUrl` field
Add a single `String authorAvatarUrl` field with getter/setter to
`PullRequestComment`. This keeps the model provider-agnostic (both GitHub
and Bitbucket supply an HTTPS URL).

No change needed to `PullRequest.User` for this issue — comments are the
scope.

### Layer 2 — JSON Parsing: extract avatar URLs

**GitHub** (`GitHubJsonParser.parseComment()` at line 511):
The `"user"` object already contains `"avatar_url"`. Extract it and call
`comment.setAuthorAvatarUrl(...)`.

**Bitbucket DC** (`BitbucketJsonParser.parseComment()` at line 402):
Bitbucket DC user objects contain an `"avatarUrl"` field at the top level
of the author object (e.g., `"author": { "name": "...", "avatarUrl": "..." }`).
Alternatively, if the Bitbucket DC version provides avatars via a `"links"`
object with an `"avatar"` array, extract from
`author.links.avatar[0].href`. Parse whichever is present.

### Layer 3 — Avatar Cache Service (new class)

Create `AvatarCache` in `internal.ui` package — a singleton managing:

1. **In-memory LRU map**: `Map<String, Image>` (URL → SWT `Image`),
   bounded (e.g., 128 entries). On eviction, dispose the SWT `Image`.
2. **Disk cache**: Store downloaded images under the Eclipse state location
   (`Activator.getDefault().getStateLocation()` →
   `.metadata/.plugins/org.eclipse.egit.pullrequest/avatars/`).
   File name = SHA-256 hex of URL + `.png`.
3. **Async loading**: Use Eclipse `Job` API to download + write to disk +
   create `Image`, then `Display.asyncExec()` to notify the caller via a
   callback so the widget can redraw.
4. **Proxy support**: Use `java.net.HttpURLConnection` which respects
   Eclipse proxy settings (via `IProxyService` or JVM system properties).
5. **Size normalization**: Downloaded images are scaled to `AVATAR_SIZE`
   (28×28) accounting for DPI via `DPIUtil` or the SWT zoom factor.
6. **Error handling**: On download failure, return `null` so the UI falls
   back to initials. Cache failures for a short TTL so we don't re-fetch
   broken URLs repeatedly.
7. **Lifecycle**: Dispose all cached `Image` objects in `Activator.stop()`
   or when the cache is explicitly cleared.

### Layer 4 — UI: swap initials for real images

Modify `ExpandedCommentComposite.createAvatarCanvas()`:

1. Accept the `authorAvatarUrl` as a parameter (extracted from comment).
2. Request the image from `AvatarCache.getAvatar(url, size, callback)`.
3. If the image is available synchronously (cache hit), paint it
   immediately in the `PaintListener`.
4. If not (cache miss), draw the initials fallback; when the async
   callback fires, store the `Image` and call `canvas.redraw()`.
5. The `PaintListener` checks: if `avatarImage != null`, draw it clipped
   to a circle (using `gc.setClipping(Region)` with an oval path, or
   painting the image and overlaying a mask); otherwise draw initials.

Circular clipping approach:
```java
gc.setClipping(new Region(display));
// ... add oval to region
gc.drawImage(scaledImage, 0, 0);
```
Or simpler: draw image then paint corner masks to simulate a circle.

### Layer 5 — Fallback behavior

The initials fallback already works today. The only change is to make
`createAvatarCanvas()` try the real image first, and if it's `null`
(no URL, failed download, cache miss still loading), fall through to
the existing initials drawing code.

## Task Breakdown

### Task 1: Add `authorAvatarUrl` to `PullRequestComment` model
**File**: `PullRequestComment.java`
- Add `private String authorAvatarUrl;`
- Add `getAuthorAvatarUrl()` / `setAuthorAvatarUrl(String)` with Javadoc

### Task 2: Parse `avatar_url` in GitHub JSON parser
**File**: `GitHubJsonParser.java` (around line 512)
- After extracting `login`, also extract:
  ```java
  comment.setAuthorAvatarUrl(
      extractString(userJson, "avatar_url"));
  ```

### Task 3: Parse avatar URL in Bitbucket JSON parser
**File**: `BitbucketJsonParser.java` (around line 403)
- After extracting author fields, extract:
  ```java
  String avatarUrl = extractStringValue(
      authorJson, "\"avatarUrl\":");
  if (avatarUrl != null) {
      comment.setAuthorAvatarUrl(avatarUrl);
  }
  ```
- If Bitbucket DC provides avatars via `links.avatar[0].href` instead,
  parse that nested structure.

### Task 4: Create `AvatarCache` class
**File**: new `internal/ui/AvatarCache.java`
- Singleton with `getInstance()` method
- `Image getAvatar(String url, int size, Runnable onLoaded)`:
  - Returns cached `Image` immediately if available
  - Returns `null` and schedules async download if not
  - Calls `onLoaded` (on UI thread) when image is ready
- `void dispose()`: disposes all cached SWT `Image` objects
- Disk cache directory under plugin state location
- Download via `HttpURLConnection`, respecting Eclipse proxy
- Scale images to requested size, handling DPI

### Task 5: Modify `ExpandedCommentComposite` to use real avatars
**File**: `ExpandedCommentComposite.java`
- Change `createAvatarCanvas(Composite parent, String authorName)` to
  `createAvatarCanvas(Composite parent, String authorName, String avatarUrl)`
- Update both call sites (root comment at line 331 and replies via
  `renderComment`/`renderReply`)
- In the `PaintListener`:
  1. Try `AvatarCache.getInstance().getAvatar(url, AVATAR_SIZE, canvas::redraw)`
  2. If non-null, draw circular-clipped image
  3. If null, fall back to existing initials code
- Ensure loaded images are properly disposed when the composite is disposed

### Task 6: Hook up `AvatarCache.dispose()` in `Activator.stop()`
**File**: `Activator.java`
- Call `AvatarCache.getInstance().dispose()` in `stop()` before
  `super.stop()`

### Task 7: Add unit tests
**File**: `GitHubJsonParserTest.java`
- Add test `testParseCommentWithAvatarUrl`: verify `avatar_url` is
  extracted from user object
- Update existing test JSON strings to include `avatar_url` field and
  verify it's parsed

**File**: `BitbucketClientTest.java`
- Add test for Bitbucket comment avatar URL parsing

**File**: new `AvatarCacheTest.java` (optional, limited without SWT display)
- Test disk cache file naming (SHA-256 of URL)
- Test fallback behavior (null URL returns null)

### Task 8: Build verification
- Run `mvn clean verify` from project root
- Ensure no test failures
- Verify 80-char line limit, tab indentation, EPL-2.0 headers

## Dependencies

- No new external library dependencies needed
- Uses existing `java.net.HttpURLConnection` for downloads
- Uses existing SWT `Image`/`GC` APIs for rendering
- Uses existing Eclipse `Job` API for async work

## Potential Challenges

| Risk | Mitigation |
|------|------------|
| **Bitbucket DC avatar URL format varies by version** | Parse both `avatarUrl` field and `links.avatar[0].href`; log warning if neither found |
| **High-DPI scaling** | Use `DPIUtil.autoScaleDown/Up` or compute zoom from `Display.getDPI()` |
| **SWT Image disposal** | Centralize in `AvatarCache.dispose()`; use weak references if needed |
| **Circular clipping on all platforms** | Test on macOS, Windows, Linux; fallback to square if clipping unsupported |
| **Proxy authentication** | `HttpURLConnection` automatically uses Eclipse proxy settings; test behind proxy |
| **Thread safety** | `AvatarCache` methods synchronized; UI updates via `Display.asyncExec()` |
| **Rate limiting** | GitHub avatars served from `avatars.githubusercontent.com` (no API rate limit); Bitbucket DC is self-hosted (no concern) |

## Testing Strategy

1. **Unit tests**: Parse avatar URLs from both GitHub and Bitbucket JSON
2. **Manual testing**:
   - Open a GitHub PR with comments → avatars appear
   - Open a Bitbucket PR with comments → avatars appear
   - Disconnect network → fallback to initials
   - Restart Eclipse → avatars loaded from disk cache
   - High-DPI display → avatars crisp at correct size

## Files Modified (Summary)

| File | Change |
|------|--------|
| `PullRequestComment.java` | Add `authorAvatarUrl` field + accessors |
| `GitHubJsonParser.java` | Extract `avatar_url` from user object |
| `BitbucketJsonParser.java` | Extract avatar URL from author object |
| `AvatarCache.java` | **New file** — async image download + caching |
| `ExpandedCommentComposite.java` | Use real images in `createAvatarCanvas()` |
| `Activator.java` | Dispose `AvatarCache` on stop |
| `GitHubJsonParserTest.java` | Test avatar URL parsing |
| `BitbucketClientTest.java` | Test avatar URL parsing |

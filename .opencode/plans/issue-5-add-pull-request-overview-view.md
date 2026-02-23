# Plan: Add Pull Request Overview View using embedded Browser widget

**Issue**: [#5](https://github.com/Philipp0205/eclipse-pull-request-plugin/issues/5)
**Labels**: enhancement
**Complexity**: Medium-Large

## Overview

Add a `PullRequestOverviewView` that renders a rich HTML overview of a selected
pull request using an SWT `Browser` widget. This fills the gap between selecting
a PR in the list and viewing changed files -- the user currently has no way to
see the PR description, branch info, author, labels, draft status, or metadata
at a glance.

## Technical Approach

### Architecture

The new view follows the existing pattern: `PullRequestListView` calls
`loadPullRequest(pr)` on the overview view directly (same pattern used for
`PullRequestChangedFilesView` at `PullRequestListView.java:513-527`).

The view embeds `org.eclipse.swt.browser.Browser` and renders a self-contained
HTML page by injecting PR data into an HTML/CSS template. No external
dependencies are needed -- SWT Browser is already available on all platforms.

### Browser-to-Java Communication: `BrowserFunction`

Use `org.eclipse.swt.browser.BrowserFunction` to expose Java methods callable
from JavaScript inside the browser. This pattern is chosen over
`LocationListener` because:

- It supports passing structured data (comment text, file paths, line numbers)
- It can return results to JavaScript (success/error status)
- It scales cleanly for future interactive features (adding comments, approving
  PRs, requesting changes) without refactoring
- Multiple independent actions coexist as separate function calls

The view registers a `BrowserFunction` named `eclipseAction` that dispatches
based on the first argument:

```java
new BrowserFunction(browser, "eclipseAction") {
    @Override
    public Object function(Object[] arguments) {
        String action = (String) arguments[0];
        switch (action) {
        case "openInBrowser":
            openInExternalBrowser();
            break;
        case "viewChangedFiles":
            activateChangedFilesView();
            break;
        // Future: "addComment", "approve", etc.
        }
        return null;
    }
};
```

HTML buttons/links invoke it via JavaScript:
```html
<a href="#" onclick="eclipseAction('openInBrowser'); return false;">
    Open in Browser
</a>
```

### HTML Rendering Strategy

- Store the HTML/CSS template as a Java string constant in a dedicated
  `PullRequestOverviewHtmlRenderer` utility class
- Escape all PR data before injection to prevent rendering issues
- Support light theme; dark theme detection via
  `Display.getDefault().getSystemColor()` as a future enhancement
- Render description as plain text in `<pre>` initially (markdown-to-HTML
  can be added later as an enhancement)

### Navigation Flow (Updated)

When a PR is double-clicked in the list:
1. `PullRequestOverviewView` updates with the PR's HTML overview (NEW)
2. `PullRequestChangedFilesView` loads changed files (existing)
3. User can click "View Changed Files" button in overview to activate that view
4. User can click "Open in Browser" to open the PR URL externally

## Task Breakdown

### Task 1: Create `PullRequestOverviewHtmlRenderer` utility class

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestOverviewHtmlRenderer.java`

- Static utility class with `renderHtml(PullRequest pr)` method
- HTML template with CSS inline styles for:
  - Header: PR title (h1), state badge (color-coded), draft badge, PR ID
  - Metadata table: author, source/target branches (monospace badges with
    arrow), labels (colored pills), created/updated dates, comment count
  - Description section: escaped text in `<pre>` block (or "No description"
    placeholder)
  - Action buttons calling `eclipseAction(...)` via `onclick` handlers
- `escapeHtml(String)` private helper method for safe data injection
- `renderPlaceholderHtml()` static method for the empty/no-PR-selected state
- CSS provides clean, readable styling with:
  - System font stack, reasonable spacing
  - Color-coded state badges (green=OPEN, purple=MERGED, red=DECLINED/CLOSED)
  - Monospace branch name badges with background
  - Label pills
- Light theme styling as baseline

### Task 2: Create `PullRequestOverviewView` ViewPart class

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestOverviewView.java`

- Extends `ViewPart`
- `public static final String VIEW_ID = "org.eclipse.egit.pullrequest.PullRequestOverviewView"`
- `createPartControl(Composite)`:
  - Create `Browser` widget filling the parent (catch `SWTError` and show
    fallback `Label` if browser creation fails)
  - Register `BrowserFunction` named `"eclipseAction"` with dispatch logic:
    - `"openInBrowser"` -> open PR URL via
      `PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser()`
    - `"viewChangedFiles"` -> find and activate
      `PullRequestChangedFilesView` via `page.showView()`
  - Add a `LocationListener` that blocks all external navigation (prevents
    the embedded browser from navigating away from the rendered HTML)
  - Show placeholder HTML: "No pull request selected"
- `loadPullRequest(PullRequest pr)`:
  - Store current PR reference
  - Call `PullRequestOverviewHtmlRenderer.renderHtml(pr)`
  - Set HTML on browser widget via `browser.setText(html)`
  - Update view title/tooltip with PR name
- `setFocus()`: set focus to browser widget
- `dispose()`: dispose `BrowserFunction`, clean up browser

### Task 3: Register view in `plugin.xml`

**File**: `org.eclipse.egit.pullrequest/plugin.xml`

Add new view entry under the existing `PullRequestCategory`:
```xml
<view
      allowMultiple="false"
      category="org.eclipse.egit.pullrequest.PullRequestCategory"
      class="org.eclipse.egit.pullrequest.internal.ui.PullRequestOverviewView"
      icon="icons/obj16/gitrepository.png"
      id="org.eclipse.egit.pullrequest.PullRequestOverviewView"
      name="%PullRequestOverviewView">
</view>
```

### Task 4: Add externalized strings

**File**: `plugin.properties` -- add:
```
PullRequestOverviewView = Pull Request Overview
```

**File**: `PRText.java` -- add NLS string fields for user-facing strings in
the HTML template and view (e.g., placeholder message, section headings,
action link labels).

**File**: `prtext.properties` -- add corresponding property entries.

Note: Since the HTML template uses hardcoded English strings rendered in a
browser, NLS externalization for the HTML content is a pragmatic tradeoff.
The Eclipse view name and any SWT-rendered text (like error messages) MUST
be externalized. HTML template strings can use `PRText` fields injected at
render time for full i18n support.

### Task 5: Update `PullRequestListView` to notify overview view

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestListView.java`

Modify `loadPullRequest(PullRequest pr)` (line 513) to also update the
overview view:

```java
private void loadPullRequest(PullRequest pr) {
    try {
        IWorkbenchPage page = getSite().getWorkbenchWindow()
                .getActivePage();

        // Update overview view
        IWorkbenchPart overviewPart = page
                .showView(PullRequestOverviewView.VIEW_ID);
        if (overviewPart instanceof PullRequestOverviewView) {
            ((PullRequestOverviewView) overviewPart)
                    .loadPullRequest(pr);
        }

        // Update changed files view (existing)
        IWorkbenchPart part = page
                .showView(PullRequestChangedFilesView.VIEW_ID);
        if (part instanceof PullRequestChangedFilesView) {
            ((PullRequestChangedFilesView) part)
                    .loadPullRequest(pr);
        }
    } catch (PartInitException e) {
        Activator.logError(
                "Failed to open pull request views", e); //$NON-NLS-1$
    }
}
```

### Task 6: Update perspective layout

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestPerspectiveFactory.java`

Add the Overview view as a tab alongside Comments in the bottom folder:

```java
IFolderLayout bottom = layout.createFolder(
        "bottom", //$NON-NLS-1$
        IPageLayout.BOTTOM,
        0.7f,
        editorArea);
bottom.addView(PullRequestOverviewView.VIEW_ID);  // NEW - first tab
bottom.addView(PullRequestCommentsView.VIEW_ID);
bottom.addView(IPageLayout.ID_PROP_SHEET);
```

Add show-view shortcut:
```java
layout.addShowViewShortcut(PullRequestOverviewView.VIEW_ID);
```

### Task 7: Ensure code quality compliance

- EPL-2.0 license headers on all new files
- Tab indentation, 80-char line width
- Javadoc on all public/protected members
- No wildcard imports
- No `System.out.println` -- use `Activator.logError()` etc.
- `$NON-NLS-1$` on all non-translatable string literals
- HTML-escape all PR data before injection

### Task 8: Build and verify

- Run `mvn clean verify` from project root
- Ensure no compilation errors
- Ensure existing tests pass

## Files Modified

| File | Action | Description |
|------|--------|-------------|
| `internal/ui/PullRequestOverviewHtmlRenderer.java` | **CREATE** | HTML rendering utility |
| `internal/ui/PullRequestOverviewView.java` | **CREATE** | New ViewPart with BrowserFunction |
| `plugin.xml` | MODIFY | Register new view |
| `plugin.properties` | MODIFY | Add view display name |
| `internal/PRText.java` | MODIFY | Add NLS string fields |
| `internal/prtext.properties` | MODIFY | Add NLS string values |
| `internal/ui/PullRequestListView.java` | MODIFY | Notify overview view on PR load |
| `internal/ui/PullRequestPerspectiveFactory.java` | MODIFY | Add overview to bottom folder |

## Dependencies

- No new OSGi dependencies needed (`org.eclipse.swt.browser.Browser` and
  `org.eclipse.swt.browser.BrowserFunction` are part of the SWT bundle)
- No MANIFEST.MF changes required
- All model fields already exist in `PullRequest.java`

## Testing Strategy

- **Manual testing**: Open the perspective, load a PR, verify the overview
  renders with correct data, click action buttons
- **Build verification**: `mvn clean verify` passes
- **Unit tests**: Consider adding a test for `PullRequestOverviewHtmlRenderer`
  to verify HTML output contains expected elements and properly escapes
  special characters (e.g., `<script>` in PR titles)

## Potential Challenges

1. **SWT Browser availability**: On some Linux configurations, WebKit may not
   be available. The view should catch `SWTError` during browser creation and
   show a fallback message.
2. **HTML escaping**: Must be thorough -- PR titles and descriptions can
   contain any characters including `<`, `>`, `&`, quotes.
3. **Dark theme**: Not in scope for initial implementation, but the HTML/CSS
   should be structured to make theme switching easy to add later.
4. **Long descriptions**: The browser widget handles scrolling natively, so
   long PR descriptions should work without extra effort.
5. **BrowserFunction threading**: `BrowserFunction.function()` is called on
   the SWT UI thread. Any long-running operations (future API calls like
   posting comments) must be offloaded to Eclipse `Job` instances with
   `Display.asyncExec()` callbacks to update the browser on completion.

## Future Extensibility via BrowserFunction

The `BrowserFunction` pattern established here directly supports planned
future features without refactoring:

| Future Action | JS Call | Java Handler |
|---------------|---------|--------------|
| Add PR comment | `eclipseAction('addComment', text)` | `IPullRequestClient.addComment()` in a Job |
| Approve PR | `eclipseAction('approve')` | `IPullRequestClient.approve()` in a Job |
| Request changes | `eclipseAction('requestChanges', text)` | API call in a Job |
| Merge PR | `eclipseAction('merge', method)` | API call in a Job |

Each new action is just a new `case` in the dispatch switch + a JavaScript
button in the HTML template. The communication plumbing is already in place.

## Open Questions

None -- the issue specification is comprehensive and the codebase patterns are
clear. Ready to implement.

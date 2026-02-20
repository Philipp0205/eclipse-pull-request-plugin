# GitHub Comment Thread Resolution Fix

## Problem
The error `Unable to find review thread for comment XXXXX` occurred when trying to resolve/unresolve GitHub review comments because:

1. The `node_id` field from GitHub's REST API was not being extracted
2. The GraphQL query was fetching the wrong object type (`pullRequestReview` instead of `pullRequestReviewThread`)

## Changes Made

### 1. GitHubJsonParser.java
- **Location**: Line ~527
- **Change**: Added extraction of `node_id` field for review comments and storing it in `PullRequestComment.threadId`
- **Code**:
```java
if (isReviewComment) {
    // Extract node_id for GraphQL thread resolution
    String nodeId = extractString(json, "node_id");
    Activator.logInfo("parseComment: extracted node_id=" + nodeId
            + " for comment id=" + comment.getId());
    if (nodeId != null && !nodeId.isEmpty()) {
        comment.setThreadId(nodeId);
    }
    ...
}
```

### 2. GitHubClient.java - Import
- **Location**: Line ~31
- **Change**: Added import for `org.eclipse.egit.pullrequest.Activator` to enable logging

### 3. GitHubClient.java - updateCommentState()
- **Location**: Line ~287-294
- **Change**: Added debug logging to trace the issue
- **Code**:
```java
String commentThreadId = comment.getThreadId();
Activator.logInfo("updateCommentState: commentId=" + commentId
        + ", threadId(node_id)=" + commentThreadId);

String threadId = getThreadIdFromComment(commentThreadId);
Activator.logInfo("updateCommentState: resolved threadId=" + threadId);
```

### 4. GitHubClient.java - getThreadIdFromComment()
- **Location**: Line ~337
- **Change**: Fixed GraphQL query to fetch `pullRequestReviewThread.id` instead of `pullRequestReview.id`
- **Change**: Added extensive debug logging
- **Code**:
```java
String query = "query { node(id: \"" + commentNodeId
        + "\") { ... on PullRequestReviewComment { pullRequestReviewThread { id } } } }";
```

## How to Test

### Step 1: Restart Eclipse
The changes won't take effect until Eclipse reloads the plugin. **You must restart Eclipse** or use "Run as > Eclipse Application" to test the changes in a new Eclipse instance.

### Step 2: Try to Resolve a Comment
1. Open a pull request with review comments
2. Try to resolve/unresolve a comment
3. Check the Eclipse log file (`.metadata/.log` in your workspace) for the debug messages:
   - `parseComment: extracted node_id=...`
   - `updateCommentState: commentId=... threadId(node_id)=...`
   - `getThreadIdFromComment: GraphQL result=...`
   - `getThreadIdFromComment: extracted threadId=...`

### Step 3: Verify the Fix
If the fix is working:
- No IOException should occur
- The debug log should show:
  - `node_id` being extracted (starts with `MDI6...` or similar GitHub node ID)
  - GraphQL query returning the thread ID
  - Thread being successfully resolved/unresolved

If still failing, check the debug logs to see where it's failing:
- If `node_id` is null → JSON parsing issue
- If GraphQL result doesn't contain `pullRequestReviewThread` → API issue or wrong query
- If thread ID extraction fails → parsing issue

## Troubleshooting

### The error still occurs with line 289
This means Eclipse hasn't reloaded the code. After my changes, line 289 is now:
```java
+ ", threadId(node_id)=" + commentThreadId);
```

If the error still shows line 289 with the IOException, the old code is still running.

**Solution**: Restart Eclipse completely.

### Debug logs don't appear
Make sure the log level is set to INFO or higher. Check `.metadata/.log` in your workspace directory.

### GraphQL query returns empty/null
This could mean:
1. The comment is not a review comment (issue comment) - these don't have threads
2. The node_id is invalid or in wrong format
3. GitHub API permissions issue

## Next Steps

Once tested and verified working:
1. Remove or comment out the debug logging statements
2. Test with various comment types (root comments, replies, outdated comments)
3. Verify it works for both resolve and unresolve operations

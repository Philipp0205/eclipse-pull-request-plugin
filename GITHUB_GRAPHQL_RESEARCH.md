# GitHub GraphQL Schema Notes for Review Comments

## PullRequestReviewComment Fields

According to GitHub's GraphQL API v4 documentation, the `PullRequestReviewComment` type has these relevant fields:

- `id`: ID! (GraphQL global node ID)
- `databaseId`: Int (the numeric ID from REST API)
- `pullRequestReview`: PullRequestReview (the review this comment belongs to)

**IMPORTANT**: There is NO `pullRequestReviewThread` field on `PullRequestReviewComment`!

## The Problem

In GitHub's GraphQL schema:
- A `PullRequestReviewComment` belongs to a `PullRequestReview`
- A `PullRequestReview` contains multiple comments
- The concept of "threads" is handled differently

## The Solution

To resolve/unresolve a thread in GitHub, we need to use the **review thread ID**, which is accessed differently:

### Option 1: Use the comment's position to find the thread
GitHub's `resolveReviewThread` mutation requires a thread ID, but threads are not directly accessible from a single comment node.

### Option 2: Query the Pull Request for all threads
We may need to query the pull request itself to find threads.

### Option 3: Use the PullRequestReview
The `PullRequestReview` object might have thread information.

## Testing Queries

### Query 1: Get PullRequestReview from comment
```graphql
query {
  node(id: "PRRC_kwDORUuGFs6o3N0d") {
    ... on PullRequestReviewComment {
      id
      databaseId
      pullRequestReview {
        id
      }
    }
  }
}
```

### Query 2: Check if there's a different field
```graphql
query {
  node(id: "PRRC_kwDORUuGFs6o3N0d") {
    ... on PullRequestReviewComment {
      id
      databaseId
      replyTo {
        id
      }
      pullRequest {
        id
        reviewThreads(first: 100) {
          nodes {
            id
            isResolved
            comments(first: 1) {
              nodes {
                databaseId
              }
            }
          }
        }
      }
    }
  }
}
```

## Alternative Approach

Instead of querying for the thread ID from the comment's node_id, we might need to:

1. Store the thread ID when fetching comments (if GitHub provides it in the REST API)
2. OR query the pull request's review threads and match by comment ID
3. OR use the REST API endpoint if it exists for resolving threads

## REST API Investigation Needed

Check if GitHub has a REST API endpoint for:
- Resolving/unresolving review threads
- Getting thread information from a comment


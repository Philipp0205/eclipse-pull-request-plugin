#!/bin/bash
# Debug script to test GraphQL query for comment resolution status
# Replace these values with your actual data
GITHUB_TOKEN="YOUR_TOKEN_HERE"
OWNER="Philipp0205"
REPO="test-repo"
PR_NUMBER=1

echo "Testing GraphQL query for PR #${PR_NUMBER} in ${OWNER}/${REPO}"
echo "=================================================="
echo ""

# The exact query your code uses
QUERY='query { repository(owner: \"'${OWNER}'\", name: \"'${REPO}'\") { pullRequest(number: '${PR_NUMBER}') { reviewThreads(first: 100) { nodes { id isResolved comments(first: 100) { nodes { databaseId } } } } } } }'

echo "GraphQL Query:"
echo "$QUERY"
echo ""
echo "=================================================="
echo "Sending request to GitHub..."
echo ""

# Execute the GraphQL query
curl -X POST https://api.github.com/graphql \
  -H "Authorization: Bearer ${GITHUB_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"query\":\"${QUERY}\"}" \
  2>&1 | tee graphql_response.json

echo ""
echo "=================================================="
echo "Response saved to: graphql_response.json"
echo ""
echo "Analyzing response..."
echo ""

# Check if response contains errors
if grep -q '"errors"' graphql_response.json; then
    echo "❌ ERROR: GraphQL query returned errors!"
    cat graphql_response.json | grep -A 10 '"errors"'
else
    echo "✓ No errors in response"
fi

# Check if response contains data
if grep -q '"reviewThreads"' graphql_response.json; then
    echo "✓ Response contains reviewThreads data"
    
    # Count threads
    THREAD_COUNT=$(grep -o '"isResolved"' graphql_response.json | wc -l)
    echo "✓ Found ${THREAD_COUNT} review threads"
    
    # Count resolved threads
    RESOLVED_COUNT=$(grep -o '"isResolved":true' graphql_response.json | wc -l)
    echo "✓ Found ${RESOLVED_COUNT} resolved threads"
    
    # Count unresolved threads
    UNRESOLVED_COUNT=$(grep -o '"isResolved":false' graphql_response.json | wc -l)
    echo "✓ Found ${UNRESOLVED_COUNT} unresolved threads"
    
    # Extract comment IDs
    echo ""
    echo "Comment IDs found in GraphQL response:"
    grep -o '"databaseId":[0-9]*' graphql_response.json | cut -d: -f2 | sort -u
else
    echo "❌ ERROR: Response does not contain reviewThreads data!"
    echo "Full response:"
    cat graphql_response.json
fi

echo ""
echo "=================================================="
echo "Compare these comment IDs with your REST API response:"
echo "REST API comment IDs: 2833046813, 2833046835, 2833070054"

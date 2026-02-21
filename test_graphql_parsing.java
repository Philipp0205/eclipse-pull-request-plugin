import java.util.HashMap;
import java.util.Map;

public class test_graphql_parsing {
    
    // Simulated GraphQL response - what GitHub actually returns
    private static final String SAMPLE_RESPONSE_1 = 
        "{\"data\":{\"repository\":{\"pullRequest\":{\"reviewThreads\":{\"nodes\":[" +
        "{\"id\":\"PRRT_kwDOABC123\",\"isResolved\":true,\"comments\":{\"nodes\":[{\"databaseId\":2833046813}]}}," +
        "{\"id\":\"PRRT_kwDOABC456\",\"isResolved\":false,\"comments\":{\"nodes\":[{\"databaseId\":2833046835},{\"databaseId\":2833070054}]}}" +
        "]}}}}}";
    
    // What if there are no threads?
    private static final String SAMPLE_RESPONSE_2 = 
        "{\"data\":{\"repository\":{\"pullRequest\":{\"reviewThreads\":{\"nodes\":[]}}}}}";
    
    // What if there's an error?
    private static final String SAMPLE_RESPONSE_3 = 
        "{\"errors\":[{\"message\":\"Field 'reviewThreads' doesn't exist on type 'PullRequest'\"}]}";
    
    public static void main(String[] args) {
        System.out.println("Testing GraphQL Response Parsing");
        System.out.println("=================================\n");
        
        System.out.println("Test 1: Normal response with resolved and unresolved threads");
        Map<Long, Boolean> result1 = parseThreadResolutionStates(SAMPLE_RESPONSE_1);
        System.out.println("Result: " + result1);
        System.out.println("Expected: {2833046813=true, 2833046835=false, 2833070054=false}");
        System.out.println("Match: " + (result1.size() == 3));
        System.out.println();
        
        System.out.println("Test 2: Empty nodes array");
        Map<Long, Boolean> result2 = parseThreadResolutionStates(SAMPLE_RESPONSE_2);
        System.out.println("Result: " + result2);
        System.out.println("Expected: {}");
        System.out.println("Match: " + (result2.size() == 0));
        System.out.println();
        
        System.out.println("Test 3: Error response");
        Map<Long, Boolean> result3 = parseThreadResolutionStates(SAMPLE_RESPONSE_3);
        System.out.println("Result: " + result3);
        System.out.println("Expected: {}");
        System.out.println("Match: " + (result3.size() == 0));
        System.out.println();
    }
    
    // Copy of the actual parsing method from GitHubClient.java
    private static Map<Long, Boolean> parseThreadResolutionStates(String graphqlResult) {
        Map<Long, Boolean> result = new HashMap<>();
        
        System.out.println("  [DEBUG] Input length: " + graphqlResult.length());
        
        // Parse the JSON structure to find all threads and their comments
        // Structure: data.repository.pullRequest.reviewThreads.nodes[]
        // Each node has: id, isResolved, comments.nodes[].databaseId
        
        int nodesStart = graphqlResult.indexOf("\"nodes\":[");
        System.out.println("  [DEBUG] nodesStart index: " + nodesStart);
        if (nodesStart == -1) {
            System.out.println("  [WARNING] No 'nodes' array found in GraphQL response");
            return result;
        }
        
        String nodesSection = graphqlResult.substring(nodesStart);
        System.out.println("  [DEBUG] nodesSection: " + nodesSection.substring(0, Math.min(100, nodesSection.length())));
        
        String[] threadBlocks = nodesSection.split("\\{\"id\":");
        System.out.println("  [DEBUG] Found " + (threadBlocks.length - 1) + " thread blocks");
        
        for (int i = 1; i < threadBlocks.length; i++) {
            String threadBlock = threadBlocks[i];
            System.out.println("  [DEBUG] Thread " + i + " block: " + threadBlock.substring(0, Math.min(80, threadBlock.length())));
            
            // Extract isResolved
            boolean isResolved = threadBlock.contains("\"isResolved\":true");
            System.out.println("  [DEBUG] Thread " + i + " isResolved=" + isResolved);
            
            // Extract comment database IDs from this thread
            String commentNodesMarker = "\"comments\":{\"nodes\":[";
            int commentStart = threadBlock.indexOf(commentNodesMarker);
            System.out.println("  [DEBUG] Thread " + i + " commentStart index: " + commentStart);
            
            if (commentStart != -1) {
                String commentsSection = threadBlock.substring(commentStart + commentNodesMarker.length());
                System.out.println("  [DEBUG] Thread " + i + " commentsSection: " + commentsSection.substring(0, Math.min(50, commentsSection.length())));
                
                String[] commentBlocks = commentsSection.split("\\{\"databaseId\":");
                System.out.println("  [DEBUG] Thread " + i + " has " + (commentBlocks.length - 1) + " comments");
                
                for (int j = 1; j < commentBlocks.length; j++) {
                    String commentBlock = commentBlocks[j];
                    System.out.println("  [DEBUG] Thread " + i + " Comment " + j + " block: " + commentBlock.substring(0, Math.min(30, commentBlock.length())));
                    
                    int endIndex = commentBlock.indexOf('}');
                    System.out.println("  [DEBUG] Thread " + i + " Comment " + j + " endIndex: " + endIndex);
                    
                    if (endIndex != -1) {
                        try {
                            String idString = commentBlock.substring(0, endIndex);
                            System.out.println("  [DEBUG] Thread " + i + " Comment " + j + " idString: '" + idString + "'");
                            
                            long commentId = Long.parseLong(idString);
                            result.put(commentId, isResolved);
                            System.out.println("  [INFO] Mapped comment " + commentId + " -> " + isResolved);
                        } catch (NumberFormatException e) {
                            System.out.println("  [WARNING] Failed to parse comment ID from: " + commentBlock.substring(0, Math.min(50, endIndex)));
                            System.out.println("  [WARNING] Exception: " + e.getMessage());
                        }
                    }
                }
            } else {
                System.out.println("  [WARNING] No comments found in thread " + i);
            }
        }
        
        System.out.println("  [INFO] Parsed " + result.size() + " total comment states");
        return result;
    }
}

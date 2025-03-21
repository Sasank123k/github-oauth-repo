package com.wellsfargo.utcap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellsfargo.utcap.dto.GitOperationRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * Controller for handling Git operations.
 * It processes operations like creating a branch, updating a file, adding a file,
 * merging branches, and a unified pushFile operation.
 */
@RestController
@RequestMapping("/ghe")
public class GitOperationsController {

    private static final Logger log = LoggerFactory.getLogger(GitOperationsController.class);
    private static final String GHE_API_BASE = "https://api.github.com/";
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Endpoint to perform Git operations based on the operation type specified in the request.
     *
     * @param request GitOperationRequest payload containing details of the Git operation
     * @param session HttpSession to obtain the stored access token
     * @return ResponseEntity with the result of the operation or an error message
     */
    @PostMapping("/operation")
    public ResponseEntity<?> performOperation(@RequestBody GitOperationRequest request, HttpSession session) {
        String accessToken = (String) session.getAttribute("GHE_ACCESS_TOKEN");
        if (accessToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        try {
            String result;
            switch (request.getOperation()) {
                case "createBranch":
                    result = createBranch(accessToken, request);
                    break;
                case "pushFile":
                    result = pushFile(accessToken, request);
                    break;
                case "updateFile":
                    // For backward compatibility, if needed
                    result = updateFile(accessToken, request);
                    break;
                case "addFile":
                    // For backward compatibility, if needed
                    result = addFile(accessToken, request);
                    break;
                case "mergeBranch":
                    result = mergeBranch(accessToken, request);
                    break;
                default:
                    return ResponseEntity.badRequest().body("Invalid operation: " + request.getOperation());
            }
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            log.error("Operation failed", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Operation failed: " + ex.getMessage());
        }
    }

    /**
     * Creates a new branch in the repository.
     * Automatically fetches the repository's default branch and its commit SHA,
     * then uses that SHA as the base for the new branch.
     *
     * @param accessToken the GitHub access token
     * @param request     details for branch creation (new branch name, owner, repo)
     * @return the response from the GitHub API as a String
     */
    private String createBranch(String accessToken, GitOperationRequest request) throws IOException {
        String owner = request.getOwner();
        String repo = request.getRepo();

        // Automatically fetch the default branch name
        String defaultBranch = getDefaultBranch(accessToken, owner, repo);
        // Fetch the latest commit SHA from the default branch
        String baseSha = getBranchSha(accessToken, owner, repo, defaultBranch);

        String url = GHE_API_BASE + "/repos/" + owner + "/" + repo + "/git/refs";
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildAuthHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Use the fetched baseSha instead of relying on user input
        String payload = String.format("{\"ref\": \"refs/heads/%s\", \"sha\": \"%s\"}",
                request.getNewBranch(), baseSha);
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        log.info("Create branch response: {}", response.getBody());
        return response.getBody();
    }

    /**
     * Unified method to push a file.
     * Checks if the file exists on GitHub:
     * - If it exists, automatically updates the file.
     * - If it does not exist, adds the file.
     *
     * @param accessToken the GitHub access token
     * @param request     details for the file operation (file path, commit message, content, etc.)
     * @return the response from the GitHub API as a String
     */
    private String pushFile(String accessToken, GitOperationRequest request) throws IOException {
        String owner = request.getOwner();
        String repo = request.getRepo();
        String filePath = request.getFilePath();
        String url = GHE_API_BASE + "/repos/" + owner + "/" + repo + "/contents/" + filePath;
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildAuthHeaders(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        try {
            // Try to get the file details to check if it exists
            ResponseEntity<String> getResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            // Extract the current file SHA
            String existingFileSha = extractShaFromResponse(getResponse.getBody());
            request.setFileSha(existingFileSha);
            log.info("File exists. Proceeding with update.");
            return updateFile(accessToken, request);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("File does not exist. Proceeding with add.");
                return addFile(accessToken, request);
            } else {
                throw e;
            }
        }
    }

    /**
     * Updates an existing file in the repository.
     *
     * @param accessToken the GitHub access token
     * @param request     details for updating the file including file path, commit message, content, and file SHA
     * @return the response from the GitHub API as a String
     */
    private String updateFile(String accessToken, GitOperationRequest request) {
        String url = GHE_API_BASE + "/repos/" + request.getOwner() + "/" + request.getRepo()
                + "/contents/" + request.getFilePath();
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildAuthHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = String.format("{\"message\": \"%s\", \"content\": \"%s\", \"sha\": \"%s\"}",
                request.getCommitMessage(), request.getContent(), request.getFileSha());
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        log.info("Update file response: {}", response.getBody());
        return response.getBody();
    }

    /**
     * Adds a new file to the repository.
     *
     * @param accessToken the GitHub access token
     * @param request     details for adding the file including file path, commit message, and content
     * @return the response from the GitHub API as a String
     */
    private String addFile(String accessToken, GitOperationRequest request) {
        String url = GHE_API_BASE + "/repos/" + request.getOwner() + "/" + request.getRepo()
                + "/contents/" + request.getFilePath();
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildAuthHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = String.format("{\"message\": \"%s\", \"content\": \"%s\"}",
                request.getCommitMessage(), request.getContent());
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        log.info("Add file response: {}", response.getBody());
        return response.getBody();
    }

    /**
     * Merges two branches in the repository.
     *
     * @param accessToken the GitHub access token
     * @param request     details for merging including base branch, head branch, and commit message
     * @return the response from the GitHub API as a String
     */
    private String mergeBranch(String accessToken, GitOperationRequest request) {
        String url = GHE_API_BASE + "/repos/" + request.getOwner() + "/" + request.getRepo() + "/merges";
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildAuthHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = String.format("{\"base\": \"%s\", \"head\": \"%s\", \"commit_message\": \"%s\"}",
                request.getBaseBranch(), request.getHeadBranch(), request.getCommitMessage());
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        log.info("Merge branch response: {}", response.getBody());
        return response.getBody();
    }

    /**
     * Builds and returns HTTP headers with authorization and required internal headers.
     *
     * @param accessToken the GitHub access token
     * @return HttpHeaders with the necessary header values set
     */
    private HttpHeaders buildAuthHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + accessToken);
        headers.setAccept(Collections.singletonList(MediaType.parseMediaType("application/vnd.github.v3+json")));
        headers.add("X-REQUEST-ID", UUID.randomUUID().toString());
        headers.add("X-CORRELATION-ID", UUID.randomUUID().toString());
        headers.add("X-CLIENT-ID", "UTCAP");
        return headers;
    }

    /**
     * Retrieves the default branch for a given repository using the GitHub API.
     *
     * @param accessToken the GitHub access token
     * @param owner       the repository owner's username
     * @param repo        the repository name
     * @return the default branch name as a String
     * @throws IOException if JSON parsing fails
     */
    private String getDefaultBranch(String accessToken, String owner, String repo) throws IOException {
        String url = GHE_API_BASE + "/repos/" + owner + "/" + repo;
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildAuthHeaders(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        String defaultBranch = root.get("default_branch").asText();
        log.info("Default branch for {}/{}: {}", owner, repo, defaultBranch);
        return defaultBranch;
    }

    /**
     * Retrieves the latest commit SHA for a given branch using the GitHub API.
     *
     * @param accessToken the GitHub access token
     * @param owner       the repository owner's username
     * @param repo        the repository name
     * @param branchName  the branch name
     * @return the latest commit SHA as a String
     * @throws IOException if JSON parsing fails
     */
    private String getBranchSha(String accessToken, String owner, String repo, String branchName) throws IOException {
        String url = GHE_API_BASE + "/repos/" + owner + "/" + repo + "/branches/" + branchName;
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildAuthHeaders(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        String branchSha = root.get("commit").get("sha").asText();
        log.info("SHA for branch {} in {}/{}: {}", branchName, owner, repo, branchSha);
        return branchSha;
    }

    /**
     * Extracts the file SHA from the JSON response returned by the GitHub API.
     *
     * @param responseBody the JSON response body from the file details API
     * @return the file SHA as a String
     * @throws IOException if JSON parsing fails
     */
    private String extractShaFromResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.get("sha").asText();
    }
}

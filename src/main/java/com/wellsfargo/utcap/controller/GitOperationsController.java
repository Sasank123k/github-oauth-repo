package com.wellsfargo.utcap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellsfargo.utcap.dto.GitOperationRequest;
import com.wellsfargo.utcap.service.PathConstructorService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * Controller for handling Git operations.
 */
@RestController
@RequestMapping("/ghe")
public class GitOperationsController {

    private static final Logger log = LoggerFactory.getLogger(GitOperationsController.class);
    private static final String GHE_API_BASE = "https://api.github.com/";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private PathConstructorService pathConstructorService;

    /**
     * Endpoint to perform Git operations based on the operation type specified in the request.
     * For file operations, the request should include: sor, feedName, fileType.
     *
     * @param request GitOperationRequest payload containing details of the Git operation.
     * @param session HttpSession to obtain the stored access token.
     * @return ResponseEntity with the result of the operation or an error message.
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
                    // Use PathConstructorService to compute the target file path.
                    String sor = request.getSor();         // e.g., "sor1"
                    String feedName = request.getFeedName(); // e.g., "feed1"
                    String fileType = request.getFileType(); // e.g., "json", "sql", etc.
                    String owner = request.getOwner();
                    String repo = request.getRepo();

                    log.info("performOperation: Received pushFile request for sor={}, feedName={}, fileType={}", sor, feedName, fileType);
                    // Determine SOR structure type.
                    int structureType = pathConstructorService.determineStructureType(owner, repo, sor, accessToken);
                    log.info("performOperation: Determined structureType={}", structureType);
                    // Compute target file path.
                    String targetPath = pathConstructorService.constructTargetPath(sor, feedName, fileType, structureType);
                    log.info("performOperation: Computed target file path: {}", targetPath);
                    // Directly set the computed file path.
                    request.setFilePath(targetPath);
                    result = pushFile(accessToken, request);
                    break;
                case "updateFile":
                    result = updateFile(accessToken, request);
                    break;
                case "addFile":
                    result = addFile(accessToken, request);
                    break;
                case "mergeBranch":
                    result = mergeBranch(accessToken, request);
                    break;
                default:
                    return ResponseEntity.badRequest().body("Invalid operation: " + request.getOperation());
            }
            log.info("performOperation: Operation result: {}", result);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            log.error("performOperation: Operation failed", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Operation failed: " + ex.getMessage());
        }
    }

    /**
     * Creates a new branch in the repository.
     * If the branch already exists, returns a message indicating so.
     */
    private String createBranch(String accessToken, GitOperationRequest request) throws IOException {
        String owner = request.getOwner();
        String repo = request.getRepo();
        String newBranch = request.getNewBranch();

        // Check if branch already exists.
        String branchUrl = GHE_API_BASE + "/repos/" + owner + "/" + repo + "/branches/" + newBranch;
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildAuthHeaders(accessToken);
        HttpEntity<?> getEntity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> branchResponse = restTemplate.exchange(branchUrl, HttpMethod.GET, getEntity, String.class);
            if (branchResponse.getStatusCode() == HttpStatus.OK) {
                log.info("createBranch: Branch {} already exists.", newBranch);
                return "Branch " + newBranch + " already exists.";
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw e;
            }
        }

        // Create branch.
        String defaultBranch = getDefaultBranch(accessToken, owner, repo);
        String baseSha = getBranchSha(accessToken, owner, repo, defaultBranch);
        String url = GHE_API_BASE + "/repos/" + owner + "/" + repo + "/git/refs";
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = String.format(
                "{\"ref\": \"refs/heads/%s\", \"sha\": \"%s\"}",
                newBranch, baseSha
        );
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        log.info("createBranch: Create branch response: {}", response.getBody());
        return response.getBody();
    }

    /**
     * Unified method to push a file.
     * Checks if the file exists on GitHub (in the specified branch):
     * - If it exists, updates the file.
     * - If not, adds the file.
     */
    private String pushFile(String accessToken, GitOperationRequest request) throws IOException {
        String owner = request.getOwner();
        String repo = request.getRepo();
        String filePath = request.getFilePath();
        // Append branch query parameter.
        String url = GHE_API_BASE + "/repos/" + owner + "/" + repo + "/contents/" + filePath + "?ref=" + request.getNewBranch();
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildAuthHeaders(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> getResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            log.info("pushFile: GET response for file update: {}", getResponse.getBody());
            String existingFileSha = extractShaFromResponse(getResponse.getBody());
            request.setFileSha(existingFileSha);
            log.info("pushFile: File exists. Proceeding with update. SHA: {}", existingFileSha);
            return updateFile(accessToken, request);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("pushFile: File does not exist in branch {}. Proceeding with add.", request.getNewBranch());
                return addFile(accessToken, request);
            } else {
                throw e;
            }
        }
    }

    /**
     * Updates an existing file in the repository.
     * Now includes the branch parameter.
     */
    private String updateFile(String accessToken, GitOperationRequest request) {
        String url = GHE_API_BASE + "/repos/" + request.getOwner() + "/" + request.getRepo()
                + "/contents/" + request.getFilePath();
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildAuthHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = String.format(
                "{\"message\": \"%s\", \"content\": \"%s\", \"sha\": \"%s\", \"branch\": \"%s\"}",
                request.getCommitMessage(), request.getContent(), request.getFileSha(), request.getNewBranch()
        );
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        log.info("updateFile: Update file response: {}", response.getBody());
        return response.getBody();
    }

    /**
     * Adds a new file to the repository.
     * Now includes the branch parameter.
     */
    private String addFile(String accessToken, GitOperationRequest request) {
        String url = GHE_API_BASE + "/repos/" + request.getOwner() + "/" + request.getRepo()
                + "/contents/" + request.getFilePath();
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildAuthHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = String.format(
                "{\"message\": \"%s\", \"content\": \"%s\", \"branch\": \"%s\"}",
                request.getCommitMessage(), request.getContent(), request.getNewBranch()
        );
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        log.info("addFile: Add file response: {}", response.getBody());
        return response.getBody();
    }

    /**
     * Merges two branches in the repository.
     */
    private String mergeBranch(String accessToken, GitOperationRequest request) {
        String url = GHE_API_BASE + "/repos/" + request.getOwner() + "/" + request.getRepo() + "/merges";
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildAuthHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = String.format(
                "{\"base\": \"%s\", \"head\": \"%s\", \"commit_message\": \"%s\"}",
                request.getBaseBranch(), request.getHeadBranch(), request.getCommitMessage()
        );
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        log.info("mergeBranch: Merge branch response: {}", response.getBody());
        return response.getBody();
    }

    /**
     * Retrieves the default branch for a given repository.
     */
    private String getDefaultBranch(String accessToken, String owner, String repo) throws IOException {
        String url = GHE_API_BASE + "/repos/" + owner + "/" + repo;
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildAuthHeaders(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        String defaultBranch = root.get("default_branch").asText();
        log.info("getDefaultBranch: Default branch for {}/{}: {}", owner, repo, defaultBranch);
        return defaultBranch;
    }

    /**
     * Retrieves the latest commit SHA for a given branch.
     */
    private String getBranchSha(String accessToken, String owner, String repo, String branchName) throws IOException {
        String url = GHE_API_BASE + "/repos/" + owner + "/" + repo + "/branches/" + branchName;
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildAuthHeaders(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        String sha = root.get("commit").get("sha").asText();
        log.info("getBranchSha: SHA for branch {} in {}/{}: {}", branchName, owner, repo, sha);
        return sha;
    }

    /**
     * Extracts the file SHA from a GitHub API response.
     * Checks if the response is an object or an array.
     */
    private String extractShaFromResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        if (root.isArray() && root.size() > 0) {
            String sha = root.get(0).get("sha").asText();
            log.info("extractShaFromResponse: Extracted SHA from array: {}", sha);
            return sha;
        } else if (root.isObject() && root.has("sha")) {
            String sha = root.get("sha").asText();
            log.info("extractShaFromResponse: Extracted SHA from object: {}", sha);
            return sha;
        } else {
            log.error("extractShaFromResponse: Unable to extract sha from response: {}", responseBody);
            throw new IOException("SHA not found in GitHub response.");
        }
    }

    /**
     * Builds authentication headers for GitHub API requests.
     */
    private HttpHeaders buildAuthHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + accessToken);
        headers.setAccept(Collections.singletonList(
                org.springframework.http.MediaType.parseMediaType("application/vnd.github.v3+json")));
        headers.add("X-REQUEST-ID", UUID.randomUUID().toString());
        headers.add("X-CORRELATION-ID", UUID.randomUUID().toString());
        headers.add("X-CLIENT-ID", "UTCAP");
        return headers;
    }
}

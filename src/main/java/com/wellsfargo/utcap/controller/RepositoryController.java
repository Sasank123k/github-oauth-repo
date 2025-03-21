package com.wellsfargo.utcap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.UUID;

/**
 * Controller for fetching GitHub repositories and branches.
 * Provides endpoints to list repositories (with push access) and branches for a specific repository.
 */
@RestController
@RequestMapping("/ghe")
public class RepositoryController {

    private static final Logger log = LoggerFactory.getLogger(RepositoryController.class);
    private static final String GHE_API_BASE = "https://api.github.com/";

    /**
     * Retrieves the list of repositories where the authenticated user has push access.
     *
     * @param session HttpSession to obtain the stored access token
     * @return ResponseEntity with filtered repository list or error status
     */
    @GetMapping("/repositories")
    public ResponseEntity<?> getRepositories(HttpSession session) {
        String accessToken = (String) session.getAttribute("GHE_ACCESS_TOKEN");
        if (accessToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        RestTemplate restTemplate = new RestTemplate();
        String url = GHE_API_BASE + "/user/repos";
        HttpHeaders headers = buildAuthHeaders(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        String responseBody = response.getBody();
        log.info("Repositories API response status: {}", response.getStatusCode());

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode rootArray = objectMapper.readTree(responseBody);
            ArrayNode filteredRepos = objectMapper.createArrayNode();
            // Filter repositories where the user has push permissions
            if (rootArray.isArray()) {
                for (JsonNode repoNode : rootArray) {
                    JsonNode permissions = repoNode.get("permissions");
                    if (permissions != null && permissions.get("push").asBoolean()) {
                        ObjectNode simpleRepo = objectMapper.createObjectNode();
                        simpleRepo.put("name", repoNode.get("name").asText());
                        simpleRepo.put("owner", repoNode.get("owner").get("login").asText());
                        filteredRepos.add(simpleRepo);
                    }
                }
            }
            return ResponseEntity.ok(filteredRepos);
        } catch (Exception e) {
            log.error("Error parsing repositories", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing repositories");
        }
    }

    /**
     * Retrieves the list of branches for a specified repository.
     *
     * @param owner   the repository owner's username
     * @param repo    the repository name
     * @param session HttpSession to obtain the stored access token
     * @return ResponseEntity with branch names or error status
     */
    @GetMapping("/branches")
    public ResponseEntity<?> getBranches(@RequestParam("owner") String owner,
                                         @RequestParam("repo") String repo,
                                         HttpSession session) {
        String accessToken = (String) session.getAttribute("GHE_ACCESS_TOKEN");
        if (accessToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        RestTemplate restTemplate = new RestTemplate();
        String url = GHE_API_BASE + "/repos/" + owner + "/" + repo + "/branches";
        HttpHeaders headers = buildAuthHeaders(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        String responseBody = response.getBody();
        log.info("Branches API response: {}", responseBody);

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            ArrayNode branchNames = objectMapper.createArrayNode();
            // Iterate through the array and add each branch name
            if (root.isArray()) {
                for (JsonNode branch : root) {
                    branchNames.add(branch.get("name").asText());
                }
            }
            return ResponseEntity.ok(branchNames);
        } catch (Exception e) {
            log.error("Error parsing branches", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing branches");
        }
    }

    /**
     * Builds and returns HTTP headers with authentication and required internal headers.
     *
     * @param accessToken the GitHub access token
     * @return HttpHeaders including authorization and internal header values
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
}

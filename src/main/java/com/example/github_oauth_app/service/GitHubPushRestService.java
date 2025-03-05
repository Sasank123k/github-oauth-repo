package com.example.github_oauth_app.service;


import com.example.github_oauth_app.model.PushRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class GitHubPushRestService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void pushJsonFile(PushRequest request, String userAccessToken) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "token " + userAccessToken);

        // Construct the URL using dynamic repo info
        String url = "https://api.github.com/repos/" + request.getRepoOwner() + "/" + request.getRepoName() + "/contents/" + request.getFilePath();

        // Check if the file exists (to get its SHA for update)
        String sha = null;
        try {
            ResponseEntity<String> getResponse = restTemplate.exchange(
                    url + "?ref=" + request.getBranch(),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            Map<String, Object> fileInfo = objectMapper.readValue(getResponse.getBody(), HashMap.class);
            sha = (String) fileInfo.get("sha");
        } catch (Exception ex) {
            // If the file does not exist, sha remains null, and we'll create it
        }

        // Build the request payload
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("message", "Update JSON file via REST API");
        requestBody.put("content", request.getContent());  // Already Base64 encoded JSON content
        requestBody.put("branch", request.getBranch());
        if (sha != null) {
            requestBody.put("sha", sha);
        }

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to push file: " + response.getBody());
        }
    }
}

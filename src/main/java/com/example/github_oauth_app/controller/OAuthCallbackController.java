package com.example.github_oauth_app.controller;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@Controller
public class OAuthCallbackController {

    @Value("${github.client.id}")
    private String clientId;

    @Value("${github.client.secret}")
    private String clientSecret;

    @Value("${github.redirect.uri}")
    private String redirectUri;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/oauth/callback")
    public ResponseEntity<String> githubCallback(@RequestParam("code") String code, HttpSession session) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String tokenUrl = "https://github.com/login/oauth/access_token";

            String requestBody = "client_id=" + clientId +
                    "&client_secret=" + clientSecret +
                    "&code=" + code +
                    "&redirect_uri=" + redirectUri;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(tokenUrl, HttpMethod.POST, entity, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            String accessToken = jsonNode.get("access_token").asText();

            // Log the obtained token
            System.out.println("Obtained access token: " + accessToken);

            // Store token in session and log the session ID
            session.setAttribute("githubAccessToken", accessToken);
            System.out.println("Stored access token in session. Session ID: " + session.getId());

            return ResponseEntity.ok("Access Token stored in session. You can now use the app.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error exchanging code for token: " + e.getMessage());
        }
    }
}

package com.example.github_oauth_app.controller;


import com.example.github_oauth_app.model.PushRequest;
import com.example.github_oauth_app.service.GitHubPushRestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api")
public class GitHubPushController {

    private final GitHubPushRestService pushService;

    public GitHubPushController(GitHubPushRestService pushService) {
        this.pushService = pushService;
    }

    @PostMapping("/push-json")
    public ResponseEntity<String> pushJson(@RequestBody PushRequest request, HttpSession session) {
        try {
            // Retrieve the user's access token from session
            String userAccessToken = (String) session.getAttribute("githubAccessToken");
            System.out.println("Access token in session: " + userAccessToken);
            if (userAccessToken == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User is not authenticated with GitHub.");
            }

            pushService.pushJsonFile(request, userAccessToken);
            return ResponseEntity.ok("File pushed successfully!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to push file: " + e.getMessage());
        }
    }
}


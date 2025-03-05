package com.example.github_oauth_app.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestSessionController {

    @GetMapping("/test-session")
    public String testSession(HttpSession session) {
        String token = (String) session.getAttribute("githubAccessToken");
        return "Session ID: " + session.getId() + " | Stored Token: " + token;
    }
}
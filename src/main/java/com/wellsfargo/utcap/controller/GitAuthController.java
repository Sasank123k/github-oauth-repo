package com.wellsfargo.utcap.controller;

import com.wellsfargo.utcap.model.GithubClientProperties;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

/**
 * Controller for GitHub Authentication (OAuth Flow).
 * It provides endpoints for redirecting to GitHub for authentication (/auth)
 * and handling the callback (/callback) after user authorization.
 */
@RestController
@RequestMapping("/ghe")
public class GitAuthController {

    private static final Logger log = LoggerFactory.getLogger(GitAuthController.class);
    private final GithubClientProperties githubClientProperties;

    // Inject GitHub client properties from configuration
    public GitAuthController(GithubClientProperties githubClientProperties) {
        this.githubClientProperties = githubClientProperties;
    }

    /**
     * Redirects the user to GitHub Enterprise's OAuth login page.
     *
     * @param response HttpServletResponse used for redirection
     * @param session  HttpSession to store the generated OAuth state
     * @throws IOException if redirection fails
     */
    @GetMapping("/auth")
    public void redirectToGithub(HttpServletResponse response, HttpSession session) throws IOException {
        // Generate a unique state parameter to prevent CSRF
        String state = UUID.randomUUID().toString();
        session.setAttribute("GITHUB_OAUTH_STATE", state);
        log.info("In /auth: Session ID: {} | State set: '{}'", session.getId(), state);

        // Build the authorization URL with required parameters
        String clientId = githubClientProperties.getId();
        String redirectUri = "http://localhost:8080/ghe/callback";
        String authorizationUrl = "https://github.com/login/oauth/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&scope=repo"
                + "&state=" + state;
        response.sendRedirect(authorizationUrl);
    }

    /**
     * Handles the GitHub callback after user authorization.
     * Exchanges the provided code for an access token and stores it in the session.
     *
     * @param code     the code provided by GitHub
     * @param state    the state parameter to validate the request
     * @param session  HttpSession to retrieve and store attributes
     * @param response HttpServletResponse used for redirection or error response
     * @throws IOException if response writing fails
     */
    @GetMapping("/callback")
    public void handleGithubCallback(String code, String state, HttpSession session, HttpServletResponse response) throws IOException {
        log.info("In /callback: Session ID: {}", session.getId());
        String sessionState = (String) session.getAttribute("GITHUB_OAUTH_STATE");
        log.info("In /callback: Stored state: '{}' | Received state: '{}'", sessionState, state);

        // Validate the state parameter
        if (sessionState == null || !sessionState.equals(state)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid state parameter");
            return;
        }

        // Exchange the authorization code for an access token
        String accessToken = getAccessTokenFromGhe(code);
        log.info("Access Token: {}", accessToken);

        // Store the access token in session for future API calls
        session.setAttribute("GHE_ACCESS_TOKEN", accessToken);
        log.info("before redirect");
        response.addHeader("X-REQUEST-ID", UUID.randomUUID().toString());
        // Note: Explicit cookie setting has been removed per internal requirements

        // Redirect the user to the frontend after successful authentication
        response.sendRedirect("http://localhost:3000/githubintegrationpage");
        log.info("after redirect");
    }

    /**
     * Helper method to exchange the authorization code for an access token.
     *
     * @param code the authorization code received from GitHub
     * @return the access token as a String
     */
    private String getAccessTokenFromGhe(String code) {
        String tokenUrl = "https://github.com/login/oauth/access_token";
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

        // Prepare parameters for token exchange
        org.springframework.util.LinkedMultiValueMap<String, String> params = new org.springframework.util.LinkedMultiValueMap<>();
        params.add("client_id", githubClientProperties.getId());
        params.add("client_secret", githubClientProperties.getSecret());
        params.add("code", code);

        // Build headers including required internal headers
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("X-REQUEST-ID", UUID.randomUUID().toString());
        headers.add("X-CORRELATION-ID", UUID.randomUUID().toString());
        headers.add("X-CLIENT-ID", "UTCAP");

        org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, String>> requestEntity =
                new org.springframework.http.HttpEntity<>(params, headers);

        // Execute the POST request to fetch the access token
        org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                tokenUrl, org.springframework.http.HttpMethod.POST, requestEntity, String.class);
        String responseBody = response.getBody();
        log.info("Token endpoint response: {}", responseBody);

        // Parse and return the access token from the response
        return java.util.Arrays.stream(responseBody.split("&"))
                .filter(s -> s.startsWith("access_token"))
                .map(s -> s.split("=")[1])
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No access token found"));
    }
}

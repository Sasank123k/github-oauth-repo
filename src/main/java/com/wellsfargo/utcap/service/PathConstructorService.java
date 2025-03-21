package com.wellsfargo.utcap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class PathConstructorService {

    private static final String GHE_API_BASE = "https://api.github.com/";
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Expected folder names for Type 1 structure.
    private static final Set<String> EXPECTED_FOLDERS = new HashSet<>();
    static {
        EXPECTED_FOLDERS.add("config");
        EXPECTED_FOLDERS.add("sql");
        EXPECTED_FOLDERS.add("scripts");
        EXPECTED_FOLDERS.add("metadata");
        EXPECTED_FOLDERS.add("hql");
        EXPECTED_FOLDERS.add("ddl");
    }

    /**
     * Determines the SOR folder structure.
     * If at least one folder in "src/batch/{sor}" matches an expected file type folder, returns 1 (Type 1);
     * if the folder doesn't exist or no expected folder is found, returns 2 (Type 2).
     */
    public int determineStructureType(String owner, String repo, String sor, String accessToken) throws IOException {
        String url = GHE_API_BASE + "/repos/" + owner + "/" + repo + "/contents/src/batch/" + sor;
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = buildAuthHeaders(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String responseBody = response.getBody();
            System.out.println("determineStructureType: Response for SOR folder: " + responseBody);
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    if (node.has("name")) {
                        String folderName = node.get("name").asText().toLowerCase();
                        System.out.println("determineStructureType: Found folder: " + folderName);
                        if (EXPECTED_FOLDERS.contains(folderName)) {
                            System.out.println("determineStructureType: Detected Type 1 structure.");
                            return 1;
                        }
                    }
                }
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                System.out.println("determineStructureType: SOR folder not found. Assuming new SOR (Type 2).");
                return 2;
            } else {
                throw e;
            }
        }
        System.out.println("determineStructureType: No expected file type folders found. Defaulting to Type 2.");
        return 2;
    }

    /**
     * Constructs the target file path based on the SOR structure type, file type, and feed name.
     * The returned path is relative to the repository root.
     */
    public String constructTargetPath(String sor, String feedName, String fileType, int structureType) {
        String basePath = "src/batch/" + sor + "/";
        String targetPath;
        if (structureType == 1) {
            // Type 1: file type folders directly under sor.
            switch (fileType.toLowerCase()) {
                case "json":
                    targetPath = basePath + "config/dci/json/" + feedName + ".json";
                    break;
                case "sql":
                    targetPath = basePath + "sql/" + feedName + ".sql";
                    break;
                case "scripts":
                    targetPath = basePath + "scripts/" + feedName + ".ksh";
                    break;
                case "metadata":
                    targetPath = basePath + "metadata/" + feedName + ".txt";
                    break;
                case "hql":
                    targetPath = basePath + "hql/" + feedName + ".hql";
                    break;
                case "ddl":
                    targetPath = basePath + "ddl/" + feedName + ".ddl";
                    break;
                default:
                    targetPath = basePath;
            }
        } else {
            // Type 2: feed folder exists under sor.
            basePath = basePath + feedName + "/";
            switch (fileType.toLowerCase()) {
                case "json":
                    targetPath = basePath + "config/dci/json/" + feedName + ".json";
                    break;
                case "sql":
                    targetPath = basePath + "sql/" + feedName + ".sql";
                    break;
                case "scripts":
                    targetPath = basePath + "scripts/" + feedName + ".ksh";
                    break;
                case "metadata":
                    targetPath = basePath + "metadata/" + feedName + ".txt";
                    break;
                case "hql":
                    targetPath = basePath + "hql/" + feedName + ".hql";
                    break;
                case "ddl":
                    targetPath = basePath + "ddl/" + feedName + ".ddl";
                    break;
                default:
                    targetPath = basePath;
            }
        }
        System.out.println("constructTargetPath: For sor=" + sor + ", feedName=" + feedName + ", fileType=" + fileType +
                ", structureType=" + structureType + ", targetPath computed: " + targetPath);
        return targetPath;
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

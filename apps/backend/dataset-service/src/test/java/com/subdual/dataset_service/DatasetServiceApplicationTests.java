package com.subdual.dataset_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DatasetServiceApplicationTests {

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {
    }

    @Test
    @DisplayName("Test multipart upload to /api/v2/datasets/upload directly")
    void testMultipartUpload() {
        RestClient client = RestClient.create("http://localhost:" + port);

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.TEXT_PLAIN);
        fileHeaders.setContentDispositionFormData("file", "test.csv");
        HttpEntity<String> fileEntity = new HttpEntity<>("name,email\nAlice,alice@test.com", fileHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileEntity);

        ResponseEntity<String> response = client.post()
                .uri("/api/v2/datasets/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        System.out.println("=== Direct Dataset-Service Upload Result ===");
        System.out.println("Status: " + response.getStatusCode());
        System.out.println("Body: " + response.getBody());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("test.csv"));
    }
}


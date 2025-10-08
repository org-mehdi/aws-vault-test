package com.example.aws_vault_test.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aws_vault_test.service.AwsSecretsService;

@RestController
public class TestController {

    @Autowired
    private AwsSecretsService secretsService;

    @GetMapping("/hola")
    public ResponseEntity<String> ddd() {
        return ResponseEntity.ok("dddd");
    }

    /**
     * Get all secret names
     */
    @GetMapping("/names")
    public ResponseEntity<List<String>> getAllSecretNames() {
        return ResponseEntity.ok(secretsService.getAllSecretNames());
    }

    /**
     * Get all secrets with values (use with caution!)
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> getAllSecrets() {
        return ResponseEntity.ok(secretsService.getAllSecrets());
    }

    /**
     * Get secret by name
     */
    @GetMapping("/{secretName}")
    public ResponseEntity<String> getSecretByName(@PathVariable String secretName) {
        return ResponseEntity.ok(secretsService.getSecretByName(secretName));
    }

    /**
     * Create a new secret
     */
    @PostMapping
    public ResponseEntity<String> createSecret(
            @RequestParam String name,
            @RequestBody String secretValue,
            @RequestParam(required = false) String description) {

        String arn = secretsService.createSecret(name, secretValue, description);
        return ResponseEntity.ok("Secret created: " + arn);
    }

}

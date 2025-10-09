package com.example.aws_vault_test.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

@Service
public class AwsSecretsService {
    @Value("${aws.region:ca-central-1}")
    private String region;

    private static final Logger log = LoggerFactory.getLogger(AwsSecretsService.class);
    private SecretsManagerClient secretsClient;

    /**
     * Create a Secrets Manager client
     */
    @PostConstruct
    public void initializeClient() {
        this.secretsClient = SecretsManagerClient.builder()
                .region(Region.of("ca-central-1"))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        log.info("Initialized SecretsManagerClient for region: {}", region);
    }

    /**
     * Get all secret names (not values) in the account/region
     */
    public List<String> getAllSecretNames() {
        try {
            ListSecretsResponse response = secretsClient.listSecrets();
            return response.secretList().stream()
                    .map(SecretListEntry::name)
                    .toList();
        } catch (SecretsManagerException e) {
            log.error("Failed to list secrets", e);
            throw new RuntimeException("Failed to list secrets: " + e.getMessage(), e);
        }
    }

    /**
     * Get secret value by name
     */
    public String getSecretByName(String secretName) {
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse response = secretsClient.getSecretValue(request);
            if (response.secretString() != null) {
                return response.secretString();
            } else if (response.secretBinary() != null) {
                return response.secretBinary().asUtf8String();
            } else {
               return "No secret data found";
            }
        } catch (SecretsManagerException e) {
            log.error("Failed to retrieve secret '{}'", secretName, e);
            throw new RuntimeException("Failed to retrieve secret '" + secretName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Get all secrets with their values (use cautiously!)
     */
    public Map<String, String> getAllSecrets() {
        List<String> secretNames = getAllSecretNames();
        Map<String, String> secrets = new HashMap<>();
        
        for (String name : secretNames) {
            try {
                secrets.put(name, getSecretByName(name));
            } catch (Exception e) {
                // Skip secrets we don't have permission to read
                secrets.put(name, "[ACCESS DENIED]");
            }
        }
        return secrets;
    }

    /**
     * Create a new secret
     * @param secretName Name of the secret
     * @param secretValue Secret value (string)
     * @param description Optional description
     */
    public String createSecret(String secretName, String secretValue, String description) {
        log.debug("Creating secret: {}", secretName);
        try {
            CreateSecretRequest.Builder requestBuilder = CreateSecretRequest.builder()
                    .name(secretName)
                    .secretString(secretValue);

            if (description != null && !description.isEmpty()) {
                requestBuilder.description(description);
            }

            CreateSecretResponse response = secretsClient.createSecret(requestBuilder.build());
            String arn = response.arn();
            log.info("Created secret: {} (ARN: {})", secretName, arn);
            return arn;
        } catch (SecretsManagerException e) {
            log.error("Failed to create secret '{}'", secretName, e);
            throw new RuntimeException("Failed to create secret '" + secretName + "': " + e.getMessage(), e);
        }
    }

    // Cleanup resources
    public void close() {
        if (secretsClient != null) {
            secretsClient.close();
            log.info("Closed SecretsManagerClient");
        }
    }

}

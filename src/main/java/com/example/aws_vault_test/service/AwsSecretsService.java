package com.example.aws_vault_test.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private SecretsManagerClient secretsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initializeClient() {
        this.secretsClient = SecretsManagerClient.builder()
                .region(Region.of(Region.CA_CENTRAL_1.id()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
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
        try {
            CreateSecretRequest.Builder requestBuilder = CreateSecretRequest.builder()
                    .name(secretName)
                    .secretString(secretValue);

            if (description != null && !description.isEmpty()) {
                requestBuilder.description(description);
            }

            CreateSecretResponse response = secretsClient.createSecret(requestBuilder.build());
            return response.arn();
        } catch (SecretsManagerException e) {
            throw new RuntimeException("Failed to create secret '" + secretName + "': " + e.getMessage(), e);
        }
    }

    // Cleanup resources
    public void close() {
        if (secretsClient != null) {
            secretsClient.close();
        }
    }

}

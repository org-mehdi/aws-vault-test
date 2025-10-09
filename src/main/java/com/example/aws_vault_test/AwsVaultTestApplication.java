package com.example.aws_vault_test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.example.aws_vault_test.service.AwsSecretsService;

@SpringBootApplication
public class AwsVaultTestApplication implements CommandLineRunner {

	@Autowired
    private AwsSecretsService secretsService;
	
	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(AwsVaultTestApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		application.run(args);
	}

	@Override
	public void run(String... args) throws Exception {
		System.out.println("Application context fully initialized!");
		System.out.println("Getting secret names...");
		secretsService.getAllSecrets().forEach((name, value) -> {
			System.out.println("Secret Name: " + name);
			System.out.println("Secret Value: " + value);
		});
	}

}

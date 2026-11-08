package com.oriontech.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PrivateEnterpriseAiAssistantApplication {

	public static void main(String[] args) {
		SpringApplication.run(PrivateEnterpriseAiAssistantApplication.class, args);
	}

}

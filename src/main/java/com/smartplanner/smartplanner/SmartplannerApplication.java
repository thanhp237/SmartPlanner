package com.smartplanner.smartplanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SmartplannerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartplannerApplication.class, args);
	}

}

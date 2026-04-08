package com.osporo.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
@RequestMapping("api")
public class OsporoEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(OsporoEngineApplication.class, args);
	}

	@GetMapping("/ping")
	public String ping() {
		return "pong";
	}
}

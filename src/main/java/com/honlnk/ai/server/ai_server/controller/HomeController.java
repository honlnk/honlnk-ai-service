package com.honlnk.ai.server.ai_server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

	@GetMapping("/")
	public String home() {
		return "Welcome to AI Server - ai-server.honlnk.top";
	}
	
	@GetMapping("/health")
	public String health() {
		return "AI Server is running!";
	}
}
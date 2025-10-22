package com.honlnk.ai.server.ai_server.controller;

import com.honlnk.ai.server.ai_server.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/ai")
public class AiController {

    private final AiService aiService;

    @Autowired
    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        return aiService.chat(request.getMessage());
    }
    
    @PostMapping("/chat/{sessionId}")
    public String chatWithHistory(@PathVariable String sessionId, 
                                 @RequestBody ChatRequest request) {
        return aiService.chatWithHistory(sessionId, request.getMessage());
    }
    
    public static class ChatRequest {
        private String message;
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
}
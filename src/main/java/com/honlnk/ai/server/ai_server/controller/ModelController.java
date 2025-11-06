package com.honlnk.ai.server.ai_server.controller;

import com.honlnk.ai.server.ai_server.model.DynamicModelEntity;
import com.honlnk.ai.server.ai_server.service.DynamicModelRepository;
import com.honlnk.ai.server.ai_server.service.LlmService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    @Autowired
    private DynamicModelRepository modelRepository;

    @Autowired
    private LlmService llmService;

    @GetMapping
    public List<DynamicModelEntity> getAllModels() {
        return modelRepository.findAll();
    }

    @PostMapping
    public DynamicModelEntity createModel(@RequestBody DynamicModelEntity model) {
        if (model.getIsDefault()) {
            // Unset other default models
            List<DynamicModelEntity> defaults = modelRepository.findAll().stream()
                    .filter(DynamicModelEntity::getIsDefault)
                    .toList();
            for (DynamicModelEntity defaultModel : defaults) {
                defaultModel.setIsDefault(false);
                modelRepository.save(defaultModel);
            }
        }
        return modelRepository.save(model);
    }

    @GetMapping("/{id}")
    public DynamicModelEntity getModel(@PathVariable Long id) {
        return modelRepository.findById(id).orElse(null);
    }

    @PutMapping("/{id}/default")
    public String setDefaultModel(@PathVariable Long id) {
        DynamicModelEntity newDefault = modelRepository.findById(id).orElse(null);
        if (newDefault != null) {
            // Unset existing default
            DynamicModelEntity existingDefault = modelRepository.findByIsDefaultTrue();
            if (existingDefault != null) {
                existingDefault.setIsDefault(false);
                modelRepository.save(existingDefault);
            }
            
            // Set new default
            newDefault.setIsDefault(true);
            modelRepository.save(newDefault);
            
            // Refresh the LLM service cache
            llmService.refreshDefaultModelCache();
            
            return "Default model updated successfully";
        }
        return "Model not found";
    }

    @PostMapping("/chat")
    public String chat(@RequestParam(required = false) String modelName, @RequestParam String message) {
        ChatClient chatClient = llmService.getChatClient(modelName);
        return chatClient.prompt().user(message).call().content().toString();
    }
}
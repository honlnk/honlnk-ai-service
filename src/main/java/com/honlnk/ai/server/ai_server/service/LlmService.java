package com.honlnk.ai.server.ai_server.service;

import com.honlnk.ai.server.ai_server.model.DynamicModelEntity;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private DynamicModelEntity defaultModel;

    // Cached concurrent map for ChatClient instances with modelName as key
    private final Map<String, ChatClient> chatClientCache = new ConcurrentHashMap<>();

    /*
     * Required for creating custom chatModel
     */
    @Autowired
    private ObjectProvider<RestClient.Builder> restClientBuilderProvider;

    @Autowired
    private ObjectProvider<WebClient.Builder> webClientBuilderProvider;

    @Autowired
    private ObjectProvider<ObservationRegistry> observationRegistry;

    @Autowired
    private ObjectProvider<ChatModelObservationConvention> observationConvention;

    @Autowired
    private DynamicModelRepository dynamicModelRepository;

    public LlmService() {
    }

    /**
     * Unified ChatClient builder method that creates a ChatModel based on dynamic model entity
     * @param model Dynamic model entity
     * @param options Chat options
     * @return Configured ChatClient
     */
    private ChatClient buildChatClient(String modelName, DynamicModelEntity model, OpenAiChatOptions options) {
        // Use the dynamic model to create a custom ChatModel
        ChatModel chatModel = createChatModel(model, options);

        return ChatClient.builder(chatModel)
            .defaultAdvisors(new SimpleLoggerAdvisor())
            .defaultOptions(options)
            .build();
    }

    private ChatModel createChatModel(DynamicModelEntity dynamicModelEntity, OpenAiChatOptions defaultOptions) {
        // Set model name from dynamic entity
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
            .withModel(dynamicModelEntity.getModelName());
        
        if (dynamicModelEntity.getTemperature() != null) {
            optionsBuilder.withTemperature(dynamicModelEntity.getTemperature().floatValue());
        }
        if (dynamicModelEntity.getTopP() != null) {
            optionsBuilder.withTopP(dynamicModelEntity.getTopP().floatValue());
        }
        
        Map<String, String> headers = dynamicModelEntity.getHeaders();
        if (headers == null) {
            headers = new HashMap<>();
        }
        headers.put("User-Agent", "Honlnk-AI-Service/1.0.0");
        
        var openAiApi = openAiApi(restClientBuilderProvider.getIfAvailable(RestClient::builder),
                webClientBuilderProvider.getIfAvailable(WebClient::builder), dynamicModelEntity);
        
        var chatModel = new OpenAiChatModel(
            openAiApi,
            optionsBuilder.build()
        );

        return chatModel;
    }

    private void initializeChatClientsWithModel(DynamicModelEntity model) {
        // Set the default model
        this.defaultModel = model;
    }

    private void tryLazyInitialization() {
        // Check if defaultModel is already cached
        if (defaultModel != null) {
            log.debug("Using cached default model: {}", defaultModel.getModelName());
            return;
        }

        try {
            DynamicModelEntity fetchedDefaultModel = dynamicModelRepository.findByIsDefaultTrue();
            if (fetchedDefaultModel == null) {
                List<DynamicModelEntity> availableModels = dynamicModelRepository.findAll();
                if (!availableModels.isEmpty()) {
                    fetchedDefaultModel = availableModels.get(0);
                }
            }

            if (fetchedDefaultModel != null) {
                log.info("Lazy init ChatClient, using model: {}", fetchedDefaultModel.getModelName());
                initializeChatClientsWithModel(fetchedDefaultModel);
            }
        }
        catch (Exception e) {
            log.error("Lazy init ChatClient failed", e);
        }
    }

    public ChatClient getDefaultChatClient() {
        return getChatClient(null);
    }

    public ChatClient getChatClient(String modelName) {
        if (defaultModel == null) {
            log.warn("Default model not initialized...");
            tryLazyInitialization();

            if (defaultModel == null) {
                throw new IllegalStateException("Default model not initialized, please specify model first");
            }
        }

        // Use DEFAULT_MODELNAME as key when modelName is null or empty
        String cacheKey = (modelName == null || modelName.isEmpty()) ? defaultModel.getModelName() : modelName;

        // Check cache first
        ChatClient cachedClient = chatClientCache.get(cacheKey);
        if (cachedClient != null) {
            log.debug("Using cached ChatClient for model: {}", cacheKey);
            return cachedClient;
        }

        // Use unified ChatOptions creation
        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder().build();

        // Use unified ChatClient builder
        ChatClient client = buildChatClient(modelName, defaultModel, defaultOptions);

        // Cache the ChatClient instance
        chatClientCache.put(cacheKey, client);

        log.info("Build and cache dynamic chat client for model: {}", cacheKey);
        return client;
    }

    /**
     * Refresh the cached default model from database
     */
    public void refreshDefaultModelCache() {
        log.info("Refreshing default model cache");
        this.defaultModel = null;
        // Clear the ChatClient cache when refreshing default model
        chatClientCache.clear();
        tryLazyInitialization();
    }

    /**
     * Clear specific ChatClient from cache by model name
     * @param modelName The model name to remove from cache
     */
    public void clearChatClientCache(String modelName) {
        if (modelName != null && !modelName.isEmpty()) {
            chatClientCache.remove(modelName);
            log.info("Cleared ChatClient cache for model: {}", modelName);
        }
    }

    /**
     * Clear all ChatClient cache entries
     */
    public void clearAllChatClientCache() {
        chatClientCache.clear();
        log.info("Cleared all ChatClient cache entries");
    }

    /**
     * Get cache size for monitoring purposes
     * @return Number of cached ChatClient instances
     */
    public int getChatClientCacheSize() {
        return chatClientCache.size();
    }

    private OpenAiApi openAiApi(RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
            DynamicModelEntity dynamicModelEntity) {
        Map<String, String> headers = dynamicModelEntity.getHeaders();
        MultiValueMap<String, String> multiValueMap = new LinkedMultiValueMap<>();
        if (headers != null) {
            headers.forEach((key, value) -> multiValueMap.add(key, value));
        }

        String completionsPath = dynamicModelEntity.getCompletionsPath();
        if (completionsPath == null) {
            completionsPath = "/v1/chat/completions"; // default path
        }

        return new OpenAiApi(
            dynamicModelEntity.getBaseUrl(),
            dynamicModelEntity.getApiKey()
        );
    }
}
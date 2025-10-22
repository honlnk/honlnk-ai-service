package com.honlnk.ai.server.ai_server.service.impl;

import com.honlnk.ai.server.ai_server.service.AiService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiServiceImpl implements AiService {

    private final ChatClient chatClient;

    @Autowired
    public AiServiceImpl(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String chat(String message) {
        return chatClient
                .prompt()
                .user(message)
                .call()
                .content();
    }

    @Override
    public String chatWithHistory(String sessionId, String message) {
        // 对于简单示例，我们暂时只实现基本的聊天功能
        // 在实际应用中，这里会使用会话ID来管理对话历史
        return chat(message);
    }
}
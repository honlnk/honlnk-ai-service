package com.honlnk.ai.server.ai_server.service;

public interface AiService {
    /**
     * 与AI进行简单对话
     * @param message 用户输入的消息
     * @return AI的回复
     */
    String chat(String message);
    
    /**
     * 与AI进行带历史记录的对话
     * @param sessionId 会话ID
     * @param message 用户输入的消息
     * @return AI的回复
     */
    String chatWithHistory(String sessionId, String message);
}
package cn.geekyspace.springaidashscope.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通义千问聊天示例。
 *
 * @see <a href="https://java2ai.com/integration/chatclient/">官方文档：Chat Client API</a>
 */
@RestController
@RequestMapping("/api/qwen/chat")
class QwenChatController {

    private final ChatClient chatClient;

    public QwenChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 基础调用：同步返回文本回答。
     */
    @GetMapping("/basic")
    String generation(String userInput) {
        return this.chatClient.prompt()
                .user(userInput)
                .call()
                .content();
    }
}
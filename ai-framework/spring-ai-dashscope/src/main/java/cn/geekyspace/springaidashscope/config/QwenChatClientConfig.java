package cn.geekyspace.springaidashscope.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按任务配置客户端默认模型，不影响全局配置。
 *
 * @see <a href="https://java2ai.com/integration/chatclient/#%E4%BD%BF%E7%94%A8%E5%A4%9A%E4%B8%AA-chat-model">Spring AI Alibaba：多模型客户端</a>
 */
@Configuration
public class QwenChatClientConfig {

    @Bean("qwenPlusClient")
    public ChatClient qwenPlusClient(ChatClient.Builder builder) {
        return builder
                // 通用选项：适用于模型名、温度等跨厂商参数
                .defaultOptions(ChatOptions.builder().model("qwen-plus").build())
                .build();
    }

    @Bean("qwenMaxClient")
    public ChatClient qwenMaxClient(ChatClient.Builder builder) {
        return builder
                // DashScope 专用选项：支持通用参数及联网搜索等扩展参数
                .defaultOptions(DashScopeChatOptions.builder().model("qwen-max").build())
                .build();
    }
}

package cn.geekyspace.springaidashscope.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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


    // 知乎风格回答示例
    private static final String ZHIHU_SYSTEM_PROMPT = """
            你是一位知乎高赞答主，请用中文回答用户的问题。
            
            - 开头用 1～2 句话给出明确结论。
            - 分点说明观点、推理和例证。有可靠数据时可引用。
            - 如果存在争议，简述不同观点及各自的适用场景。
            - 适合给建议时，以可执行的建议或检查清单收尾。
            - 表达专业、克制，可用 Markdown 标题和列表，避免堆砌术语。
            - 围绕问题展开，不杜撰事实，明确标注假设和推测。
            """;

    @GetMapping("/answer")
    public String zhihuAnswer(@RequestParam("question") String question,
                              @RequestParam(name = "model", required = false) String model) {

        if (!StringUtils.hasText(question)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question 不能为空");
        }

        // 优先使用请求指定的模型，否则使用 qwen-plus
        String effectiveModel = StringUtils.hasText(model) ? model.strip() : "qwen-plus";

        return chatClient.prompt()
                .system(ZHIHU_SYSTEM_PROMPT) // 系统消息：定义回答风格和规则
                .user(question.strip())      // 用户消息：传入实际问题
                .options(ChatOptions.builder().model(effectiveModel).build()) // 设置本次调用的模型
                .call()                      // 同步调用，等待完整回答
                .content();                  // 提取回答文本
    }

}

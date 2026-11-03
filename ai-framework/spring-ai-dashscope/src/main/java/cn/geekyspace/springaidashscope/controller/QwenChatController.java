package cn.geekyspace.springaidashscope.controller;

import cn.geekyspace.springaidashscope.pojo.LogisticsInfo;
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

    // 物流信息结构化抽取示例
    private static final String LOGISTICS_SYSTEM_PROMPT = """
            你是一名物流信息抽取助手。请从用户提供的中文物流状态描述中提取以下字段，并以 JSON 返回：
            - orderId：订单号
            - currentLocation：当前位置
            - estimatedArrival：预计到达时间
            - courierName：快递员姓名
            - courierPhone：快递员联系方式
            输出要求：
            - 只返回一个 JSON 对象，不要附加任何解释或额外文本；
            - 若缺失某字段，请给出合理的空值或最可能的估计；
            - 时间字段使用可解析字符串（如 ISO-8601 或 "明晚" 这类自然语言）。
            """;

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

    /**
     * 知乎风格回答。
     */
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


    /**
     * 结构化输出：从物流描述中抽取信息，直接返回 Java 对象而非原始回答文本。
     */
    @GetMapping("/extract")
    public LogisticsInfo extractLogistics(@RequestParam("text") String text,
                                          @RequestParam(name = "model", required = false) String model) {
        String effectiveText = StringUtils.hasText(text) ? text : "订单号 2026-11-04-8899，快件已到达杭州滨江转运中心，预计今晚19:00送达；派送员张三，联系电话 13800138000。";
        String effectiveModel = StringUtils.hasText(model) ? model.strip() : "qwen-plus";

        return chatClient.prompt()
                .options(ChatOptions.builder().model(effectiveModel).build())
                .system(LOGISTICS_SYSTEM_PROMPT)
                .user(effectiveText)
                .call()
                // 1. 根据 @JsonProperty（字段名）和 @JsonPropertyDescription（字段描述）生成 JSON Schema。
                // 2. 将 Schema 加入提示词，引导模型输出对应结构的 JSON。
                // 3. 将返回的 JSON 自动转换为 LogisticsInfo 对象。
                .entity(LogisticsInfo.class);
    }

}

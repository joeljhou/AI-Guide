package cn.geekyspace.springaidashscope.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 根据任务类型、文本长度和关键词选择模型，兼顾成本与分析质量。
 */
@Service
public class QwenModelRouter {
    // 任务类型：SUMMARY（总结） | SWOT（SWOT 分析） | AUTO（自动选择模型）
    public enum TaskType {SUMMARY, SWOT, AUTO}

    private final ChatClient qwenPlusClient;
    private final ChatClient qwenMaxClient;

    public QwenModelRouter(@Qualifier("qwenPlusClient") ChatClient qwenPlusClient,
                           @Qualifier("qwenMaxClient") ChatClient qwenMaxClient) {
        this.qwenPlusClient = qwenPlusClient;
        this.qwenMaxClient = qwenMaxClient;
    }

    /**
     * A. 按任务分析文本
     *
     * @param inputText 输入文本
     * @param taskType 任务类型：SUMMARY、SWOT、AUTO
     * @param model 强制模型（可选）
     * @return 模型输出结果
     */
    public String analyze(String inputText, TaskType taskType, String model) {
        // 1. 选择客户端
        ChatClient client = selectClient(taskType, inputText);
        // 2. 构建请求（构建系统提示 + 用户输入）
        ChatClient.ChatClientRequestSpec prompt = client.prompt()
                .system(buildSystemPrompt(taskType))
                .user(inputText);
        // 3. 强制模型覆盖（指定时）
        if (StringUtils.hasText(model)) {
            prompt.options(ChatOptions.builder().model(model.strip()).build());
        }
        return prompt.call().content();
    }

    // A1. 选择客户端：根据任务类型和文本内容选择
    private ChatClient selectClient(TaskType task, String text) {
        return switch (task) {
            case SUMMARY -> qwenPlusClient;
            case SWOT -> qwenMaxClient;
            // 自动模式以文本长度和关键词作为分析复杂度的启发式判断。
            case AUTO -> text.length() > 600 || containsDeepKeywords(text) ? qwenMaxClient : qwenPlusClient;
        };
    }

    // A1a. 检查文本是否包含深度分析关键词：SWOT、深入、深度、全面分析、系统性
    private boolean containsDeepKeywords(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("swot") || normalized.contains("深入") || normalized.contains("深度")
                || normalized.contains("全面分析") || normalized.contains("系统性");
    }

    // A2. 构建系统提示：根据任务类型生成不同的系统消息，指导模型输出符合预期的结构和风格。
    private String buildSystemPrompt(TaskType task) {
        return switch (task) {
            case SUMMARY -> """
                    你是一名报告压缩助手，请生成中文摘要：
                    - 先给出一句话结论；
                    - 然后列出3-5条要点；
                    - 风格简洁、信息密度高，避免空话。
                    """;
            case SWOT -> """
                    你是一名商业分析顾问，请输出针对输入内容的SWOT分析：
                    - 结构包含四部分：Strengths、Weaknesses、Opportunities、Threats；
                    - 每部分给出3-5条要点，简洁且具体；
                    - 禁止杜撰事实，必要时标注假设；
                    - 用中文回答，可使用Markdown二级标题（##）与列表。
                    """;
            case AUTO -> """
                    你是一名智能报告分析助手：
                    - 简短文本请生成摘要（一句话结论 + 3-5要点）；
                    - 若文本包含“SWOT/深入/深度/全面分析/系统性”等关键词或超过600字符，请输出SWOT分析；
                    - 禁止杜撰事实，必要时标注假设；
                    - 中文回答，可使用Markdown结构，但避免过度装饰。
                    """;
        };
    }

}

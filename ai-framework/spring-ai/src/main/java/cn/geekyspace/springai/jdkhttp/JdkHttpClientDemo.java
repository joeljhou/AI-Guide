package cn.geekyspace.springai.jdkhttp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * 使用 JDK 原生 HttpClient 调用兼容 OpenAI 的聊天补全接口的示例。
 * <p>
 * 通过非流式请求发送系统提示词和用户消息，并输出 HTTP 状态码及响应内容。
 */
public class JdkHttpClientDemo {

    private static final String API_KEY = "sk-suvLTWDAJMi4jk2r9gz7H1bZM5umnzN3EIruxwhh0Sv449GJ"; // 替换为你的 API Key
    private static final String BASE_URL = "http://127.0.0.1:8317/v1"; // 替换为你的兼容 OpenAI API 的地址

    public static void main(String[] args) throws Exception {
        String requestBody = """
                {
                  "model": "gpt-6.1-sol",
                  "messages": [
                    {"role": "system", "content": "你是一位 Java 技术讲师。"},
                    {"role": "user", "content": "请用三句话介绍 Java。"}
                  ],
                  "stream": false
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("HTTP " + response.statusCode());
            System.out.println(response.body());
        }
    }
}
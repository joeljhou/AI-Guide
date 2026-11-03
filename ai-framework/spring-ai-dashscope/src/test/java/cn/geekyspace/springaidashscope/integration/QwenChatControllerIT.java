package cn.geekyspace.springaidashscope.integration;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * 通过真实 HTTP 请求调用通义千问，需设置 DASHSCOPE_API_KEY。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.error.include-message=always")
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = "\\S+")
class QwenChatControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("基础调用：询问模型身份，返回通义千问相关回答")
    void shouldReturnModelIntroductionWhenAskedAboutModel() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/qwen/chat/basic?userInput={userInput}",
                String.class,
                "你好，你使用的模型是？"
        );

        assertThat(response.getStatusCode())
                .as("HTTP 状态应为 200，响应体：%s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank().contains("通义千问");
        System.out.println(response.getBody());
    }

    @Test
    @DisplayName("知乎风格回答：手动指定 qwen-max 模型")
    void shouldReturnZhihuAnswerWithSpecifiedModel() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/qwen/chat/answer?question={question}&model={model}",
                String.class,
                "如何评价刘慈欣的《三体》？",
                "qwen-max"
        );

        assertThat(response.getStatusCode())
                .as("HTTP 状态应为 200，响应体：%s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank().containsPattern("\\p{IsHan}");
        System.out.println(response.getBody());
    }
}

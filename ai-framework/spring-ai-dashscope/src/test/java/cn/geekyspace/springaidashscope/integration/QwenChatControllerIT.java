package cn.geekyspace.springaidashscope.integration;

import cn.geekyspace.springaidashscope.pojo.LogisticsInfo;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过真实 HTTP 请求调用通义千问，需设置 DASHSCOPE_API_KEY。
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.error.include-message=always")
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = "\\S+")
class QwenChatControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("一、基础调用：询问模型身份")
    void testBaseMethod() {
        String url = "/api/qwen/chat/basic?userInput={userInput}";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class, "你好，你使用的模型是？");
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("HTTP 状态应为 200，响应体：%s", body)
                .isEqualTo(HttpStatus.OK);
        assertThat(body).isNotBlank();
        log.info("/basic 响应体: {}", body);
    }

    @Test
    @DisplayName("二、知乎风格回答：手动指定 qwen-max 模型")
    void testAnswerZhihuStyle() {
        String url = "/api/qwen/chat/answer?question={question}&model={model}";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class, "为什么DDD在电商系统中常见？", "qwen-max");
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("HTTP 状态应为 200，响应体：%s", body)
                .isEqualTo(HttpStatus.OK);
        assertThat(body).isNotBlank();
        log.info("/answer 响应体: {}", body);
    }

    @Test
    @DisplayName("三、结构化输出：提取物流信息")
    void testExtractLogisticsInfo() {
        String text = "订单号 2026-10-21-8899，快件已到达杭州滨江转运中心，预计今晚19:00送达；派送员张三，联系电话 13800138000。";
        String url = "/api/qwen/chat/extract?text={text}";
        ResponseEntity<LogisticsInfo> response = restTemplate.getForEntity(url, LogisticsInfo.class, text);
        LogisticsInfo info = response.getBody();

        assertThat(response.getStatusCode())
                .as("HTTP 状态应为 200，响应体：%s", info)
                .isEqualTo(HttpStatus.OK);
        assertThat(info).isNotNull();
        log.info("/extract 结构化抽取结果: {}", info);
    }
}

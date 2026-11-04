package cn.geekyspace.springaidashscope.integration;

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
 * 通过真实 HTTP 请求验证模型路由接口，需设置 DASHSCOPE_API_KEY。
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.error.include-message=always")
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = "\\S+")
class QwenRouterControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("一、摘要分析：提取关键结论与要点")
    void testSummaryEndpoint() {
        String text = "请用五条要点总结这份2页产品评审建议。";
        String url = "/api/qwen/router/summary?text={text}";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class, text);
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("HTTP 状态应为 200，响应体：%s", body)
                .isEqualTo(HttpStatus.OK);
        assertThat(body).isNotBlank().containsPattern("\\p{IsHan}");
        log.info("/router/summary 响应体: {}", body);
    }

    @Test
    @DisplayName("二、SWOT 分析：提取优势、劣势、机会与威胁")
    void testSwotEndpoint() {
        String text = "围绕跨境电商的市场与团队能力进行SWOT分析。";
        String url = "/api/qwen/router/swot?text={text}";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class, text);
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("HTTP 状态应为 200，响应体：%s", body)
                .isEqualTo(HttpStatus.OK);
        assertThat(body).isNotBlank().containsPattern("\\p{IsHan}");
        log.info("/router/swot 响应体: {}", body);
    }

    @Test
    @DisplayName("三、自动路由：根据内容自动选择模型")
    void testAnalyzeAutoEndpoint() {
        String text = "请做全面深入的SWOT分析，考虑市场竞争与供应链风险。";
        String url = "/api/qwen/router/analyze?text={text}&mode={mode}";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class, text, "auto");
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("HTTP 状态应为 200，响应体：%s", body)
                .isEqualTo(HttpStatus.OK);
        assertThat(body).isNotBlank().containsPattern("\\p{IsHan}");
        log.info("/router/analyze(auto) 响应体: {}", body);
    }

    @Test
    @DisplayName("四、手动覆盖：摘要模式指定 qwen-plus 模型")
    void testAnalyzeSummaryWithForcedModel() {
        String text = "将以下段落压缩为摘要，突出关键结论与三条要点。";
        String url = "/api/qwen/router/analyze?text={text}&mode={mode}&model={model}";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class, text, "summary", "qwen-plus");
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("HTTP 状态应为 200，响应体：%s", body)
                .isEqualTo(HttpStatus.OK);
        assertThat(body).isNotBlank().containsPattern("\\p{IsHan}");
        log.info("/router/analyze(summary + forced model) 响应体: {}", body);
    }
}

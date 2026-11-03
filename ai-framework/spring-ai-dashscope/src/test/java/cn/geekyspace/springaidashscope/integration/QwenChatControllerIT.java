package cn.geekyspace.springaidashscope.integration;

import cn.geekyspace.springaidashscope.pojo.LogisticsInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过真实 HTTP 请求调用通义千问，需设置 DASHSCOPE_API_KEY。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.error.include-message=always")
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = "\\S+")
class QwenChatControllerIT {

    private static final Logger log = LoggerFactory.getLogger(QwenChatControllerIT.class);

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("基础调用：询问模型身份，返回通义千问相关回答")
    void testBaseMethod() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/qwen/chat/basic?userInput={userInput}",
                String.class,
                "你好，你使用的模型是？"
        );

        assertThat(response.getStatusCode())
                .as("HTTP 状态应为 200，响应体：%s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank().contains("通义千问");
        log.info("模型回答：{}", response.getBody());
    }

    @Test
    @DisplayName("知乎风格回答：手动指定 qwen-max 模型")
    void testAnswerZhihuStyle() {
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
        log.info("模型回答：{}", response.getBody());
    }

    @Test
    @DisplayName("结构化抽取：使用默认模型提取五个物流字段")
    void testExtractLogisticsInfo() {
        String text = "订单号 2026-11-04-8899，快件已到达杭州滨江转运中心，预计今晚19:00送达；派送员张三，联系电话 13800138000。";
        ResponseEntity<LogisticsInfo> response = restTemplate.getForEntity(
                "/api/qwen/chat/extract?text={text}",
                LogisticsInfo.class,
                text
        );

        assertThat(response.getStatusCode())
                .as("HTTP 状态应为 200，响应体：%s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        // 获取结构化对象，验证非空后打印原始文本和抽取结果。
        LogisticsInfo info = response.getBody();
        assertThat(info).as("返回的 LogisticsInfo 不能为 null").isNotNull();
        log.info("原始文本：{}", text);
        log.info("结构化抽取结果：orderId={}, location={}, eta={}, courier={}, phone={}",
                info.orderId(), info.currentLocation(), info.estimatedArrival(), info.courierName(), info.courierPhone());
    }
}

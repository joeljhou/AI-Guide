package cn.geekyspace.springaidashscope.unit;

import cn.geekyspace.springaidashscope.pojo.LogisticsInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsInfoTests {

    private final BeanOutputConverter<LogisticsInfo> converter = new BeanOutputConverter<>(LogisticsInfo.class);

    @Test
    @DisplayName("字段注解：JSON Schema 包含五个字段及其语义描述")
    void shouldIncludePropertyNamesAndDescriptionsInSchema() throws Exception {
        JsonNode properties = new ObjectMapper().readTree(converter.getJsonSchema()).path("properties");
        Map<String, String> descriptions = Map.of(
                "orderId", "订单号",
                "currentLocation", "当前位置",
                "estimatedArrival", "预计到达时间",
                "courierName", "快递员姓名",
                "courierPhone", "快递员联系方式"
        );

        assertThat(properties.size()).isEqualTo(descriptions.size());
        descriptions.forEach((name, description) -> {
            assertThat(properties.has(name)).as("Schema 应包含字段 %s", name).isTrue();
            assertThat(properties.path(name).path("description").asText()).contains(description);
        });
    }

    @Test
    @DisplayName("结构化转换：将模型返回的 JSON 映射为物流对象")
    void shouldConvertJsonToLogisticsInfo() {
        LogisticsInfo info = converter.convert("""
                {
                  "orderId": "2026-10-21-8899",
                  "currentLocation": "杭州滨江转运中心",
                  "estimatedArrival": "2026-10-22T19:00:00",
                  "courierName": "张三",
                  "courierPhone": "13800138000"
                }
                """);

        assertThat(info).isEqualTo(new LogisticsInfo(
                "2026-10-21-8899", "杭州滨江转运中心", "2026-10-22T19:00:00", "张三", "13800138000"
        ));
    }

    @Test
    @DisplayName("空值转换：允许缺失信息以 null 表达")
    void shouldConvertNullFields() {
        LogisticsInfo info = converter.convert("""
                {
                  "orderId": "2026-10-21-8899",
                  "currentLocation": null,
                  "estimatedArrival": null,
                  "courierName": null,
                  "courierPhone": null
                }
                """);

        assertThat(info).isEqualTo(new LogisticsInfo("2026-10-21-8899", null, null, null, null));
    }
}

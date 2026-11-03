package cn.geekyspace.springaidashscope.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 物流信息结构化抽取的目标对象。
 * 字段描述会进入 BeanOutputConverter 生成的 JSON Schema，为模型提供语义与格式引导。
 */
public record LogisticsInfo(
        @JsonProperty("orderId")
        @JsonPropertyDescription("订单号：电商订单的唯一标识，例如 202409-XYZ-8899")
        String orderId,

        @JsonProperty("currentLocation")
        @JsonPropertyDescription("当前位置：快件当前所在城市或网点，例如 杭州滨江转运中心")
        String currentLocation,

        @JsonProperty("estimatedArrival")
        @JsonPropertyDescription("预计到达时间：ISO日期时间或自然语言，例如 2025-10-22 18:00 或 明晚")
        String estimatedArrival,

        @JsonProperty("courierName")
        @JsonPropertyDescription("快递员姓名：负责最后一公里派送的人员姓名")
        String courierName,

        @JsonProperty("courierPhone")
        @JsonPropertyDescription("快递员联系方式：手机号码或座机，优先手机号")
        String courierPhone
) {
}

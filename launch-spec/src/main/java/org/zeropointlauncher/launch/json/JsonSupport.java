package org.zeropointlauncher.launch.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.zeropointlauncher.launch.model.LaunchArgument;
import org.zeropointlauncher.launch.model.Rule;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 版本清单 JSON 的宽容反序列化组件。
 * <p>
 * 手写解析集中在无法用 record 直接表达的联合类型与脏数据降级上；其余字段全部交给 Jackson 绑定。
 */
public final class JsonSupport {
    private JsonSupport() {
    }

    /** 只接受 JSON 字符串；数字/布尔/对象/数组一律视为未声明。 */
    public static final class TextualOnlyDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = ctxt.readTree(p);
            return node != null && node.isTextual() ? node.textValue() : null;
        }
    }

    /** arguments 数组元素：字符串 -> Direct，对象 -> Conditional。 */
    public static final class LaunchArgumentDeserializer extends StdDeserializer<LaunchArgument> {
        public LaunchArgumentDeserializer() {
            super(LaunchArgument.class);
        }

        @Override
        public LaunchArgument deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = ctxt.readTree(p);
            if (node.isTextual()) {
                return new LaunchArgument.Direct(node.textValue());
            }
            if (node.isObject()) {
                return ctxt.readTreeAsValue(node, LaunchArgument.Conditional.class);
            }
            if (node.isNull()) {
                return null;
            }
            throw JsonMappingException.from(p, "arguments 元素必须是字符串或条件对象");
        }
    }

    /** rules 数组元素：非对象一律按保守的 DISALLOW 处理，避免脏规则让整份清单失败。 */
    public static final class RuleDeserializer extends StdDeserializer<Rule> {
        public RuleDeserializer() {
            super(Rule.class);
        }

        @Override
        public Rule deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = ctxt.readTree(p);
            if (!node.isObject()) {
                return Rule.deny();
            }
            return ctxt.readTreeAsValue(node, Rule.class);
        }
    }

    /** features：缺失/类型错误 -> 空表；布尔、带引号布尔、0/1 数字统一降级为 boolean。 */
    public static final class LenientFeaturesDeserializer extends JsonDeserializer<Map<String, Boolean>> {
        @Override
        public Map<String, Boolean> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = ctxt.readTree(p);
            if (node == null || !node.isObject()) {
                return Map.of();
            }

            var features = new LinkedHashMap<String, Boolean>();
            node.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (value.isBoolean()) {
                    features.put(entry.getKey(), value.booleanValue());
                } else if (value.isTextual()) {
                    features.put(entry.getKey(), Boolean.parseBoolean(value.textValue().strip()));
                } else if (value.isNumber()) {
                    features.put(entry.getKey(), value.intValue() != 0);
                }
            });
            return Map.copyOf(features);
        }
    }

    /** size：缺失/小数/溢出 -> null 或 -1（未知），不阻断整个清单。 */
    public static final class LenientSizeDeserializer extends JsonDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = ctxt.readTree(p);
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isIntegralNumber() && node.canConvertToLong()) {
                return node.longValue();
            }
            if (node.isNumber()) {
                try {
                    return node.decimalValue().longValueExact();
                } catch (ArithmeticException e) {
                    return -1L;
                }
            }
            if (node.isTextual()) {
                try {
                    return Long.parseLong(node.textValue().strip());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        }
    }
}

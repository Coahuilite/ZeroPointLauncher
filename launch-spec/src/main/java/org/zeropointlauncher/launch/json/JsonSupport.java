package org.zeropointlauncher.launch.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.zeropointlauncher.launch.model.LaunchArgument;
import org.zeropointlauncher.launch.model.OsRule;
import org.zeropointlauncher.launch.model.Rule;
import org.zeropointlauncher.launch.model.RuleAction;
import org.zeropointlauncher.launch.parse.ParseWarning;
import org.zeropointlauncher.launch.parse.ParseWarningCode;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 版本清单 JSON 的宽容反序列化组件。
 * <p>
 * 手写解析集中在无法用 record 直接表达的联合类型与脏数据降级上。规则条件一旦声明则严格校验：
 * 畸形的 OS / features 条件会把整条规则标记为永不命中，而不是退化成无条件规则。
 */
public final class JsonSupport {
    /**
     * 解析入口通过 Jackson {@code ObjectReader.withAttribute} 注入的告警收集器。
     * 没有该属性时（普通 {@code parse}）静默丢弃告警。
     */
    public static final Object WARNINGS_ATTRIBUTE = new Object();

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

    /**
     * rules 数组元素：非对象按保守的 DISALLOW 处理；已声明但畸形的 os / features 条件
     * 标记为永不命中，避免脏条件被误解为“无条件”。
     */
    public static final class RuleDeserializer extends StdDeserializer<Rule> {
        private static final Set<String> KNOWN_OS_FIELDS = Set.of("name", "arch", "version");

        public RuleDeserializer() {
            super(Rule.class);
        }

        @Override
        public Rule deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = ctxt.readTree(p);
            if (node == null || !node.isObject()) {
                warn(ctxt, ParseWarningCode.MALFORMED_RULE,
                        "rules 元素不是对象，已按 DISALLOW 处理");
                return Rule.deny();
            }

            RuleAction action = readAction(node.get("action"));
            JsonNode osNode = node.get("os");
            JsonNode featuresNode = node.get("features");

            Optional<OsRule> os = readOs(osNode);
            Map<String, Boolean> features = readFeatures(featuresNode);

            var unknownOsField = firstUnknownOsField(osNode);
            if (unknownOsField.isPresent()) {
                warn(ctxt, ParseWarningCode.UNKNOWN_RULE_CONDITION,
                        "规则包含未识别的 OS 条件 '" + unknownOsField.get()
                                + "'，已跳过整条规则");
                return Rule.invalid(action, os, features);
            }
            if (hasMalformedOsCondition(osNode)) {
                warn(ctxt, ParseWarningCode.MALFORMED_RULE,
                        "规则包含畸形 OS 条件，已跳过整条规则");
                return Rule.invalid(action, os, features);
            }
            var malformedVersion = firstMalformedVersionRegex(osNode);
            if (malformedVersion.isPresent()) {
                warn(ctxt, ParseWarningCode.MALFORMED_RULE,
                        "规则的 os.version 正则无效，已跳过整条规则: "
                                + malformedVersion.get());
                return Rule.invalid(action, os, features);
            }
            if (hasMalformedFeatures(featuresNode)) {
                warn(ctxt, ParseWarningCode.MALFORMED_RULE,
                        "规则包含畸形 features 条件，已跳过整条规则");
                return Rule.invalid(action, os, features);
            }
            return new Rule(action, os, features);
        }

        private static void warn(DeserializationContext ctxt, ParseWarningCode code, String message) {
            if (ctxt.getAttribute(WARNINGS_ATTRIBUTE) instanceof List<?> raw) {
                @SuppressWarnings("unchecked")
                var warnings = (List<ParseWarning>) raw;
                warnings.add(new ParseWarning(code, message));
            }
        }

        private static RuleAction readAction(JsonNode node) {
            return node != null && node.isTextual()
                    ? RuleAction.fromJson(node.textValue())
                    : RuleAction.DISALLOW;
        }

        /**
         * 缺失/null 表示未声明；一旦声明，未知属性无法求值，整条规则必须跳过。
         */
        private static Optional<String> firstUnknownOsField(JsonNode node) {
            if (node == null || !node.isObject()) {
                return Optional.empty();
            }
            for (var entry : node.properties()) {
                if (!KNOWN_OS_FIELDS.contains(entry.getKey())) {
                    return Optional.of(entry.getKey());
                }
            }
            return Optional.empty();
        }

        /**
         * 缺失/null 表示未声明；非对象、非文本字段或空白 name/arch 都属于畸形条件。
         * 空白 version 保留为已声明正则，由求值器按普通条件处理。
         */
        private static boolean hasMalformedOsCondition(JsonNode node) {
            if (node == null || node.isNull()) {
                return false;
            }
            if (!node.isObject()) {
                return true;
            }
            return isMalformedOsIdentity(node.get("name"))
                    || isMalformedOsIdentity(node.get("arch"))
                    || isMalformedOsText(node.get("version"));
        }

        private static boolean isMalformedOsIdentity(JsonNode node) {
            if (node == null || node.isNull()) {
                return false;
            }
            return !node.isTextual() || node.textValue().isBlank();
        }

        private static boolean isMalformedOsText(JsonNode node) {
            return node != null && !node.isNull() && !node.isTextual();
        }

        /**
         * 版本条件是文本时提前编译一次；语法错误在解析边界告警并把整条规则标记为畸形。
         * 原文仍保留在 {@link OsRule#version()} 中供诊断。
         */
        private static Optional<String> firstMalformedVersionRegex(JsonNode osNode) {
            if (osNode == null || !osNode.isObject()) {
                return Optional.empty();
            }
            JsonNode versionNode = osNode.get("version");
            if (versionNode == null || !versionNode.isTextual()) {
                return Optional.empty();
            }
            String regex = versionNode.textValue();
            try {
                Pattern.compile(regex);
                return Optional.empty();
            } catch (PatternSyntaxException e) {
                return Optional.of(regex);
            }
        }

        private static Optional<OsRule> readOs(JsonNode node) {
            if (node == null || node.isNull() || !node.isObject()) {
                return Optional.empty();
            }
            return Optional.of(new OsRule(
                    readOptionalText(node.get("name")),
                    readOptionalText(node.get("arch")),
                    readOptionalText(node.get("version"))
            ));
        }

        private static Optional<String> readOptionalText(JsonNode node) {
            return node == null || node.isNull()
                    ? Optional.empty()
                    : Optional.ofNullable(node.textValue());
        }

        /**
         * 缺失/null 表示未声明；声明后必须是对象，且每个值都必须是严格布尔。
         * 数组、字符串、数字或带引号布尔都不能降级为“无条件”或“false”。
         */
        private static boolean hasMalformedFeatures(JsonNode node) {
            if (node == null || node.isNull()) {
                return false;
            }
            if (!node.isObject()) {
                return true;
            }
            for (var entry : node.properties()) {
                if (!entry.getValue().isBoolean()) {
                    return true;
                }
            }
            return false;
        }

        private static Map<String, Boolean> readFeatures(JsonNode node) {
            if (node == null || !node.isObject()) {
                return Map.of();
            }

            var features = new LinkedHashMap<String, Boolean>();
            node.properties().forEach(entry -> {
                if (entry.getValue().isBoolean()) {
                    features.put(entry.getKey(), entry.getValue().booleanValue());
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

package org.zeropointlauncher.launch.rule;

import org.zeropointlauncher.launch.model.OsRule;
import org.zeropointlauncher.launch.model.Rule;
import org.zeropointlauncher.launch.model.RuleAction;
import org.zeropointlauncher.launch.model.RuleContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 版本清单规则求值器。
 * <p>
 * 纯函数：不读取磁盘、不读取系统属性、不记录日志，也不依赖时间或随机数。规则列表为空时
 * 默认放行；非空时默认拒绝，并按声明顺序由最后一次命中的规则决定结果。
 * <p>
 * 无法识别的字段、缺失的事实和畸形正则一律按条件不满足降级，不向调用方抛出业务异常。
 * 已声明但畸形的 OS / features 条件由 JSON 边界标记为 {@link Rule#malformed()}，求值器
 * 直接跳过整条规则，绝不能退化为无条件规则。
 */
public final class RuleEvaluator {
    private RuleEvaluator() {
    }

    /**
     * 按顺序求值规则列表。
     *
     * @param rules   规则列表；{@code null} 或空列表视为无条件放行
     * @param context 当前宿主事实；缺失的字段只会使依赖它的规则不命中
     * @return 最终是否放行
     */
    public static boolean evaluate(List<Rule> rules, RuleContext context) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }

        boolean allowed = false;
        for (Rule rule : rules) {
            if (rule == null || rule.malformed() || !matches(rule, context)) {
                continue;
            }
            allowed = rule.action() == RuleAction.ALLOW;
        }
        return allowed;
    }

    private static boolean matches(Rule rule, RuleContext context) {
        return matchesOs(rule.os(), context) && matchesFeatures(rule.features(), context);
    }

    private static boolean matchesOs(Optional<OsRule> declared, RuleContext context) {
        if (declared == null || declared.isEmpty()) {
            return true;
        }
        OsRule os = declared.get();
        if (os == null) {
            return false;
        }

        if (os.name().isPresent()
                && (context == null || !Objects.equals(os.name().get(), context.osName()))) {
            return false;
        }
        if (os.arch().isPresent()
                && (context == null || !Objects.equals(os.arch().get(), context.osArch()))) {
            return false;
        }
        if (os.version().isPresent()) {
            Optional<String> actualVersion = context == null ? Optional.empty() : context.osVersion();
            if (actualVersion == null || actualVersion.isEmpty()) {
                return false;
            }
            try {
                if (!Pattern.compile(os.version().get())
                        .matcher(actualVersion.get())
                        .find()) {
                    return false;
                }
            } catch (PatternSyntaxException e) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesFeatures(Map<String, Boolean> declared, RuleContext context) {
        if (declared == null || declared.isEmpty()) {
            return true;
        }
        if (context == null) {
            return false;
        }

        Map<String, Boolean> actual = context.features();
        if (actual == null) {
            return false;
        }
        for (Map.Entry<String, Boolean> entry : declared.entrySet()) {
            String key = entry.getKey();
            if (key == null || !actual.containsKey(key)
                    || !Objects.equals(entry.getValue(), actual.get(key))) {
                return false;
            }
        }
        return true;
    }
}

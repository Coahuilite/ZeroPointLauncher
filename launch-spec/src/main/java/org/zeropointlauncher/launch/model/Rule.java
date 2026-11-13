package org.zeropointlauncher.launch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.zeropointlauncher.launch.json.JsonSupport;

import java.util.Map;
import java.util.Optional;

/**
 * 单条版本清单规则。规则内的条件按 AND 聚合；未声明的条件不参与判定。
 * <p>
 * {@code malformed} 只由 JSON 解析边界设置：已声明但无法解析的条件不能被当作“未声明”，
 * 求值器必须跳过整条规则，保留之前的判定状态。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = JsonSupport.RuleDeserializer.class)
public record Rule(
        RuleAction action,
        Optional<OsRule> os,
        Map<String, Boolean> features,
        boolean malformed
) {
    public Rule {
        action = action == null ? RuleAction.DISALLOW : action;
        os = os == null ? Optional.empty() : os;
        features = features == null ? Map.of() : Map.copyOf(features);
    }

    /** 条件完整可求值的规则。 */
    public Rule(RuleAction action, Optional<OsRule> os, Map<String, Boolean> features) {
        this(action, os, features, false);
    }

    /**
     * JSON 边界发现畸形条件时使用；该规则永不命中。可解析出的原始条件仍保留，
     * 便于诊断，但绝不参与求值。
     */
    public static Rule invalid(
            RuleAction action,
            Optional<OsRule> os,
            Map<String, Boolean> features
    ) {
        return new Rule(action, os, features, true);
    }

    public static Rule deny() {
        return new Rule(RuleAction.DISALLOW, Optional.empty(), Map.of());
    }
}

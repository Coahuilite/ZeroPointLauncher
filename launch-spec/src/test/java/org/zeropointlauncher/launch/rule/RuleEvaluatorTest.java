package org.zeropointlauncher.launch.rule;

import org.junit.jupiter.api.Test;
import org.zeropointlauncher.launch.model.OsRule;
import org.zeropointlauncher.launch.model.Rule;
import org.zeropointlauncher.launch.model.RuleAction;
import org.zeropointlauncher.launch.model.RuleContext;
import org.zeropointlauncher.launch.parse.ManifestFragmentParser;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEvaluatorTest {
    private static final RuleContext WINDOWS_X64 =
            context("windows", "x86_64", null, Map.of());
    private static final RuleContext MACOS_X64 =
            context("osx", "x86_64", null, Map.of());
    private static final RuleContext MACOS_ARM64 =
            context("osx", "arm64", null, Map.of());

    @Test
    void nullOrEmptyRulesAllowByDefault() {
        assertTrue(RuleEvaluator.evaluate(null, WINDOWS_X64));
        assertTrue(RuleEvaluator.evaluate(List.of(), WINDOWS_X64));
        assertTrue(RuleEvaluator.evaluate(List.of(), null));
    }

    @Test
    void laterMatchingRuleOverridesEarlierAllow() {
        var rules = List.of(
                new Rule(RuleAction.ALLOW, Optional.empty(), Map.of()),
                new Rule(RuleAction.DISALLOW,
                        Optional.of(os("osx", null, null)), Map.of())
        );

        assertTrue(RuleEvaluator.evaluate(rules, WINDOWS_X64));
        assertFalse(RuleEvaluator.evaluate(rules, MACOS_X64));
    }

    @Test
    void allDeclaredOsConditionsMustMatch() {
        var rule = new Rule(RuleAction.ALLOW,
                Optional.of(os("osx", "arm64", null)), Map.of());

        assertFalse(RuleEvaluator.evaluate(List.of(rule), MACOS_X64));
        assertTrue(RuleEvaluator.evaluate(List.of(rule), MACOS_ARM64));
    }

    @Test
    void x86AndX86_64AreDistinctArchitectures() {
        var rule = new Rule(RuleAction.ALLOW,
                Optional.of(os(null, "x86", null)), Map.of());

        assertFalse(RuleEvaluator.evaluate(List.of(rule), WINDOWS_X64));
        assertTrue(RuleEvaluator.evaluate(
                List.of(rule), context("windows", "x86", null, Map.of())));
    }

    @Test
    void missingFeatureKeyDoesNotMatch() {
        var requiresDemoUser = new Rule(RuleAction.ALLOW, Optional.empty(),
                Map.of("is_demo_user", true));

        assertFalse(RuleEvaluator.evaluate(List.of(requiresDemoUser), WINDOWS_X64));
    }

    @Test
    void explicitlyFalseFeatureMatchesOnlyWhenPresentInContext() {
        var requiresNonDemoUser = new Rule(RuleAction.ALLOW, Optional.empty(),
                Map.of("is_demo_user", false));

        assertTrue(RuleEvaluator.evaluate(
                List.of(requiresNonDemoUser),
                context("windows", "x86_64", null, Map.of("is_demo_user", false))));
        assertFalse(RuleEvaluator.evaluate(List.of(requiresNonDemoUser), WINDOWS_X64));
    }

    @Test
    void versionRegexUsesFindSemantics() {
        var anchored = new Rule(RuleAction.ALLOW,
                Optional.of(os(null, null, "^10\\.")), Map.of());

        assertTrue(RuleEvaluator.evaluate(
                List.of(anchored),
                context("windows", "x86_64", "10.0", Map.of())));

        var unanchored = new Rule(RuleAction.ALLOW,
                Optional.of(os(null, null, "10\\.0")), Map.of());
        assertTrue(RuleEvaluator.evaluate(
                List.of(unanchored),
                context("windows", "x86_64", "release 10.0.19", Map.of())));
    }

    @Test
    void missingContextVersionDoesNotMatchVersionCondition() {
        var rule = new Rule(RuleAction.ALLOW,
                Optional.of(os(null, null, "^10\\.")), Map.of());

        assertFalse(RuleEvaluator.evaluate(List.of(rule), WINDOWS_X64));
    }

    @Test
    void malformedVersionRegexDegradesToNoMatchWithoutThrowing() {
        var rule = new Rule(RuleAction.ALLOW,
                Optional.of(os(null, null, "[a-z")), Map.of());

        boolean result = assertDoesNotThrow(() -> RuleEvaluator.evaluate(
                List.of(rule), context("windows", "x86_64", "10.0", Map.of())));
        assertFalse(result);
    }

    @Test
    void malformedOsContainerIsSkippedInsteadOfBecomingUnconditional() {
        var rules = parseRules("""
                [{"action":"allow"},{"action":"disallow","os":false}]
                """);

        assertTrue(rules.get(1).malformed());
        assertTrue(RuleEvaluator.evaluate(rules, WINDOWS_X64));
    }

    @Test
    void malformedOsFieldIsSkippedInsteadOfDroppingTheCondition() {
        var rules = parseRules("""
                [{"action":"allow"},{"action":"disallow","os":{"name":false}}]
                """);

        assertTrue(rules.get(1).malformed());
        assertTrue(RuleEvaluator.evaluate(rules, WINDOWS_X64));
    }

    @Test
    void malformedFeatureContainerIsSkippedInsteadOfBecomingUnconditional() {
        var rules = parseRules("""
                [{"action":"allow"},{"action":"disallow","features":[]}]
                """);

        assertTrue(rules.get(1).malformed());
        assertTrue(RuleEvaluator.evaluate(rules, WINDOWS_X64));
    }

    @Test
    void nonBooleanFeatureIsSkippedInsteadOfCoercingToFalse() {
        var rules = parseRules("""
                [{"action":"allow"},
                 {"action":"disallow","features":{"is_demo_user":"false"}}]
                """);

        assertTrue(rules.get(1).malformed());
        assertTrue(RuleEvaluator.evaluate(rules,
                context("windows", "x86_64", null, Map.of("is_demo_user", false))));
    }

    @Test
    void malformedRegexDisallowIsSkippedAfterAllow() {
        var rules = parseRules("""
                [{"action":"allow"},{"action":"disallow","os":{"version":"[a-z"}}]
                """);

        assertTrue(rules.get(1).malformed());
        assertTrue(RuleEvaluator.evaluate(rules,
                context("windows", "x86_64", "10.0", Map.of())));
    }

    @Test
    void malformedConditionsNeverGrantPermission() {
        assertFalse(RuleEvaluator.evaluate(
                parseRules("""
                        [{"action":"allow","features":[]}]
                        """),
                WINDOWS_X64));
        assertFalse(RuleEvaluator.evaluate(
                parseRules("""
                        [{"action":"allow","os":false}]
                        """),
                WINDOWS_X64));
    }

    @Test
    void unknownOsConditionOnAllowDoesNotGrantPermission() {
        var rules = parseRules("""
                [{"action":"allow","os":{"name":"linux","abi":"musl"}}]
                """);

        assertTrue(rules.getFirst().malformed());
        assertFalse(RuleEvaluator.evaluate(rules,
                context("linux", "x86_64", null, Map.of())));
    }

    @Test
    void unknownOsConditionOnDisallowDoesNotRevokeAllow() {
        var rules = parseRules("""
                [{"action":"allow"},
                 {"action":"disallow","os":{"name":"linux","abi":"musl"}}]
                """);

        assertTrue(rules.get(1).malformed());
        assertTrue(RuleEvaluator.evaluate(rules,
                context("linux", "x86_64", null, Map.of())));
    }

    @Test
    void blankOsNameOrArchOnDisallowDoesNotRevokeAllow() {
        var nameRules = parseRules("""
                [{"action":"allow"},{"action":"disallow","os":{"name":"   "}}]
                """);
        assertTrue(nameRules.get(1).malformed());
        assertTrue(RuleEvaluator.evaluate(nameRules, WINDOWS_X64));

        var archRules = parseRules("""
                [{"action":"allow"},{"action":"disallow","os":{"arch":"  "}}]
                """);
        assertTrue(archRules.get(1).malformed());
        assertTrue(RuleEvaluator.evaluate(archRules, WINDOWS_X64));
    }

    @Test
    void blankOsVersionRemainsDeclaredAndDoesNotMatchMissingContextVersion() {
        var rules = parseRules("""
                [{"action":"allow","os":{"version":"   "}}]
                """);
        var rule = rules.getFirst();

        assertFalse(rule.malformed());
        assertEquals(Optional.of("   "), rule.os().orElseThrow().version());
        assertFalse(RuleEvaluator.evaluate(rules, WINDOWS_X64));
        assertFalse(RuleEvaluator.evaluate(rules,
                context("windows", "x86_64", "10.0", Map.of())));
    }

    private static List<Rule> parseRules(String rulesJson) {
        var fragment = new ManifestFragmentParser().parse("""
                {"id":"x","libraries":[{"name":"a","rules":%s}]}
                """.formatted(rulesJson));
        return fragment.libraries().getFirst().rules();
    }

    private static OsRule os(String name, String arch, String version) {
        return new OsRule(
                Optional.ofNullable(name),
                Optional.ofNullable(arch),
                Optional.ofNullable(version)
        );
    }

    private static RuleContext context(
            String osName,
            String osArch,
            String osVersion,
            Map<String, Boolean> features
    ) {
        return new RuleContext(
                osName,
                osArch,
                Optional.ofNullable(osVersion),
                features
        );
    }
}

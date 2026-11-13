package org.zeropointlauncher.parse;

import org.junit.jupiter.api.Test;
import org.zeropointlauncher.launch.model.LaunchArgument;
import org.zeropointlauncher.launch.model.Library;
import org.zeropointlauncher.launch.model.OsRule;
import org.zeropointlauncher.launch.model.Rule;
import org.zeropointlauncher.launch.model.RuleAction;
import org.zeropointlauncher.launch.parse.ManifestFragmentParser;
import org.zeropointlauncher.launch.parse.ManifestParseException;
import org.zeropointlauncher.launch.parse.ParseWarningCode;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ManifestFragmentTest {
    private final ManifestFragmentParser parser = new ManifestFragmentParser();

    @Test
    void readChildWithNoMainClass() {
        var fragment = parser.parse("""
                {"id":"demo", "inheritsFrom":"base_demo"}
                """);
        assertEquals("demo", fragment.id());
        assertEquals("base_demo", fragment.inheritsFrom());
        assertNull(fragment.mainClass());
        assertNull(fragment.minecraftArguments());
        assertNull(fragment.arguments());
        assertEquals(List.of(), fragment.libraries());
    }

    @Test
    void preservesLegacyMinecraftArguments() {
        var fragment = parser.parse("""
                {
                  "id": "legacy-demo",
                  "minecraftArguments": "  --username ${auth_player_name}  --gameDir ${game_directory}  "
                }
                """);

        assertEquals("--username ${auth_player_name}  --gameDir ${game_directory}",
                fragment.minecraftArguments());
        assertNull(fragment.arguments());
    }

    @Test
    void legacyArgumentsBecomeUsableAst() {
        var fragment = parser.parse("""
                {"id":"1.12.2",
                 "minecraftArguments":"--username ${auth_player_name} --version ${version_name}"}
                """);

        assertEquals(List.of(
                new LaunchArgument.Direct("--username"),
                new LaunchArgument.Direct("${auth_player_name}"),
                new LaunchArgument.Direct("--version"),
                new LaunchArgument.Direct("${version_name}")
        ), fragment.gameArguments());
    }

    @Test
    void blankOrNullLegacyIsAbsent() {
        assertNull(parser.parse("{\"id\":\"x\",\"minecraftArguments\":\"\"}").minecraftArguments());
        assertNull(parser.parse("{\"id\":\"x\",\"minecraftArguments\":\"   \"}").minecraftArguments());
        assertNull(parser.parse("{\"id\":\"x\",\"minecraftArguments\":null}").minecraftArguments());
        assertNull(parser.parse("{\"id\":\"x\",\"minecraftArguments\":42}").minecraftArguments());
    }

    @Test
    void modernFormatWinsWhenBothPresent() {
        var fragment = parser.parse("""
                {"id":"x","minecraftArguments":"--old",
                 "arguments":{"game":["--new"],"jvm":[]}}
                """);
        assertEquals(List.of(new LaunchArgument.Direct("--new")), fragment.gameArguments());
    }

    @Test
    void preservesDirectGameAndJvmArguments() {
        var arguments = parser.parse("""
                {
                  "id": "demo",
                  "arguments": {
                    "game": ["--username", "Demo Player"],
                    "jvm": ["-cp", "${classpath}"]
                  }
                }
                """).arguments();
        assertNotNull(arguments);

        assertEquals(List.of(
                new LaunchArgument.Direct("--username"),
                new LaunchArgument.Direct("Demo Player")
        ), arguments.game());

        assertEquals(List.of(
                new LaunchArgument.Direct("-cp"),
                new LaunchArgument.Direct("${classpath}")
        ), arguments.jvm());
    }

    @Test
    void preservesConditionalArgumentsAndTheirRules() {
        var arguments = parser.parse("""
                {
                  "id": "demo",
                  "arguments": {
                    "game": [
                      {"rules": [{"action": "allow", "features": {"is_demo_user": false}}],
                       "value": ["--username", "Demo Player"]}
                    ],
                    "jvm": [
                      "-ea",
                      {"rules": [
                         {"action": "allow"},
                         {"action": "disallow", "os": {"name": "windows", "arch": "x86", "version": "^10"}}
                       ], "value": "-Ddemo=true"}
                    ]
                  }
                }
                """).arguments();
        assertNotNull(arguments);

        var featureRule = new Rule(RuleAction.ALLOW, Optional.empty(), Map.of("is_demo_user", false));
        assertEquals(List.of(new LaunchArgument.Conditional(
                List.of(featureRule), List.of("--username", "Demo Player")
        )), arguments.game());

        var allow = new Rule(RuleAction.ALLOW, Optional.empty(), Map.of());
        var os = new OsRule(
                Optional.of("windows"), Optional.of("x86"), Optional.of("^10"));
        var disallow = new Rule(RuleAction.DISALLOW, Optional.of(os), Map.of());
        assertEquals(List.of(
                new LaunchArgument.Direct("-ea"),
                new LaunchArgument.Conditional(List.of(allow, disallow), List.of("-Ddemo=true"))
        ), arguments.jvm());
    }

    @Test
    void preservesLibraryOrderAndDownloadMetadata() {
        var libraries = parser.parse("""
                {
                  "id": "demo",
                  "libraries": [
                    {"name": "example:loader:1", "url": "https://example.test/maven/"},
                    {
                      "name": "example:platform:2",
                      "natives": {"windows": "natives-windows-${arch}"},
                      "downloads": {
                        "classifiers": {
                          "natives-windows-64": {
                            "path": "example/platform/2/platform-2-natives-windows-64.jar",
                            "sha1": "0000000000000000000000000000000000000000",
                            "size": 128,
                            "url": "https://example.test/platform-native.jar"
                          }
                        }
                      },
                      "rules": [{"action": "allow"}, {"action": "disallow", "os": {"name": "osx"}}],
                      "extract": {"exclude": ["META-INF/"]}
                    },
                    {
                      "name": "example:client:3:natives-linux",
                      "downloads": {
                        "artifact": {
                          "path": "example/client/3/client-3-natives-linux.jar",
                          "sha1": "1111111111111111111111111111111111111111",
                          "size": 256,
                          "url": "https://example.test/client.jar"
                        }
                      }
                    }
                  ]
                }
                """).libraries();

        assertEquals(List.of("example:loader:1", "example:platform:2", "example:client:3:natives-linux"),
                libraries.stream().map(Library::name).toList());

        var loader = libraries.getFirst();
        assertEquals("https://example.test/maven/", loader.url());
        assertNull(loader.artifact());

        var platform = libraries.get(1);
        assertNull(platform.artifact());
        assertEquals(Map.of("windows", "natives-windows-${arch}"), platform.natives());
        assertEquals(Map.of("natives-windows-64", new Library.DownloadArtifact(
                "example/platform/2/platform-2-natives-windows-64.jar",
                "0000000000000000000000000000000000000000", 128L, "https://example.test/platform-native.jar"
        )), platform.classifiers());
        assertEquals(List.of("META-INF/"), platform.extractExcludes());
        assertEquals(List.of(
                new Rule(RuleAction.ALLOW, Optional.empty(), Map.of()),
                new Rule(RuleAction.DISALLOW, Optional.of(new OsRule(
                        Optional.of("osx"), Optional.empty(), Optional.empty())), Map.of())
        ), platform.rules());

        assertEquals(new Library.DownloadArtifact(
                "example/client/3/client-3-natives-linux.jar",
                "1111111111111111111111111111111111111111", 256L, "https://example.test/client.jar"
        ), libraries.getLast().artifact());
    }

    @Test
    void numericIdIsCoercedButNonBooleanFeatureMakesRuleMalformed() {
        var fragment = parser.parse("""
                {"id":1.20,
                 "arguments":{"game":[{"rules":[{"action":"allow",
                   "features":{"is_demo_user":"true"}}],"value":"--demo"}]}}
                """);

        assertEquals("1.20", fragment.id());
        var conditional = (LaunchArgument.Conditional) fragment.gameArguments().getFirst();
        assertEquals(1, conditional.rules().size());
        var rule = conditional.rules().getFirst();
        assertTrue(rule.malformed());
        assertEquals(RuleAction.ALLOW, rule.action());
        assertEquals(Map.of(), rule.features());
        assertEquals(List.of("--demo"), conditional.value());
    }

    @Test
    void unknownOrMissingRuleActionDegradesToDisallow() {
        for (String rules : List.of(
                "[{\"os\":{\"name\":\"windows\"}}]",
                "[{\"action\":\"allowAll\",\"os\":{\"name\":\"windows\"}}]")) {
            var rule = parser.parse("""
                    {"id":"x","libraries":[{"name":"a","rules":%s}]}
                    """.formatted(rules)).libraries().getFirst().rules().getFirst();
            assertEquals(RuleAction.DISALLOW, rule.action());
        }
    }

    @Test
    void nonObjectRuleDoesNotThrow() {
        var rules = parser.parse("""
                {"id":"x","libraries":[{"name":"a","rules":["allow"]}]}
                """).libraries().getFirst().rules();

        assertEquals(1, rules.size());
        assertEquals(RuleAction.DISALLOW, rules.getFirst().action());
    }

    @Test
    void explicitNullFieldsMeanAbsent() {
        var fragment = parser.parse("""
                {"id":"x","inheritsFrom":null,"mainClass":null,"minecraftArguments":null,
                 "arguments":null,"libraries":null}
                """);
        assertNull(fragment.inheritsFrom());
        assertNull(fragment.mainClass());
        assertNull(fragment.minecraftArguments());
        assertNull(fragment.arguments());
        assertEquals(List.of(), fragment.libraries());

        var library = parser.parse("""
                {"id":"x","libraries":[{
                  "name":"a","url":null,"downloads":null,"natives":null,"rules":null,"extract":null
                }]}
                """).libraries().getFirst();
        assertNull(library.url());
        assertNull(library.downloads());
        assertEquals(Map.of(), library.natives());
        assertEquals(List.of(), library.rules());
        assertNull(library.extract());
        assertEquals(List.of(), library.extractExcludes());

        var artifact = parser.parse("""
                {"id":"x","libraries":[{"name":"a","downloads":{"artifact":{
                  "path":"a.jar","sha1":null,"size":null,"url":null
                }}}]}
                """).libraries().getFirst().artifact();
        assertNotNull(artifact);
        assertNull(artifact.sha1());
        assertNull(artifact.size());
        assertNull(artifact.url());
        assertFalse(artifact.hasIntegrity());

        var rule = parser.parse("""
                {"id":"x","libraries":[{"name":"a","rules":[
                  {"action":"allow","os":null,"features":null}
                ]}]}
                """).libraries().getFirst().rules().getFirst();
        assertTrue(rule.os().isEmpty());
        assertEquals(Map.of(), rule.features());
    }

    @Test
    void acceptsScalarWhereArrayExpected() {
        var fragment = parser.parse("""
                {"id":"x",
                 "arguments":{"game":"--demo"},
                 "libraries":[{"name":"a",
                   "rules":{"action":"allow"},
                   "extract":{"exclude":"META-INF/"}}]}
                """);

        assertEquals(List.of(new LaunchArgument.Direct("--demo")), fragment.gameArguments());
        var library = fragment.libraries().getFirst();
        assertEquals(1, library.rules().size());
        assertEquals(RuleAction.ALLOW, library.rules().getFirst().action());
        assertEquals(List.of("META-INF/"), library.extractExcludes());
    }

    @Test
    void numericMainClassIsTreatedAsAbsent() {
        assertNull(parser.parse("{\"id\":\"x\",\"mainClass\":42}").mainClass());
    }

    @Test
    void singleLibraryObjectIsWrappedIntoList() {
        var fragment = parser.parse("{\"id\":\"x\",\"libraries\":{\"name\":\"a\"}}");
        assertEquals(1, fragment.libraries().size());
        assertEquals("a", fragment.libraries().getFirst().name());
    }

    @Test
    void numericNativesAreCoercedButNumericOsArchMakesRuleMalformed() {
        var library = parser.parse("""
                {"id":"x","libraries":[{
                  "name":"a",
                  "natives":{"windows":64},
                  "rules":[{"action":"allow","os":{"name":"windows","arch":64}}]
                }]}
                """).libraries().getFirst();

        assertEquals(Map.of("windows", "64"), library.natives());
        var rule = library.rules().getFirst();
        assertTrue(rule.malformed());
        assertEquals(Optional.of("windows"), rule.os().orElseThrow().name());
        assertTrue(rule.os().orElseThrow().arch().isEmpty());
    }

    @Test
    void malformedDeclaredConditionsAreMarkedMalformed() {
        var rules = parser.parse("""
                {"id":"x","libraries":[{"name":"a","rules":[
                  {"action":"allow","os":[],"features":[]},
                  {"action":"allow","os":{"name":false}},
                  {"action":"allow","features":{"is_demo_user":"true"}},
                  {"action":"allow","features":{"is_demo_user":1}},
                  {"action":"allow","os":null,"features":null},
                  {"action":"allow","os":{},"features":{}}
                ]}]}
                """).libraries().getFirst().rules();

        assertEquals(6, rules.size());
        assertTrue(rules.get(0).malformed());
        assertTrue(rules.get(1).malformed());
        assertTrue(rules.get(2).malformed());
        assertTrue(rules.get(3).malformed());
        assertFalse(rules.get(4).malformed());
        assertFalse(rules.get(5).malformed());
        assertEquals(Map.of(), rules.get(5).features());
        assertTrue(rules.get(5).os().isPresent());
    }

    @Test
    void unknownOsConditionsAndBlankIdentifiersAreMalformed() {
        var rules = parser.parse("""
                {"id":"x","libraries":[{"name":"a","rules":[
                  {"action":"allow","os":{"name":"linux","abi":"musl"}},
                  {"action":"allow","os":{"name":"   "}},
                  {"action":"allow","os":{"arch":""}},
                  {"action":"allow","os":{"version":"   "}}
                ]}]}
                """).libraries().getFirst().rules();

        assertEquals(4, rules.size());
        assertTrue(rules.get(0).malformed());
        assertTrue(rules.get(1).malformed());
        assertTrue(rules.get(2).malformed());
        assertFalse(rules.get(3).malformed());
        assertEquals(Optional.of("linux"), rules.get(0).os().orElseThrow().name());
        assertEquals(Optional.of("   "), rules.get(1).os().orElseThrow().name());
        assertEquals(Optional.of(""), rules.get(2).os().orElseThrow().arch());
        assertEquals(Optional.of("   "), rules.get(3).os().orElseThrow().version());
    }

    @Test
    void parseWithDiagnosticsReportsMalformedAndUnknownConditions() {
        var result = parser.parseWithDiagnostics("""
                {"id":"x","libraries":[{"name":"a","rules":[
                  {"action":"allow","os":{"name":"linux","abi":"musl"}},
                  {"action":"allow","os":{"name":"   "}},
                  {"action":"allow","features":[]},
                  {"action":"allow"}
                ]}]}
                """);

        assertTrue(result.hasWarnings());
        assertEquals(3, result.warnings().size());
        assertEquals(ParseWarningCode.UNKNOWN_RULE_CONDITION, result.warnings().get(0).code());
        assertTrue(result.warnings().get(0).message().contains("abi"));
        assertEquals(ParseWarningCode.MALFORMED_RULE, result.warnings().get(1).code());
        assertEquals(ParseWarningCode.MALFORMED_RULE, result.warnings().get(2).code());
        assertEquals(4, result.fragment().libraries().getFirst().rules().size());
        assertFalse(result.fragment().libraries().getFirst().rules().get(3).malformed());
    }

    @Test
    void parseWithDiagnosticsReportsWarningsInsideConditionalArguments() {
        var result = parser.parseWithDiagnostics("""
                {"id":"x","arguments":{"game":[{"rules":[
                  {"action":"allow","os":{"name":"linux","abi":"musl"}}
                ],"value":"--demo"}]}}
                """);

        assertEquals(1, result.warnings().size());
        assertEquals(ParseWarningCode.UNKNOWN_RULE_CONDITION, result.warnings().get(0).code());
    }

    @Test
    void parseWithDiagnosticsReportsNonObjectRuleElement() {
        var result = parser.parseWithDiagnostics("""
                {"id":"x","libraries":[{"name":"a","rules":["allow"]}]}
                """);

        assertEquals(1, result.warnings().size());
        assertEquals(ParseWarningCode.MALFORMED_RULE, result.warnings().get(0).code());
        assertEquals(RuleAction.DISALLOW,
                result.fragment().libraries().getFirst().rules().getFirst().action());
    }

    @Test
    void parseWithDiagnosticsReportsMalformedVersionRegex() {
        var result = parser.parseWithDiagnostics("""
                {"id":"x","libraries":[{"name":"a","rules":[
                  {"action":"disallow","os":{"version":"[a-z"}}
                ]}]}
                """);

        assertEquals(1, result.warnings().size());
        assertEquals(ParseWarningCode.MALFORMED_RULE, result.warnings().get(0).code());
        assertTrue(result.warnings().get(0).message().contains("[a-z"));

        var rule = result.fragment().libraries().getFirst().rules().getFirst();
        assertTrue(rule.malformed());
        assertEquals(Optional.of("[a-z"), rule.os().orElseThrow().version());
    }

    @Test
    void toleratesArtifactWithoutHashAndSize() {
        var artifact = parser.parse("""
                {"id":"x","libraries":[{"name":"a","downloads":{"artifact":{"path":"a.jar"}}}]}
                """).libraries().getFirst().artifact();
        assertNotNull(artifact);
        assertEquals("a.jar", artifact.path());
        assertNull(artifact.sha1());
        assertNull(artifact.size());
        assertNull(artifact.url());
    }

    @Test
    void sizeEdgeCasesBecomeUnknown() {
        assertEquals(128L, sizeOf("128"));
        assertEquals(128L, sizeOf("\"128\""));
        assertNull(sizeOf("1.5"));
        assertNull(sizeOf("9223372036854775808"));
        assertNull(sizeOf("null"));
    }

    private Long sizeOf(String sizeJson) {
        var artifact = parser.parse("""
                {"id":"x","libraries":[{"name":"a","downloads":{"artifact":{
                  "path":"a.jar","sha1":"s","size":%s,"url":"u"
                }}}]}
                """.formatted(sizeJson)).libraries().getFirst().artifact();
        return artifact.size();
    }

    @Test
    void bomPrefixedBytesParse() {
        byte[] bytes = "\uFEFF{\"id\":\"x\"}".getBytes(StandardCharsets.UTF_8);
        assertEquals("x", parser.parse(bytes).id());
    }

    @Test
    void rejectsMissingId() {
        var error = assertThrows(ManifestParseException.class,
                () -> parser.parse("{\"mainClass\":\"x\"}"));
        assertTrue(error.getMessage().contains("id") || String.valueOf(error.getCause()).contains("id"));
    }

    @Test
    void unknownTopLevelFieldsAreIgnored() {
        var fragment = parser.parse("""
                {"id":"x","assetIndex":{"id":"legacy"},"downloads":{"client":{"sha1":"s"}},
                 "javaVersion":{"majorVersion":8},"logging":{},"type":"release"}
                """);
        assertEquals("x", fragment.id());
    }

    @Test
    void collectionsAreUnmodifiable() {
        var fragment = parser.parse("""
                {"id":"x","minecraftArguments":"--a --b",
                 "libraries":[{"name":"a","rules":[{"action":"allow"}],"natives":{"windows":"natives-windows"}}]}
                """);

        assertThrows(UnsupportedOperationException.class, () -> fragment.libraries().add(null));
        assertThrows(UnsupportedOperationException.class, () -> fragment.gameArguments().add(null));
        assertThrows(UnsupportedOperationException.class, () -> fragment.libraries().getFirst().rules().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> fragment.libraries().getFirst().natives().put("linux", "natives-linux"));
    }
}

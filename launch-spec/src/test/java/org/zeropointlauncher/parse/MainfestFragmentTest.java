package org.zeropointlauncher.parse;

import org.junit.jupiter.api.Test;
import org.zeropointlauncher.launch.model.LaunchArgument;
import org.zeropointlauncher.launch.model.Rule;
import org.zeropointlauncher.launch.parse.ManifestFragmentParser;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MainfestFragmentTest {
    private final ManifestFragmentParser parser = new ManifestFragmentParser();

    //测试mainClass缺省
    @Test
    void readChildWithNoMainClass(){
        var fragment = parser.parse("""
                {"id":"demo", "inheritsFrom":"base_demo"}
                """);
        assertEquals("demo", fragment.id());
        assertEquals(Optional.of("base_demo"), fragment.inheritsFrom());
        assertTrue(fragment.mainClass().isEmpty());
        assertTrue(fragment.arguments().isEmpty());
    }

    //验证game和jvm的参数按原顺序解析，参数中的空格和占位符均保留
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
                """).arguments().orElseThrow();

        assertEquals(List.of(
                new LaunchArgument.Direct("--username"),
                new LaunchArgument.Direct("Demo Player")
        ), arguments.game());

        assertEquals(List.of(
                new LaunchArgument.Direct("-cp"),
                new LaunchArgument.Direct("${classpath}")
        ), arguments.jvm());
    }

    //测试用例，用一个比较复杂的json丢给parser测试
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
                """).arguments().orElseThrow();//解析不到arguments，即返回Optional.empty()时报错

        var featureRule = new Rule(Rule.Action.ALLOW, Optional.empty(), Map.of("is_demo_user", false));
        assertEquals(List.of(new LaunchArgument.Conditional(
                List.of(featureRule), List.of("--username", "Demo Player")
        )), arguments.game());

        /*
        这里的测试目标：
        1. 验证arguments.jvm中既有Direct又有Conditional
        2. 验证多条rules的先后顺序得到完整保留
        3. 验证json中单个字符串value都会被统一包装成 List<String> 类型
         */
        var allow = new Rule(Rule.Action.ALLOW, Optional.empty(), Map.of());
        var os = new Rule.OsRule(Optional.of("windows"), Optional.of("x86"), Optional.of("^10"));
        var disallow = new Rule(Rule.Action.DISALLOW, Optional.of(os), Map.of());
        assertEquals(List.of(
                new LaunchArgument.Direct("-ea"),
                new LaunchArgument.Conditional(List.of(allow, disallow), List.of("-Ddemo=true"))
        ), arguments.jvm());
    }

    //测试拦截非法数据
    @Test
    void rejectMainClass(){
        assertThrows(IllegalArgumentException.class, ()-> parser.parse("""
                {"id":"demo", "mainClass":42}
                """));
    }
}

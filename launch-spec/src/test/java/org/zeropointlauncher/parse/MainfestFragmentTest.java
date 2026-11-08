package org.zeropointlauncher.parse;

import org.junit.jupiter.api.Test;
import org.zeropointlauncher.launch.model.LaunchArgument;
import org.zeropointlauncher.launch.model.Library;
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
        assertTrue(fragment.libraries().isEmpty());
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

    /**
     * 验证 libraries 的依赖项解析，包含如下项：
     * 1. 保留依赖列表的原顺序
     * 2. 覆盖三种典型形态：自定义仓库项（Maven url）、带规则的本地库（natives/classifiers/extract）以及标准单产物包（artifact）
     * 3. 完整校验下载（path/sha1/size/url）及选填字段的空安全性
     */
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

        //验证解析后依赖项的顺序与原 JSON 严格一致
        assertEquals(List.of("example:loader:1", "example:platform:2", "example:client:3:natives-linux"),
                libraries.stream().map(Library::name).toList());

        var loader = libraries.getFirst();
        //第一项 loader ：只提供自定义仓库 url，artifact 为空
        assertEquals(Optional.of("https://example.test/maven/"), loader.repositoryUrl());
        assertTrue(loader.artifact().isEmpty());

        //第二项 platform ：复合本地库，包含系统映射、分类器产物、解压排除和 rules
        var platform = libraries.get(1);
        assertTrue(platform.artifact().isEmpty());
        assertEquals(Map.of("windows", "natives-windows-${arch}"), platform.natives());
        assertEquals(Map.of("natives-windows-64", new Library.DownloadArtifact(
                "example/platform/2/platform-2-natives-windows-64.jar",
                "0000000000000000000000000000000000000000", 128, "https://example.test/platform-native.jar"
        )), platform.classifiers());
        assertEquals(List.of("META-INF/"), platform.extractExcludes());
        assertEquals(List.of(
                new Rule(Rule.Action.ALLOW, Optional.empty(), Map.of()),
                new Rule(Rule.Action.DISALLOW, Optional.of(
                        new Rule.OsRule(Optional.of("osx"), Optional.empty(), Optional.empty())), Map.of())
        ), platform.rules());

        //第三项 client ：标准依赖项，能准确提取主 artifact 的下载四要素
        assertEquals(new Library.DownloadArtifact(
                "example/client/3/client-3-natives-linux.jar",
                "1111111111111111111111111111111111111111", 256, "https://example.test/client.jar"
        ), libraries.getLast().artifact().orElseThrow());
    }

    //测试拦截非法数据
    @Test
    void rejectMainClass(){
        assertThrows(IllegalArgumentException.class, ()-> parser.parse("""
                {"id":"demo", "mainClass":42}
                """));
    }
}

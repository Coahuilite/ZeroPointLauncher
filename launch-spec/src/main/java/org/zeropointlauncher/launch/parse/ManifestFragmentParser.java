//将json处理为java对象
package org.zeropointlauncher.launch.parse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.zeropointlauncher.launch.model.LaunchArgument;
import org.zeropointlauncher.launch.model.Library;
import org.zeropointlauncher.launch.model.Library.DownloadArtifact;
import org.zeropointlauncher.launch.model.ManifestFragment;
import org.zeropointlauncher.launch.model.ManifestFragment.ArgumentsBlock;
import org.zeropointlauncher.launch.model.Rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ManifestFragmentParser {
    private final ObjectMapper mapper = new ObjectMapper();

    public ManifestFragment parse(String json){
        JsonNode sth;

        //校验JSON语法合法性
        try{
            sth = mapper.readTree(json);
        }catch (JsonProcessingException e){
            throw new IllegalArgumentException("解析版本JSON错误", e);
        }

        //校验JSON是否为{}对象，而非其他类型，如数组
        if(!sth.isObject()){
            throw new IllegalArgumentException("版本清单必须是合法的JSON对象");
        }

        //提取json内"id"字段
        String id = readText(sth, "id")
                .orElseThrow(() -> new IllegalArgumentException("清单缺少 id"));

        return new ManifestFragment(
                id,
                readText(sth, "inheritsFrom"),
                readText(sth, "mainClass"),
                readString(sth, "minecraftArguments"),
                readArguments(sth),
                readLibraries(sth)
        );
    }

    //辅助字段提取方法
    private static Optional<String> readText(JsonNode object, String name){
        var value = readString(object, name);
        if (value.isPresent() && value.get().isBlank()) {
            throw new IllegalArgumentException(name + " 必须是非空白字符串");
        }
        return value;
    }

    private static Optional<String> readString(JsonNode object, String name) {
        JsonNode field = object.get(name);
        if (field == null) {
            return Optional.empty();
        }

        String value = field.textValue();
        if (value == null) {
            throw new IllegalArgumentException(name + " 必须是字符串");
        }
        return Optional.of(value);
    }

    /**
     * 解析版本清单中的 libraries 依赖项列表。
     * <p>
     * 若字段缺省则返回只读空列表；若存在则强制校验其必须为 JSON 数组，并逐项转换为不可变 Library 实体
     *
     * @param root 版本清单 JSON
     * @return 完整解析后的依赖库列表
     */
    private static List<Library> readLibraries(JsonNode root) {
        var array = root.get("libraries");
        if (array == null) {
            return List.of();
        }
        if (!array.isArray()) {
            throw new IllegalArgumentException("libraries 必须是数组");
        }

        var libraries = new ArrayList<Library>();
        for (var node : array) {
            libraries.add(readLibrary(node));//将数组的每个json元素逐项解析并返回收集为List<>
        }
        return libraries;
    }

    /**
     * 解析单个依赖项节点，提取 Maven 坐标、产物下载信息、多平台分类器及提取规则
     * @param node 单个依赖库的 JSON 的节点
     * @return 组装完成的 Library 实体
     */
    private static Library readLibrary(JsonNode node) {
        String name = requiredText(node, "name");
        var downloads = optionalObject(node, "downloads");
        var artifact = Optional.ofNullable(downloads.get("artifact"))
                .map(ManifestFragmentParser::readDownloadArtifact);

        var classifiers = new HashMap<String, DownloadArtifact>();
        for (var entry : optionalObject(downloads, "classifiers").properties()) {
            classifiers.put(entry.getKey(), readDownloadArtifact(entry.getValue()));
        }

        var nativeMappings = optionalObject(node, "natives");
        var natives = new HashMap<String, String>();
        for (var entry : nativeMappings.properties()) {
            natives.put(entry.getKey(), requiredText(nativeMappings, entry.getKey()));
        }

        var rules = node.get("rules");
        var exclude = optionalObject(node, "extract").get("exclude");
        if (exclude != null && !exclude.isArray()) {
            throw new IllegalArgumentException("extract.exclude 必须是字符串数组");
        }
        return new Library(
                name,
                readText(node, "url"),
                artifact,
                classifiers,
                natives,
                rules == null ? List.of() : readRules(rules),
                exclude == null ? List.of() : readStringElements(exclude, "extract.exclude")
        );
    }

    /**
     * 解析可下载构件元数据，包含相对路径、哈希校验码、文件大小与下载 URL
     * <p>
     * 严格校验 size 必须为 long 范围内的整数
     * @param node 构件下载信息的 JSON 节点
     * @return DownloadArtifact 实体
     */
    private static DownloadArtifact readDownloadArtifact(JsonNode node) {
        String path = requiredText(node, "path");
        String sha1 = requiredText(node, "sha1");
        var size = node.required("size");
        if (!size.isIntegralNumber() || !size.canConvertToLong()) {
            throw new IllegalArgumentException("制品 size 必须是 long 范围内的整数");
        }
        return new DownloadArtifact(path, sha1, size.longValue(), requiredText(node, "url"));
    }

    /**
     * 安全提取指定的必填字符串字段。
     * @return 提取出的非空字符串内容
     */
    private static String requiredText(JsonNode object, String name) {
        return readText(object, name)
                .orElseThrow(() -> new IllegalArgumentException("清单缺少 " + name));
    }

    /**
     * 安全提取选填的 JSON 子对象
     * <p>
     * 若字段不存在则返回空节点，若存在但非JSON则抛出异常
     * @param parent 父JSON节点
     * @param name   目标名称
     * @return 存在的对象节点或缺失节点
     */
    private static JsonNode optionalObject(JsonNode parent, String name) {
        var node = parent.path(name);//节点不存在时MissingNode而非Null
        if (!node.isMissingNode() && !node.isObject()) {
            throw new IllegalArgumentException(name + " 必须是 JSON 对象");
        }
        return node;
    }

    /**
     * 解析节点下的 arguments 启动参数
     */
    private static Optional<ArgumentsBlock> readArguments(JsonNode root){
        var block = root.get("arguments");
        if(block == null){
            return Optional.empty();
        }
        if(!block.isObject()){
            throw new IllegalArgumentException("arguments 必须为JSON对象");
        }

        return Optional.of(new ArgumentsBlock(
                readArgumentsList(block, "game"),
                readArgumentsList(block, "jvm")
        ));
    }

    /**
     * 顺序解析 arguments 下的具体参数列表（game 或 jvm）
     * 严格保留 JSON 原来的顺序，按元素类型分别封装为直接参数 (Direct) 或带规则的条件参数 (Conditional)
     */
    private static List<LaunchArgument> readArgumentsList(JsonNode block, String name){
        var array = block.get(name);
        if(array == null){
            return List.of();
        }
        if(!array.isArray()){
            throw new IllegalArgumentException("arguments." + name + " 必须为数组");
        }

        var result = new ArrayList<LaunchArgument>();
        for (var item : array) {
            if (item.isTextual()) {
                result.add(new LaunchArgument.Direct(item.textValue()));
            } else if (item.isObject()) {
                result.add(new LaunchArgument.Conditional(
                        readRules(item.required("rules")),
                        readConditionalValues(item.required("value"))
                ));
            } else {
                throw new IllegalArgumentException(
                        "arguments." + name + " 的元素必须是字符串或条件对象");
            }
        }
        return result;
    }

    /**
     * 提取条件参数的 value 字段
     * 将 Mojang 混用的“单个字符串”与“字符串数组”统一解析为结构一致的 List<String>，并严格校验数组元素均为字符串
     */
    private static List<String> readConditionalValues(JsonNode value) {
        if (value.isTextual()) {
            return List.of(value.textValue());
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("条件参数的 value 必须是字符串或字符串数组");
        }

        return readStringElements(value, "条件参数的 value");
    }

    private static List<String> readStringElements(JsonNode array, String name) {
        var values = new ArrayList<String>();
        for (var item : array) {
            String text = item.textValue();
            if (text == null) {
                throw new IllegalArgumentException(name + " 数组只能包含字符串");
            }
            values.add(text);
        }
        return values;
    }

    /**
     * 解析条件参数或依赖库的 rules 规则数组
     * 校验 action 必须为 allow 或 disallow，并将 os 要求与 features 转换为不可变rules实体
     */
    private static List<Rule> readRules(JsonNode array) {
        if (!array.isArray()) {
            throw new IllegalArgumentException("rules 必须是数组");
        }

        var rules = new ArrayList<Rule>();
        for (var rule : array) {
            var action = switch (rule.required("action").textValue()) {
                case "allow" -> Rule.Action.ALLOW;
                case "disallow" -> Rule.Action.DISALLOW;
                case null, default -> throw new IllegalArgumentException("规则 action 必须是 allow 或 disallow");
            };//将状态通过switch返回给var变量，同时带有统一报错
            var os = Optional.ofNullable(rule.get("os")).map(ManifestFragmentParser::readOsRule);//把存在或者滚木状态的os字段转换为实体
            rules.add(new Rule(action, os, readFeatures(rule.get("features"))));
        }
        return rules;
    }

    /**
     * 解析rules中的 os 约束
     * 提取操作系统名称 (name)、架构 (arch) 及版本 (version)，缺省用 Optional.empty() 防止Null
     */
    private static Rule.OsRule readOsRule(JsonNode os) {
        if (!os.isObject()) {
            throw new IllegalArgumentException("规则 os 必须是 JSON 对象");
        }
        return new Rule.OsRule(readText(os, "name"), readText(os, "arch"), readText(os, "version"));
    }

    private static Map<String, Boolean> readFeatures(JsonNode node) {
        if (node == null) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("规则 features 必须是 JSON 对象");
        }

        var features = new HashMap<String, Boolean>();
        for (var entry : node.properties()) {
            if (!entry.getValue().isBoolean()) {
                throw new IllegalArgumentException("features." + entry.getKey() + " 必须是布尔值");
            }
            features.put(entry.getKey(), entry.getValue().booleanValue());
        }
        return features;
    }
}

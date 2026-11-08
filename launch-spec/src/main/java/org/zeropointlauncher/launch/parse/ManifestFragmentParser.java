//将json处理为java对象
package org.zeropointlauncher.launch.parse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.zeropointlauncher.launch.model.LaunchArgument;
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
                readArguments(sth)
        );
    }

    //辅助字段提取方法
    private static Optional<String> readText(JsonNode object, String name){
        JsonNode field = object.get(name);
        if(field == null){
            return Optional.empty();
        }//字段为空返回空的容器而非Null

        String value = field.textValue();
        if(value == null || value.isBlank()){
            throw new IllegalArgumentException(name + " 必须是非空白字符串");
        }//如果value是非字符串，比如"mainclass" = 42，textValue必定返回null；同时防止value为空的字符串
        return Optional.of(value);
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

        var values = new ArrayList<String>();
        for (var item : value) {
            String text = item.textValue();
            if (text == null) {
                throw new IllegalArgumentException("条件参数的 value 数组只能包含字符串");
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

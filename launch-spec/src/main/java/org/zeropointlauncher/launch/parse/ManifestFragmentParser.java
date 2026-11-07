//将json处理为java对象
package org.zeropointlauncher.launch.parse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.zeropointlauncher.launch.model.ManifestFragment;

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
                readText(sth, "mainClass")
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
            throw new IllegalArgumentException(name + "必须为空白字符串");
        }//如果value是非字符串，比如"mainclass" = 42，textValue必定返回null；同时防止value为空的字符串
        return Optional.of(value);
    }
}

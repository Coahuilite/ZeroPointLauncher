package org.zeropointlauncher.launch.parse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.zeropointlauncher.launch.json.JsonSupport;
import org.zeropointlauncher.launch.model.LaunchArgument;
import org.zeropointlauncher.launch.model.ManifestFragment;

import java.io.IOException;

public final class ManifestFragmentParser {
    private static final ObjectMapper DEFAULT_MAPPER = configure(new ObjectMapper());

    private final ObjectMapper mapper;

    public ManifestFragmentParser() {
        this.mapper = DEFAULT_MAPPER;
    }

    public ManifestFragmentParser(ObjectMapper mapper) {
        this.mapper = configure(mapper);
    }

    public ManifestFragment parse(String json) {
        if (json == null) {
            throw new ManifestParseException("版本清单 JSON 为空");
        }
        try {
            return mapper.readValue(json, ManifestFragment.class);
        } catch (IOException e) {
            throw new ManifestParseException("解析版本清单失败: " + e.getMessage(), e);
        }
    }

    /** 文件入口优先使用字节数组，Jackson 的字节解析器会处理 UTF-8 BOM。 */
    public ManifestFragment parse(byte[] json) {
        if (json == null) {
            throw new ManifestParseException("版本清单 JSON 为空");
        }
        try {
            return mapper.readValue(json, ManifestFragment.class);
        } catch (IOException e) {
            throw new ManifestParseException("解析版本清单失败: " + e.getMessage(), e);
        }
    }

    private static ObjectMapper configure(ObjectMapper base) {
        var mapper = base.copy()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        var module = new SimpleModule();
        module.addDeserializer(LaunchArgument.class, new JsonSupport.LaunchArgumentDeserializer());
        mapper.registerModule(module);
        return mapper;
    }
}

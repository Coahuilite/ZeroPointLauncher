package org.zeropointlauncher.launch.parse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.zeropointlauncher.launch.json.JsonSupport;
import org.zeropointlauncher.launch.model.LaunchArgument;
import org.zeropointlauncher.launch.model.ManifestFragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        return read(json, null);
    }

    /** 文件入口优先使用字节数组，Jackson 的字节解析器会处理 UTF-8 BOM。 */
    public ManifestFragment parse(byte[] json) {
        return read(json, null);
    }

    /**
     * 读取清单并暴露边界告警。告警不会进入模型或求值器；普通 {@link #parse} 会丢弃它们。
     */
    public ManifestParseResult parseWithDiagnostics(String json) {
        var warnings = new ArrayList<ParseWarning>();
        return new ManifestParseResult(read(json, warnings), warnings);
    }

    public ManifestParseResult parseWithDiagnostics(byte[] json) {
        var warnings = new ArrayList<ParseWarning>();
        return new ManifestParseResult(read(json, warnings), warnings);
    }

    private ManifestFragment read(String json, List<ParseWarning> warnings) {
        if (json == null) {
            throw new ManifestParseException("版本清单 JSON 为空");
        }
        try {
            return reader(warnings).readValue(json);
        } catch (IOException e) {
            throw new ManifestParseException("解析版本清单失败: " + e.getMessage(), e);
        }
    }

    private ManifestFragment read(byte[] json, List<ParseWarning> warnings) {
        if (json == null) {
            throw new ManifestParseException("版本清单 JSON 为空");
        }
        try {
            return reader(warnings).readValue(json);
        } catch (IOException e) {
            throw new ManifestParseException("解析版本清单失败: " + e.getMessage(), e);
        }
    }

    private ObjectReader reader(List<ParseWarning> warnings) {
        var reader = mapper.readerFor(ManifestFragment.class);
        return warnings == null
                ? reader
                : reader.withAttribute(JsonSupport.WARNINGS_ATTRIBUTE, warnings);
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

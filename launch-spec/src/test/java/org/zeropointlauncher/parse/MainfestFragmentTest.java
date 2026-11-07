package org.zeropointlauncher.parse;

import org.junit.jupiter.api.Test;
import org.zeropointlauncher.launch.parse.ManifestFragmentParser;

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
    }

    //测试拦截非法数据
    @Test
    void rejectMainClass(){
        assertThrows(IllegalArgumentException.class, ()-> parser.parse("""
                {"id":"demo", "mainClass":42}
                """));
    }
}

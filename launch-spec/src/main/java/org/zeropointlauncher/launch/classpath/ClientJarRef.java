package org.zeropointlauncher.launch.classpath;

import java.util.Objects;

/**
 * 调用方显式指定的客户端 JAR 逻辑引用。
 * <p>
 * 只携带相对路径，不检查文件是否存在；由宿主在准备阶段绑定到实际工作区。
 */
public record ClientJarRef(String relativePath) {
    public ClientJarRef {
        Objects.requireNonNull(relativePath, "relativePath");
        relativePath = relativePath.strip();
        if (relativePath.isEmpty()) {
            throw new IllegalArgumentException("客户端 JAR 相对路径不能为空");
        }
    }

    /** 兼容以 path 命名的调用方；与 {@link #relativePath()} 等价。 */
    public String path() {
        return relativePath;
    }
}

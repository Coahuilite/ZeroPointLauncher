package org.zeropointlauncher.launch.classpath;

import org.zeropointlauncher.launch.model.Library;
import org.zeropointlauncher.launch.resolve.SelectedLaunchInputs;

import java.util.ArrayList;
import java.util.Objects;

/**
 * 将规则筛选后的库声明转换为有序 classpath 逻辑计划。
 * <p>
 * 纯函数：不检查文件、不解析系统环境、不拼接平台分隔符。用途只由下载字段决定，
 * 不根据 classifier 名称猜测；客户端 JAR 始终追加在全部类库之后。
 */
public final class ClasspathAssembler {
    private ClasspathAssembler() {
    }

    /**
     * 按声明顺序装配 classpath。
     * <p>
     * 判定顺序：
     * <ol>
     *   <li>{@code downloads.artifact} 存在：直接使用其 {@code path}，即使坐标带 native classifier；</li>
     *   <li>无 {@code artifact}，但存在 {@code natives} 或 {@code downloads.classifiers}：属于解压流，跳过；</li>
     *   <li>其余条目：按 {@code name} 推导标准 Maven 相对路径。</li>
     * </ol>
     *
     * @param inputs    规则筛选后的输入，保留原始库顺序与重复项
     * @param clientJar 调用方指定的客户端 JAR，必须最后追加
     * @return 不可变的有序 classpath 计划
     * @throws IllegalArgumentException 回退条目缺少可解析的 Maven 坐标
     */
    public static ClasspathPlan assemble(SelectedLaunchInputs inputs, ClientJarRef clientJar) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(clientJar, "clientJar");

        var entries = new ArrayList<String>();
        for (Library library : inputs.libraries()) {
            if (library == null) {
                continue;
            }

            String entry = classpathEntry(library);
            if (entry != null) {
                entries.add(entry);
            }
        }
        entries.add(clientJar.relativePath());

        return new ClasspathPlan(entries);
    }

    private static String classpathEntry(Library library) {
        if (library.artifact() != null) {
            return library.artifact().path();
        }

        if (!library.natives().isEmpty() || !library.classifiers().isEmpty()) {
            return null;
        }

        try {
            return MavenCoordinate.parse(library.name()).toRelativePath();
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "无法从库坐标推导 classpath 路径: " + library.name(), error);
        }
    }
}

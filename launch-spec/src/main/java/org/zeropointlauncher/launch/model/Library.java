package org.zeropointlauncher.launch.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minecraft 依赖库实体定义。
 * <p>
 * 封装游戏运行所需的 Java 类库（Jar）或本地动态链接库（Natives）的元数据与下载信息。
 * 紧凑构造函数对所有集合字段执行 防御性 拷贝
 *
 * @param name            Maven 规范坐标（如 "org.lwjgl:lwjgl:3.3.1"）
 * @param repositoryUrl   自定义 Maven 远程仓库基地址（选填，常用于第三方模组加载器或自定义补丁库）
 * @param artifact        主产物 Jar 包的下载描述实体（选填，若仅提供特定平台 Natives 分类包时可为空）
 * @param classifiers     不同平台或环境的分类产物映射表（"natives-windows"）
 * @param natives         操作系统名称到具体分类器名称的映射规则（"windows" -> "natives-windows"）
 * @param rules           依赖项的启用规则列表，基于宿主环境判定是否需要加载该库（复用统一 Rule 模型）
 * @param extractExcludes 本地库解压提取到运行目录时需排除的文件路径列表（如排除 "META-INF/"）
 */
public record Library(
        String name,
        Optional<String> repositoryUrl,
        Optional<DownloadArtifact> artifact,
        Map<String, DownloadArtifact> classifiers,
        Map<String, String> natives,
        List<Rule> rules,
        List<String> extractExcludes
) {
    public Library {
        classifiers = Map.copyOf(classifiers);
        natives = Map.copyOf(natives);
        rules = List.copyOf(rules);
        extractExcludes = List.copyOf(extractExcludes);
    }

    /**
     * 单个可下载构件的描述实体，提供安全下载及完整性校验所需的四要素元数据。
     * @param path 本地存储的相对文件路径（如 "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1.jar"）
     * @param sha1 文件的 SHA-1 哈希校验值，用于检测网络损坏或篡改
     * @param size 文件体积的字节大小，用于校验与 UI 下载进度统计
     * @param url  远程文件的官方或镜像直链下载地址
     */
    public record DownloadArtifact(String path, String sha1, long size, String url) {}
}

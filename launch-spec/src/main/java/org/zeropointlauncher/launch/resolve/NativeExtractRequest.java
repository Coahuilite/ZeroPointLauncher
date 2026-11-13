package org.zeropointlauncher.launch.resolve;

import org.zeropointlauncher.launch.model.Library;

import java.util.List;
import java.util.Optional;

/**
 * 单条旧版 native 解压请求。artifact 为空时，后续步骤可基于库坐标推导 Maven 制品。
 */
public record NativeExtractRequest(
        Library library,
        String classifier,
        Optional<Library.DownloadArtifact> artifact,
        List<String> extractExcludes
) {
    public NativeExtractRequest {
        artifact = artifact == null ? Optional.empty() : artifact;
        extractExcludes = extractExcludes == null ? List.of() : List.copyOf(extractExcludes);
    }
}

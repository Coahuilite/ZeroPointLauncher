package org.zeropointlauncher.launch.resolve;

import org.zeropointlauncher.launch.model.ManifestFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ManifestInheritance {
    /** 返回 parent -> child 顺序的不可变链。 */
    public List<ManifestFragment> resolveChain(
            String targetID,
            Map<String, ManifestFragment> manifestsByID
    ) {
        Objects.requireNonNull(targetID, "targetID");
        Objects.requireNonNull(manifestsByID, "manifestsByID");

        var childToRoot = new ArrayList<ManifestFragment>();
        var visited = new HashSet<String>();
        String currentID = targetID;

        while (true) {
            if (!visited.add(currentID)) {
                throw new IllegalArgumentException("清单继承陷入成环: " + currentID);
            }

            var fragment = manifestsByID.get(currentID);
            if (fragment == null) {
                throw new IllegalArgumentException("找不到清单: " + currentID);
            }
            childToRoot.add(fragment);

            var parentID = fragment.inheritsFrom();
            if (parentID == null) {
                return List.copyOf(childToRoot.reversed());
            }
            currentID = parentID;
        }
    }

    public String resolveMainClass(
            String tarID,
            Map<String, ManifestFragment> manifestsByID
    ) {
        String effectiveMainClass = null;

        for (var fragment : resolveChain(tarID, manifestsByID)) {
            var declaredMainClass = fragment.mainClass();
            if (declaredMainClass != null) {
                effectiveMainClass = declaredMainClass;
            }
        }

        if (effectiveMainClass == null) {
            throw new IllegalArgumentException("继承后缺少 mainClass: " + tarID);
        }
        return effectiveMainClass;
    }
}

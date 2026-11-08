package org.zeropointlauncher.launch.resolve;

import org.zeropointlauncher.launch.model.ManifestFragment;

import java.util.*;

public final class ManifestInheritance {
    //理清顺序关系
    public List<ManifestFragment> resolveChain(
            String targetID,
            Map<String, ManifestFragment> manifestsByID
    ){
        //临时列表 用于存放 顶层 -> 底层 的轨迹
        var childToRoot = new ArrayList<ManifestFragment>();
        var visited = new HashSet<String>();
        String currentID = targetID;//当前正在调查的版本ID

        while (true){
            //死循环排除
            if(!visited.add(currentID)){
                throw new IllegalArgumentException("清单继承陷入成环" + currentID);
            }

            var fragment = manifestsByID.get(currentID);//查currentID具体内容
            if(fragment == null){
                throw new IllegalArgumentException("找不到清单" + currentID);
            }
            childToRoot.add(fragment);

            var parentID = fragment.inheritsFrom();
            if(parentID.isEmpty()){
                return List.copyOf(childToRoot.reversed());
            }
            currentID = parentID.get();
        }
    }

    //通过resolveChain()方法给出的结果，敲定最终的MainClass
    public String resolveMainClass(
            String tarID,
            Map<String, ManifestFragment> manifestsByID
    ){
        Optional<String> effectiveMainClass = Optional.empty();

        //顺着继承链由底层到顶层逐步覆盖主类
        for(var fragment : resolveChain(tarID, manifestsByID)){
            var declaredMainClass = fragment.mainClass();
            if(declaredMainClass.isPresent()){
                effectiveMainClass = declaredMainClass;
            }
        }

        return effectiveMainClass.orElseThrow(
                () -> new IllegalArgumentException("继承后缺少mainClass：" + tarID)
        );
    }
}

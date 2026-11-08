package org.zeropointlauncher.launch.model;

import java.util.List;
import java.util.Optional;

public record ManifestFragment(
        String id,
        Optional<String> inheritsFrom,
        Optional<String> mainClass,
        Optional<ArgumentsBlock> arguments,
        List<Library> libraries
) {
    //确保libraries转成不可变的List，处于只读状态
    public ManifestFragment {
        libraries = List.copyOf(libraries);
    }

    public record ArgumentsBlock(
            List<LaunchArgument> game,
            List<LaunchArgument> jvm
    ){
        public ArgumentsBlock {
            game = List.copyOf(game);
            jvm = List.copyOf(jvm);
        }
    }
}

package org.zeropointlauncher.launch.model;

import java.util.List;
import java.util.Optional;

public record ManifestFragment(
        String id,
        Optional<String> inheritsFrom,
        Optional<String> mainClass,
        Optional<ArgumentsBlock> arguments
) {
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

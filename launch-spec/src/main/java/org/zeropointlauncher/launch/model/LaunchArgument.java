package org.zeropointlauncher.launch.model;

import java.util.List;

public sealed interface LaunchArgument {
    //启动参数只能由两类：Direct直接参数 Conditional条件参数
    record Direct(String value) implements LaunchArgument {}

    record Conditional(List<Rule> rules, List<String> values) implements LaunchArgument {
        public Conditional {
            //利用List防止参数被修改
            rules = List.copyOf(rules);
            values = List.copyOf(values);
        }
    }
}

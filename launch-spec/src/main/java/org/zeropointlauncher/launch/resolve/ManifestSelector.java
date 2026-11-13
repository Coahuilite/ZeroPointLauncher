package org.zeropointlauncher.launch.resolve;

import org.zeropointlauncher.launch.model.LaunchArgument;
import org.zeropointlauncher.launch.model.Library;
import org.zeropointlauncher.launch.model.ManifestFragment;
import org.zeropointlauncher.launch.model.RuleContext;
import org.zeropointlauncher.launch.rule.RuleEvaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 根据目标环境筛选依赖库并展开启动参数。
 * <p>
 * 纯函数：不读取系统属性、不访问磁盘、不记录日志，也不修改传入的清单对象。
 */
public final class ManifestSelector {
    private ManifestSelector() {
    }

    /**
     * 求值所有库规则与条件参数。
     *
     * @param fragment 已解析的版本清单
     * @param context  与规则求值器共享的目标环境事实
     * @return 通过规则的原始 {@link Library} 对象与按声明顺序展开的 token
     */
    public static SelectedLaunchInputs select(ManifestFragment fragment, RuleContext context) {
        Objects.requireNonNull(fragment, "fragment");

        var libraries = new ArrayList<Library>();
        for (Library library : fragment.libraries()) {
            if (library != null && RuleEvaluator.evaluate(library.rules(), context)) {
                libraries.add(library);
            }
        }

        return new SelectedLaunchInputs(
                libraries,
                expand(fragment.jvmArguments(), context),
                expand(fragment.gameArguments(), context)
        );
    }

    private static List<String> expand(List<LaunchArgument> arguments, RuleContext context) {
        var tokens = new ArrayList<String>();
        for (LaunchArgument argument : arguments) {
            switch (argument) {
                case LaunchArgument.Direct direct -> tokens.add(direct.value());
                case LaunchArgument.Conditional conditional -> {
                    if (RuleEvaluator.evaluate(conditional.rules(), context)) {
                        tokens.addAll(conditional.value());
                    }
                }
            }
        }
        return tokens;
    }
}

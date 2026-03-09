package nc;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 指令执行类，用于处理 /soulleash 等主命令
 */
public class Executors implements CommandExecutor, TabCompleter {

    private SoulLeash main;

    // 构造方法，接受 SoulLeash 实例
    public Executors(SoulLeash main) {
        this.main = main;
    }
    /**
     * 处理命令逻辑
     * @param sender 命令发送者
     * @param command 命令对象
     * @param label 命令标签（如 "soulleash"）
     * @param args 命令参数（如 ["reload"]）
     * @return true 表示成功执行命令，false 表示失败或未识别
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Lang.send(sender, "command.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            main.reloadConfig();
            Lang.reload();
            Lang.send(sender, "command.reload.success", "lang", Lang.getCurrentLanguage());
            return true;
        }

        if (args[0].equalsIgnoreCase("lang")) {
            if (args.length == 1) {
                Lang.send(sender, "command.lang.current", "lang", Lang.getCurrentLanguage());
                return true;
            }

            String requested = args[1];
            main.getConfig().set("language", requested);
            main.saveConfig();
            Lang.reload();
            Lang.send(sender, "command.lang.set", "lang", Lang.getCurrentLanguage());
            return true;
        }

        Lang.send(sender, "command.usage");
        return true;
    }

    /**
     * 自动补全命令参数
     * @param sender 命令发送者
     * @param command 命令对象
     * @param label 命令标签
     * @param args 当前输入的参数
     * @return 补全建议列表
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList("reload", "lang"), completions);
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("lang")) {
            StringUtil.copyPartialMatches(args[1], Arrays.asList("en_US", "de_DE"), completions);
        }

        return completions;
    }
}

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
        if (!sender.hasPermission(Settings.permissionAdmin())) {
            Lang.send(sender, "command.no_permission", "permission", Settings.permissionAdmin());
            return true;
        }

        if (args.length == 0) {
            Lang.send(sender, "command.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            main.reloadConfig();
            Settings.reload();
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

        if (args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("debug")) {
            sendDebug(sender);
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
            StringUtil.copyPartialMatches(args[0], Arrays.asList("reload", "lang", "status", "debug"), completions);
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("lang")) {
            StringUtil.copyPartialMatches(args[1], Arrays.asList("en_US", "de_DE"), completions);
        }

        return completions;
    }

    private void sendStatus(CommandSender sender) {
        Lang.send(sender, "command.status.header");
        Lang.send(sender, "command.status.language", "lang", Lang.getCurrentLanguage());
        Lang.send(sender, "command.status.permissions",
                "admin", Settings.permissionAdmin(),
                "use", Settings.permissionUse(),
                "leashable", Settings.permissionLeashable());

        Lang.send(sender, "command.status.features",
                "leash", onOff(Settings.featureLeashInteractions()),
                "follow", onOff(Settings.featureLeashFollowTask()),
                "effects", onOff(Settings.featureLeashEffects()),
                "summon", onOff(Settings.featureSummonStar()),
                "fence", onOff(Settings.featureFenceBinding()),
                "bone", onOff(Settings.featureBoneControl()),
                "food", onOff(Settings.featureFoodShare()),
                "look", onOff(Settings.featureLookatTotem()),
                "portal", onOff(Settings.featurePortalSync()),
                "respawn", onOff(Settings.featureRespawnSync()),
                "crossworld", onOff(Settings.featureCrossWorldSync()),
                "falldamage", onOff(Settings.featureFallDamageProtection()));
    }

    private void sendDebug(CommandSender sender) {
        int masters = SoulLeash.leashMap.size();
        int links = SoulLeash.leashMap.values().stream().mapToInt(List::size).sum();
        int followTasks = SoulLeash.leashTasks.size();
        int pendingTeleport = SoulLeash.getPendingTeleportCount();
        int fenceBound = Fence.getFenceBoundCount();

        Lang.send(sender, "command.debug.header");
        Lang.send(sender, "command.debug.stats",
                "masters", masters,
                "links", links,
                "tasks", followTasks,
                "fence", fenceBound,
                "pending", pendingTeleport);
        Lang.send(sender, "command.debug.leash_tuning",
                "period", Settings.leashTaskPeriodTicks(),
                "teleport", Settings.leashTeleportDistance(),
                "hard", Settings.leashPullHardDistance(),
                "medium", Settings.leashPullMediumDistance(),
                "soft", Settings.leashPullSoftDistance());
        Lang.send(sender, "command.debug.sync_tuning",
                "join", Settings.joinResyncDelayTicks(),
                "portal", Settings.portalFollowDelayTicks(),
                "effects", Settings.leashEffectPeriodTicks(),
                "summon", Settings.summonCooldownSeconds(),
                "foodcooldown", Settings.foodShareCooldownMs());
    }

    private String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }
}

package nc;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Executors implements CommandExecutor, TabCompleter {

    private final SoulLeash main;

    public Executors(SoulLeash main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Lang.send(sender, "command.usage");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("clearname")) {
            if (!(sender instanceof Player player)) {
                Lang.send(sender, "command.players_only");
                return true;
            }

            if (args.length >= 2) {
                if (!sender.hasPermission(Settings.permissionAdmin())) {
                    Lang.send(sender, "command.no_permission", "permission", Settings.permissionAdmin());
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    Lang.send(sender, "command.player_not_found", "player", args[1]);
                    return true;
                }
                leash.clearCustomName(target.getUniqueId());
                Lang.send(sender, "leash.name_cleared", "player", target.getName());
                return true;
            }

            if (leash.isCurrentlyLeashed(player.getUniqueId())) {
                Lang.send(sender, "command.cannot_clearname_while_leashed");
                return true;
            }
            leash.clearCustomName(player.getUniqueId());
            Lang.send(sender, "leash.name_cleared", "player", player.getName());
            return true;
        }

        if (!sender.hasPermission(Settings.permissionAdmin())) {
            Lang.send(sender, "command.no_permission", "permission", Settings.permissionAdmin());
            return true;
        }

        if (sub.equals("reload")) {
            main.reloadConfig();
            Settings.reload();
            Lang.reload();
            Lang.send(sender, "command.reload.success", "lang", Lang.getCurrentLanguage());
            return true;
        }

        if (sub.equals("lang")) {
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

        if (sub.equals("status")) {
            sendStatus(sender);
            return true;
        }

        if (sub.equals("debug")) {
            sendDebug(sender);
            return true;
        }

        if (sub.equals("test")) {
            if (!(sender instanceof Player player)) {
                Lang.send(sender, "command.players_only");
                return true;
            }
            return handleTestSubcommand(player, args);
        }

        if (sub.equals("select") && args.length >= 2) {
            if (!(sender instanceof Player owner)) {
                Lang.send(sender, "command.players_only");
                return true;
            }
            Player follower = Bukkit.getPlayerExact(args[1]);
            if (follower == null) {
                Lang.send(sender, "command.player_not_found", "player", args[1]);
                return true;
            }
            if (!leash.isOwnedBy(owner.getUniqueId(), follower.getUniqueId())) {
                Lang.send(sender, "command.not_owned", "player", follower.getName());
                return true;
            }
            leash.setSelectedFollower(owner.getUniqueId(), follower.getUniqueId());
            Lang.send(sender, "command.select.success", "player", follower.getName());
            return true;
        }

        if (sub.equals("temp") && args.length >= 2) {
            if (!(sender instanceof Player owner)) {
                Lang.send(sender, "command.players_only");
                return true;
            }
            Player follower = Bukkit.getPlayerExact(args[1]);
            if (follower == null) {
                Lang.send(sender, "command.player_not_found", "player", args[1]);
                return true;
            }
            if (leash.temporaryToggle(owner.getUniqueId(), follower.getUniqueId())) {
                if (leash.isTemporarilyDetached(follower.getUniqueId())) {
                    Lang.send(sender, "leash.temp_detached", "player", follower.getName());
                } else {
                    Lang.send(sender, "leash.temp_attached", "player", follower.getName());
                }
                return true;
            }
            Lang.send(sender, "command.not_owned", "player", follower.getName());
            return true;
        }

        if (sub.equals("permanent") && args.length >= 2) {
            if (!(sender instanceof Player owner)) {
                Lang.send(sender, "command.players_only");
                return true;
            }
            Player follower = Bukkit.getPlayerExact(args[1]);
            if (follower == null) {
                Lang.send(sender, "command.player_not_found", "player", args[1]);
                return true;
            }
            if (leash.permanentlyDetach(owner.getUniqueId(), follower.getUniqueId())) {
                Lang.send(sender, "leash.unbound", "player", follower.getName());
                return true;
            }
            Lang.send(sender, "command.not_owned", "player", follower.getName());
            return true;
        }

        if (sub.equals("length") && args.length >= 3) {
            if (!(sender instanceof Player owner)) {
                Lang.send(sender, "command.players_only");
                return true;
            }
            Player follower = Bukkit.getPlayerExact(args[1]);
            if (follower == null) {
                Lang.send(sender, "command.player_not_found", "player", args[1]);
                return true;
            }

            double value;
            try {
                value = Double.parseDouble(args[2]);
            } catch (NumberFormatException ex) {
                Lang.send(sender, "command.invalid_number", "value", args[2]);
                return true;
            }

            boolean ok;
            if (args.length >= 4 && args[3].equalsIgnoreCase("add")) {
                ok = leash.addLength(owner.getUniqueId(), follower.getUniqueId(), value);
            } else {
                ok = leash.setLength(owner.getUniqueId(), follower.getUniqueId(), value);
            }

            if (!ok) {
                Lang.send(sender, "command.not_owned", "player", follower.getName());
                return true;
            }

            Lang.send(sender, "leash.length_changed",
                    "player", follower.getName(),
                    "length", String.format(Locale.US, "%.1f", leash.getLength(follower.getUniqueId())));
            return true;
        }

        if (sub.equals("anchor") && args.length >= 3) {
            if (!(sender instanceof Player owner)) {
                Lang.send(sender, "command.players_only");
                return true;
            }
            Player follower = Bukkit.getPlayerExact(args[1]);
            if (follower == null) {
                Lang.send(sender, "command.player_not_found", "player", args[1]);
                return true;
            }
            if (!leash.isOwnedBy(owner.getUniqueId(), follower.getUniqueId())) {
                Lang.send(sender, "command.not_owned", "player", follower.getName());
                return true;
            }

            String mode = args[2].toLowerCase(Locale.ROOT);
            if (mode.equals("owner")) {
                leash.setAnchorToOwner(owner.getUniqueId(), follower.getUniqueId());
                leash.resumeLeash(owner.getUniqueId(), follower.getUniqueId());
                Lang.send(sender, "leash.anchor_owner", "player", follower.getName());
                return true;
            }
            if (mode.equals("block")) {
                leash.setAnchorToBlock(owner.getUniqueId(), follower.getUniqueId(), owner.getLocation());
                leash.resumeLeash(owner.getUniqueId(), follower.getUniqueId());
                Lang.send(sender, "leash.anchor_block", "player", follower.getName());
                return true;
            }
            if (mode.equals("entity")) {
                Entity target = owner.getTargetEntity(8);
                if (target == null || target instanceof Player) {
                    Lang.send(sender, "command.anchor_entity_missing");
                    return true;
                }
                leash.setAnchorToEntity(owner.getUniqueId(), follower.getUniqueId(), target);
                leash.resumeLeash(owner.getUniqueId(), follower.getUniqueId());
                Lang.send(sender, "leash.anchor_entity", "entity", target.getType().name().toLowerCase(Locale.ROOT));
                return true;
            }
            Lang.send(sender, "command.usage");
            return true;
        }

        Lang.send(sender, "command.usage");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList(
                    "reload", "lang", "status", "debug", "select", "temp", "permanent", "length", "anchor", "clearname", "test"
            ), completions);
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("lang")) {
            StringUtil.copyPartialMatches(args[1], Arrays.asList("en_US", "de_DE"), completions);
            return completions;
        }

        if (args.length == 2 && Arrays.asList("select", "temp", "permanent", "length", "anchor").contains(args[0].toLowerCase(Locale.ROOT))) {
            StringUtil.copyPartialMatches(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), completions);
            return completions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("anchor")) {
            StringUtil.copyPartialMatches(args[2], Arrays.asList("owner", "entity", "block"), completions);
            return completions;
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("length")) {
            StringUtil.copyPartialMatches(args[3], Arrays.asList("set", "add"), completions);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            StringUtil.copyPartialMatches(args[1], Arrays.asList("spawn", "despawn", "reset", "run", "mirror"), completions);
            return completions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("test")) {
            if (args[1].equalsIgnoreCase("run")) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("basic"), completions);
                return completions;
            }
            if (args[1].equalsIgnoreCase("mirror")) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("start", "stop", "tug"), completions);
                return completions;
            }
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

    private boolean handleTestSubcommand(Player player, String[] args) {
        if (args.length < 2) {
            Lang.send(player, "command.test.usage");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("spawn")) {
            SoloTestManager.spawnOrMoveSubject(player);
            Lang.send(player, "command.test.spawned");
            return true;
        }

        if (action.equals("despawn")) {
            boolean removed = SoloTestManager.despawnSubject(player.getUniqueId());
            Lang.send(player, removed ? "command.test.despawned" : "command.test.no_subject");
            return true;
        }

        if (action.equals("reset")) {
            SoloTestManager.reset(player.getUniqueId());
            Lang.send(player, "command.test.reset");
            return true;
        }

        if (action.equals("run") && args.length >= 3 && args[2].equalsIgnoreCase("basic")) {
            SoloTestManager.spawnOrMoveSubject(player);
            SoloTestManager.startMirror(player);
            SoloTestManager.tug(player);
            Lang.send(player, "command.test.run_basic");
            return true;
        }

        if (action.equals("mirror")) {
            if (args.length < 3) {
                Lang.send(player, "command.test.usage");
                return true;
            }

            String mirrorAction = args[2].toLowerCase(Locale.ROOT);
            if (mirrorAction.equals("start")) {
                SoloTestManager.startMirror(player);
                Lang.send(player, "command.test.mirror_started");
                return true;
            }
            if (mirrorAction.equals("stop")) {
                SoloTestManager.stopMirror(player.getUniqueId());
                Lang.send(player, "command.test.mirror_stopped");
                return true;
            }
            if (mirrorAction.equals("tug")) {
                if (SoloTestManager.tug(player)) {
                    Lang.send(player, "command.test.mirror_tug");
                } else {
                    Lang.send(player, "command.test.no_subject");
                }
                return true;
            }
        }

        Lang.send(player, "command.test.usage");
        return true;
    }
}

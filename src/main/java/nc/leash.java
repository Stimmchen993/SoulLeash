package nc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static nc.SoulLeash.instance;
import static nc.SoulLeash.leashDataConfig;
import static nc.SoulLeash.leashMap;
import static nc.SoulLeash.leashTasks;

public class leash implements Listener {
    private enum AnchorType {
        OWNER,
        ENTITY,
        BLOCK
    }

    private static final class AnchorState {
        private final AnchorType type;
        private final UUID entityId;
        private final Location blockLocation;

        private AnchorState(AnchorType type, UUID entityId, Location blockLocation) {
            this.type = type;
            this.entityId = entityId;
            this.blockLocation = blockLocation;
        }

        private static AnchorState owner() {
            return new AnchorState(AnchorType.OWNER, null, null);
        }

        private static AnchorState entity(UUID id) {
            return new AnchorState(AnchorType.ENTITY, id, null);
        }

        private static AnchorState block(Location location) {
            return new AnchorState(AnchorType.BLOCK, null, location.clone());
        }
    }

    private static final Map<UUID, UUID> ownerByFollower = new ConcurrentHashMap<>();
    private static final Map<UUID, AnchorState> anchorByFollower = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> maxLengthByFollower = new ConcurrentHashMap<>();
    private static final Set<UUID> temporaryDetached = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, UUID> selectedFollowerByOwner = new ConcurrentHashMap<>();
    private static final Map<UUID, String> customNameByFollower = new ConcurrentHashMap<>();
    private static final Map<UUID, HoldAdjustSession> holdAdjustSessions = new ConcurrentHashMap<>();

    private static final class HoldAdjustSession {
        private final UUID owner;
        private final UUID follower;
        private final long startedAt;

        private HoldAdjustSession(UUID owner, UUID follower) {
            this.owner = owner;
            this.follower = follower;
            this.startedAt = System.currentTimeMillis();
        }
    }

    public static void rebuildOwnershipIndex() {
        ownerByFollower.clear();
        leashMap.forEach((owner, followers) -> {
            for (UUID follower : followers) {
                ownerByFollower.put(follower, owner);
            }
        });
    }

    public static UUID getOwner(UUID follower) {
        return ownerByFollower.get(follower);
    }

    public static boolean isOwnedBy(UUID owner, UUID follower) {
        return owner.equals(ownerByFollower.get(follower));
    }

    public static boolean isTemporarilyDetached(UUID follower) {
        return temporaryDetached.contains(follower);
    }

    public static UUID getSelectedFollower(UUID owner) {
        return selectedFollowerByOwner.get(owner);
    }

    public static void setSelectedFollower(UUID owner, UUID follower) {
        selectedFollowerByOwner.put(owner, follower);
    }

    public static boolean isCurrentlyLeashed(UUID follower) {
        return ownerByFollower.containsKey(follower);
    }

    public static boolean hasCustomName(UUID follower) {
        return customNameByFollower.containsKey(follower);
    }

    public static boolean clearCustomName(UUID follower) {
        customNameByFollower.remove(follower);
        Player player = Bukkit.getPlayer(follower);
        if (player != null && player.isOnline()) {
            player.customName(null);
            player.setCustomNameVisible(false);
        }
        persistAdvancedState();
        return true;
    }

    public static boolean setCustomName(UUID owner, UUID follower, String customName) {
        if (!isOwnedBy(owner, follower) || customName == null || customName.isBlank()) {
            return false;
        }
        customNameByFollower.put(follower, customName);
        Player player = Bukkit.getPlayer(follower);
        if (player != null && player.isOnline()) {
            player.customName(net.kyori.adventure.text.Component.text(customName));
            player.setCustomNameVisible(true);
        }
        persistAdvancedState();
        return true;
    }

    public static boolean setLength(UUID owner, UUID follower, double length) {
        if (!isOwnedBy(owner, follower)) {
            return false;
        }
        double clamped = Math.max(Settings.leashLengthMin(), Math.min(Settings.leashLengthMax(), length));
        maxLengthByFollower.put(follower, clamped);
        persistAdvancedState();
        return true;
    }

    public static double getLength(UUID follower) {
        return maxLengthByFollower.getOrDefault(follower, Settings.leashDefaultLength());
    }

    public static boolean addLength(UUID owner, UUID follower, double delta) {
        if (!isOwnedBy(owner, follower)) {
            return false;
        }
        return setLength(owner, follower, getLength(follower) + delta);
    }

    public static boolean permanentlyDetach(UUID owner, UUID follower) {
        if (!isOwnedBy(owner, follower)) {
            return false;
        }

        List<UUID> followers = leashMap.get(owner);
        if (followers != null) {
            followers.remove(follower);
            if (followers.isEmpty()) {
                leashMap.remove(owner);
                leashDataConfig.set(owner.toString(), null);
            } else {
                leashDataConfig.set(owner.toString(), followers.stream().map(UUID::toString).collect(Collectors.toList()));
            }
        }

        ownerByFollower.remove(follower);
        anchorByFollower.remove(follower);
        maxLengthByFollower.remove(follower);
        temporaryDetached.remove(follower);
        selectedFollowerByOwner.entrySet().removeIf(entry -> entry.getValue().equals(follower));

        Helper.removeLeash(follower);
        clearLeashTask(follower);
        clearFenceBinding(follower);
        persistAdvancedState();
        instance.saveLeashData();
        return true;
    }

    public static void startOptOutComplianceTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Set<UUID> toDisconnect = new HashSet<>();
                for (UUID follower : ownerByFollower.keySet()) {
                    Player player = Bukkit.getPlayer(follower);
                    if (player != null && player.isOnline() && !player.hasPermission(Settings.permissionLeashable())) {
                        toDisconnect.add(follower);
                    }
                }
                for (UUID follower : toDisconnect) {
                    disconnectPlayerFromAllLeashes(follower, true);
                }

                Set<UUID> toClearOnly = new HashSet<>();
                for (UUID follower : customNameByFollower.keySet()) {
                    if (toDisconnect.contains(follower)) {
                        continue;
                    }
                    Player player = Bukkit.getPlayer(follower);
                    if (player != null && player.isOnline() && !player.hasPermission(Settings.permissionLeashable())) {
                        toClearOnly.add(follower);
                    }
                }
                for (UUID follower : toClearOnly) {
                    clearCustomName(follower);
                }
            }
        }.runTaskTimer(instance, 20L, 20L);
    }

    public static void disconnectPlayerFromAllLeashes(UUID player, boolean clearName) {
        UUID owner = ownerByFollower.get(player);
        if (owner != null) {
            permanentlyDetach(owner, player);
        }

        List<UUID> followersOwned = new ArrayList<>(leashMap.getOrDefault(player, Collections.emptyList()));
        for (UUID follower : followersOwned) {
            permanentlyDetach(player, follower);
        }

        if (clearName) {
            clearCustomName(player);
        }
    }

    public static boolean temporaryToggle(UUID owner, UUID follower) {
        if (!isOwnedBy(owner, follower)) {
            return false;
        }

        if (temporaryDetached.contains(follower)) {
            temporaryDetached.remove(follower);
            resumeLeash(owner, follower);
        } else {
            temporaryDetached.add(follower);
            Helper.removeLeash(follower);
            clearLeashTask(follower);
        }

        persistAdvancedState();
        return true;
    }

    public static boolean resumeLeash(UUID owner, UUID follower) {
        if (!isOwnedBy(owner, follower)) {
            return false;
        }
        temporaryDetached.remove(follower);
        Player ownerPlayer = Bukkit.getPlayer(owner);
        Player followerPlayer = Bukkit.getPlayer(follower);
        if (ownerPlayer != null && ownerPlayer.isOnline() && followerPlayer != null && followerPlayer.isOnline()) {
            ensureVisualLeash(ownerPlayer, followerPlayer);
            startLeashTask(ownerPlayer, followerPlayer);
        }
        persistAdvancedState();
        return true;
    }

    public static boolean setAnchorToOwner(UUID owner, UUID follower) {
        if (!isOwnedBy(owner, follower)) {
            return false;
        }
        anchorByFollower.put(follower, AnchorState.owner());
        persistAdvancedState();
        return true;
    }

    public static boolean setAnchorToEntity(UUID owner, UUID follower, Entity entity) {
        if (!isOwnedBy(owner, follower)) {
            return false;
        }
        if (entity == null || !entity.isValid()) {
            return false;
        }
        anchorByFollower.put(follower, AnchorState.entity(entity.getUniqueId()));
        temporaryDetached.remove(follower);

        Player followerPlayer = Bukkit.getPlayer(follower);
        if (followerPlayer != null && followerPlayer.isOnline()) {
            ensureVisualLeash(entity, followerPlayer);
        }

        persistAdvancedState();
        return true;
    }

    public static boolean setAnchorToBlock(UUID owner, UUID follower, Location location) {
        if (!isOwnedBy(owner, follower) || location == null) {
            return false;
        }

        Location anchor = location.clone();
        Block block = anchor.getBlock();
        if (isFence(block.getType())) {
            LeashHitch hitch = block.getWorld().spawn(block.getLocation().add(0.5, 0.5, 0.5), LeashHitch.class);
            anchorByFollower.put(follower, AnchorState.entity(hitch.getUniqueId()));
        } else {
            anchorByFollower.put(follower, AnchorState.block(anchor));
        }

        temporaryDetached.remove(follower);
        persistAdvancedState();
        return true;
    }

    public static void pullAndLook(UUID owner, UUID follower) {
        if (!isOwnedBy(owner, follower) || temporaryDetached.contains(follower)) {
            return;
        }

        Player ownerPlayer = Bukkit.getPlayer(owner);
        Player followerPlayer = Bukkit.getPlayer(follower);
        if (ownerPlayer == null || followerPlayer == null || !ownerPlayer.isOnline() || !followerPlayer.isOnline()) {
            return;
        }

        Location ownerLoc = ownerPlayer.getLocation();
        Location followerLoc = followerPlayer.getLocation();
        double distance = ownerLoc.distance(followerLoc);

        if (distance > Settings.leashTeleportDistance()) {
            followerPlayer.teleport(ownerLoc);
        } else {
            Vector towardOwner = ownerLoc.toVector().subtract(followerLoc.toVector()).normalize().multiply(Settings.leashTapPullStrength());
            followerPlayer.setVelocity(followerPlayer.getVelocity().add(towardOwner));
        }

        Lookat.performSoulLeashLook(ownerPlayer);
    }

    private static boolean bind(UUID owner, UUID follower) {
        if (ownerByFollower.containsKey(follower)) {
            return false;
        }

        leashMap.putIfAbsent(owner, new ArrayList<>());
        leashMap.get(owner).add(follower);
        ownerByFollower.put(follower, owner);
        anchorByFollower.put(follower, AnchorState.owner());
        maxLengthByFollower.put(follower, Settings.leashDefaultLength());
        selectedFollowerByOwner.put(owner, follower);
        temporaryDetached.remove(follower);

        leashDataConfig.set(owner.toString(), leashMap.get(owner).stream().map(UUID::toString).collect(Collectors.toList()));
        persistAdvancedState();
        instance.saveLeashData();
        return true;
    }

    private static void persistAdvancedState() {
        leashDataConfig.set("state.owners", null);
        leashDataConfig.set("state.temporaryDetached", temporaryDetached.stream().map(UUID::toString).collect(Collectors.toList()));

        Map<String, Double> lengths = new HashMap<>();
        maxLengthByFollower.forEach((uuid, length) -> lengths.put(uuid.toString(), length));
        leashDataConfig.set("state.lengths", lengths);

        Map<String, String> selected = new HashMap<>();
        selectedFollowerByOwner.forEach((owner, follower) -> selected.put(owner.toString(), follower.toString()));
        leashDataConfig.set("state.selected", selected);

        Map<String, String> names = new HashMap<>();
        customNameByFollower.forEach((follower, name) -> names.put(follower.toString(), name));
        leashDataConfig.set("state.customNames", names);

        leashDataConfig.set("state.anchors", null);
        anchorByFollower.forEach((follower, anchorState) -> {
            String path = "state.anchors." + follower;
            leashDataConfig.set(path + ".type", anchorState.type.name());
            if (anchorState.entityId != null) {
                leashDataConfig.set(path + ".entity", anchorState.entityId.toString());
            }
            if (anchorState.blockLocation != null) {
                leashDataConfig.set(path + ".block", anchorState.blockLocation.serialize());
            }
        });

        instance.saveLeashData();
    }

    public static void loadAdvancedState() {
        temporaryDetached.clear();
        maxLengthByFollower.clear();
        selectedFollowerByOwner.clear();
        anchorByFollower.clear();
        customNameByFollower.clear();

        List<String> detached = leashDataConfig.getStringList("state.temporaryDetached");
        for (String uuid : detached) {
            try {
                temporaryDetached.add(UUID.fromString(uuid));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (leashDataConfig.isConfigurationSection("state.lengths")) {
            for (String key : Objects.requireNonNull(leashDataConfig.getConfigurationSection("state.lengths")).getKeys(false)) {
                try {
                    maxLengthByFollower.put(UUID.fromString(key), leashDataConfig.getDouble("state.lengths." + key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if (leashDataConfig.isConfigurationSection("state.selected")) {
            for (String key : Objects.requireNonNull(leashDataConfig.getConfigurationSection("state.selected")).getKeys(false)) {
                try {
                    UUID owner = UUID.fromString(key);
                    UUID follower = UUID.fromString(Objects.requireNonNull(leashDataConfig.getString("state.selected." + key)));
                    selectedFollowerByOwner.put(owner, follower);
                } catch (Exception ignored) {
                }
            }
        }

        if (leashDataConfig.isConfigurationSection("state.customNames")) {
            for (String key : Objects.requireNonNull(leashDataConfig.getConfigurationSection("state.customNames")).getKeys(false)) {
                try {
                    UUID follower = UUID.fromString(key);
                    String name = leashDataConfig.getString("state.customNames." + key);
                    if (name != null && !name.isBlank()) {
                        customNameByFollower.put(follower, name);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (leashDataConfig.isConfigurationSection("state.anchors")) {
            for (String key : Objects.requireNonNull(leashDataConfig.getConfigurationSection("state.anchors")).getKeys(false)) {
                try {
                    UUID follower = UUID.fromString(key);
                    String typeRaw = leashDataConfig.getString("state.anchors." + key + ".type", "OWNER");
                    AnchorType type = AnchorType.valueOf(typeRaw.toUpperCase(Locale.ROOT));
                    if (type == AnchorType.ENTITY) {
                        String entityRaw = leashDataConfig.getString("state.anchors." + key + ".entity");
                        if (entityRaw != null) {
                            anchorByFollower.put(follower, AnchorState.entity(UUID.fromString(entityRaw)));
                        }
                    } else if (type == AnchorType.BLOCK) {
                        if (leashDataConfig.isConfigurationSection("state.anchors." + key + ".block")) {
                            Map<String, Object> map = Objects.requireNonNull(leashDataConfig.getConfigurationSection("state.anchors." + key + ".block")).getValues(false);
                            anchorByFollower.put(follower, AnchorState.block(Location.deserialize(map)));
                        }
                    } else {
                        anchorByFollower.put(follower, AnchorState.owner());
                    }
                } catch (Exception ignored) {
                }
            }
        }

        for (Map.Entry<UUID, String> entry : customNameByFollower.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.customName(net.kyori.adventure.text.Component.text(entry.getValue()));
                player.setCustomNameVisible(true);
            }
        }
    }

    @EventHandler
    public void onLeashPlayer(PlayerInteractAtEntityEvent e) {
        if (!Settings.featureLeashInteractions()) return;
        if (!(e.getRightClicked() instanceof Player target)) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player owner = e.getPlayer();
        if (!owner.hasPermission(Settings.permissionUse())) return;
        if (!target.hasPermission(Settings.permissionLeashable())) return;

        UUID ownerId = owner.getUniqueId();
        UUID targetId = target.getUniqueId();
        Material hand = owner.getInventory().getItemInMainHand().getType();

        if (hand == Material.NAME_TAG && isOwnedBy(ownerId, targetId)) {
            ItemStack stack = owner.getInventory().getItemInMainHand();
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String name = meta.getDisplayName();
                if (!name.isBlank() && setCustomName(ownerId, targetId, name)) {
                    e.setCancelled(true);
                    if (owner.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                        if (stack.getAmount() <= 1) {
                            owner.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                        } else {
                            stack.setAmount(stack.getAmount() - 1);
                        }
                    }
                    Lang.send(owner, "leash.name_set", "player", target.getName(), "name", name);
                }
            }
            return;
        }

        if (hand == Material.LEAD) {
            e.setCancelled(true);
            UUID existingOwner = ownerByFollower.get(targetId);
            if (existingOwner == null) {
                if (bind(ownerId, targetId)) {
                    ensureVisualLeash(owner, target);
                    startLeashTask(owner, target);
                    Lang.send(owner, "leash.bound", "player", target.getName());
                }
                return;
            }

            if (!existingOwner.equals(ownerId)) {
                return;
            }

            if (temporaryToggle(ownerId, targetId)) {
                if (isTemporarilyDetached(targetId)) {
                    Lang.send(owner, "leash.temp_detached", "player", target.getName());
                } else {
                    Lang.send(owner, "leash.temp_attached", "player", target.getName());
                }
            }
            return;
        }

        if (isSwordOrAxe(hand)) {
            e.setCancelled(true);
            if (permanentlyDetach(ownerId, targetId)) {
                Lang.send(owner, "leash.unbound", "player", target.getName());
            }
        }
    }

    @EventHandler
    public void onAnchorEntity(PlayerInteractEntityEvent e) {
        if (!Settings.featureLeashInteractions()) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player owner = e.getPlayer();
        if (owner.getInventory().getItemInMainHand().getType() != Material.LEAD) return;
        if (e.getRightClicked() instanceof Player) return;

        UUID ownerId = owner.getUniqueId();
        UUID followerId = selectedFollowerByOwner.get(ownerId);
        if (followerId == null || !isOwnedBy(ownerId, followerId)) {
            return;
        }

        e.setCancelled(true);
        if (setAnchorToEntity(ownerId, followerId, e.getRightClicked())) {
            Player follower = Bukkit.getPlayer(followerId);
            if (follower != null) {
                startLeashTask(owner, follower);
            }
            Lang.send(owner, "leash.anchor_entity", "entity", e.getRightClicked().getType().name().toLowerCase(Locale.ROOT));
        }
    }

    @EventHandler
    public void onLeadTapOrHold(PlayerInteractEvent e) {
        if (!Settings.featureLeashInteractions()) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player owner = e.getPlayer();
        if (owner.getInventory().getItemInMainHand().getType() != Material.LEAD) return;

        UUID ownerId = owner.getUniqueId();
        UUID followerId = selectedFollowerByOwner.get(ownerId);
        if (followerId == null || !isOwnedBy(ownerId, followerId)) {
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            if (setAnchorToBlock(ownerId, followerId, e.getClickedBlock().getLocation().add(0.5, 1.0, 0.5))) {
                Player follower = Bukkit.getPlayer(followerId);
                if (follower != null && follower.isOnline()) {
                    startLeashTask(owner, follower);
                }
            }
        }

        if (owner.isSneaking()) {
            startHoldAdjust(owner, followerId);
            e.setCancelled(true);
            return;
        }

        pullAndLook(ownerId, followerId);
    }

    private void startHoldAdjust(Player owner, UUID followerId) {
        UUID ownerId = owner.getUniqueId();
        if (holdAdjustSessions.containsKey(ownerId)) {
            return;
        }

        HoldAdjustSession session = new HoldAdjustSession(ownerId, followerId);
        holdAdjustSessions.put(ownerId, session);
        String followerName = Bukkit.getOfflinePlayer(followerId).getName();
        Lang.send(owner, "leash.hold_started", "player", followerName == null ? followerId.toString() : followerName);

        new BukkitRunnable() {
            @Override
            public void run() {
                HoldAdjustSession current = holdAdjustSessions.get(ownerId);
                if (current == null) {
                    cancel();
                    return;
                }

                Player currentOwner = Bukkit.getPlayer(current.owner);
                if (currentOwner == null || !currentOwner.isOnline()) {
                    holdAdjustSessions.remove(ownerId);
                    cancel();
                    return;
                }

                boolean stillHolding = currentOwner.getInventory().getItemInMainHand().getType() == Material.LEAD && currentOwner.isSneaking();
                if (stillHolding && System.currentTimeMillis() - current.startedAt <= Settings.leashHoldMaxMs()) {
                    return;
                }

                holdAdjustSessions.remove(ownerId);
                long heldMs = Math.max(0L, System.currentTimeMillis() - current.startedAt);
                double delta = Math.min(Settings.leashLengthMaxDeltaPerHold(), heldMs / 1000.0D * Settings.leashLengthPerSecond());
                if (setLength(current.owner, current.follower, getLength(current.follower) + delta)) {
                    Player follower = Bukkit.getPlayer(current.follower);
                    String followerName = follower != null ? follower.getName() : String.valueOf(current.follower);
                    Lang.send(currentOwner, "leash.length_changed",
                            "player", followerName,
                            "length", String.format(Locale.US, "%.1f", getLength(current.follower)));
                }
                cancel();
            }
        }.runTaskTimer(instance, 1L, 1L);
    }

    static void startLeashTask(Player owner, Player follower) {
        if (!Settings.featureLeashFollowTask()) return;

        UUID followerId = follower.getUniqueId();
        if (temporaryDetached.contains(followerId)) {
            clearLeashTask(followerId);
            return;
        }

        if (leashTasks.containsKey(followerId)) {
            leashTasks.get(followerId).cancel();
        }

        final Location[] lastLocation = {null};
        final long[] stuckStartTime = {0};

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                UUID ownerId = ownerByFollower.get(followerId);
                if (ownerId == null) {
                    clearLeashTask(followerId);
                    cancel();
                    return;
                }

                Player currentOwner = Bukkit.getPlayer(ownerId);
                Player currentFollower = Bukkit.getPlayer(followerId);
                if (currentOwner == null || currentFollower == null || !currentOwner.isOnline() || !currentFollower.isOnline()) {
                    cancel();
                    return;
                }

                if (!currentFollower.hasPermission(Settings.permissionLeashable())) {
                    disconnectPlayerFromAllLeashes(followerId, true);
                    Lang.send(currentOwner, "leash.optout_disconnect", "player", currentFollower.getName());
                    cancel();
                    return;
                }

                if (temporaryDetached.contains(followerId)) {
                    Helper.removeLeash(followerId);
                    cancel();
                    return;
                }

                Location anchorLoc = resolveAnchorLocation(ownerId, followerId, currentOwner, currentFollower);
                if (anchorLoc == null) {
                    anchorByFollower.put(followerId, AnchorState.owner());
                    anchorLoc = currentOwner.getLocation();
                }

                Location followerLoc = currentFollower.getLocation();
                if (!anchorLoc.getWorld().equals(followerLoc.getWorld())) {
                    currentFollower.teleport(anchorLoc);
                    attachVisualForCurrentAnchor(ownerId, followerId, currentOwner, currentFollower);
                    return;
                }

                double distance = anchorLoc.distance(followerLoc);
                double maxLength = getLength(followerId);

                if (Settings.leashStuckEnabled() && distance > Math.max(Settings.leashStuckCheckDistanceMin(), maxLength)) {
                    if (lastLocation[0] != null) {
                        double movementXZ = Math.sqrt(Math.pow(lastLocation[0].getX() - followerLoc.getX(), 1)
                                + Math.pow(lastLocation[0].getZ() - followerLoc.getZ(), 1));
                        double deltaY = Math.abs(lastLocation[0].getY() - followerLoc.getY());
                        double approach = anchorLoc.distance(lastLocation[0]) - distance;

                        if (movementXZ < Settings.leashStuckMovementXZMax()
                                && approach < Settings.leashStuckApproachMax()
                                && deltaY <= Settings.leashStuckDeltaYMax()) {
                            if (stuckStartTime[0] == 0L) {
                                stuckStartTime[0] = System.currentTimeMillis();
                            } else if (System.currentTimeMillis() - stuckStartTime[0] > Settings.leashStuckTimeoutMs()) {
                                currentOwner.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                        new net.md_5.bungee.api.chat.TextComponent(Lang.tr("leash.stuck_actionbar", "player", currentFollower.getName())));
                                stuckStartTime[0] = 0L;
                            }
                        } else {
                            stuckStartTime[0] = 0L;
                        }
                    }
                }

                if (distance > Math.max(Settings.leashTeleportDistance(), maxLength + Settings.leashTeleportSlack())) {
                    currentFollower.teleport(anchorLoc);
                } else if (distance > maxLength) {
                    if (distance > Settings.leashPullHardDistance() && currentFollower.isOnGround()) {
                        FollowPhysics.applyGroundBoost(currentFollower, Settings.leashPullGroundYBoost());
                    }

                    Vector pull = FollowPhysics.computePull(
                            followerLoc,
                            anchorLoc,
                            distance,
                            maxLength,
                            Math.max(maxLength + 0.5, Settings.leashPullSoftDistance()),
                            Math.max(maxLength + 1.0, Settings.leashPullMediumDistance()),
                            Math.max(maxLength + 2.0, Settings.leashPullHardDistance()),
                            Settings.leashPullSoftStrength(),
                            Settings.leashPullMediumStrength(),
                            Settings.leashPullHardStrength(),
                            Settings.leashPullGroundStrength(),
                            currentFollower.isOnGround()
                    );

                    if (pull != null) {
                        currentFollower.setVelocity(currentFollower.getVelocity().add(pull));
                    }
                }

                lastLocation[0] = followerLoc.clone();
            }
        };

        task.runTaskTimer(instance, 0L, Settings.leashTaskPeriodTicks());
        leashTasks.put(followerId, task);
    }

    private static Location resolveAnchorLocation(UUID ownerId, UUID followerId, Player owner, Player follower) {
        AnchorState anchor = anchorByFollower.getOrDefault(followerId, AnchorState.owner());

        if (anchor.type == AnchorType.OWNER) {
            return owner.getLocation();
        }

        if (anchor.type == AnchorType.BLOCK) {
            return anchor.blockLocation == null ? owner.getLocation() : anchor.blockLocation.clone();
        }

        if (anchor.type == AnchorType.ENTITY && anchor.entityId != null) {
            Entity entity = Bukkit.getEntity(anchor.entityId);
            if (entity != null && entity.isValid()) {
                return entity.getLocation();
            }
            anchorByFollower.put(followerId, AnchorState.owner());
            return owner.getLocation();
        }

        return owner.getLocation();
    }

    private static void attachVisualForCurrentAnchor(UUID ownerId, UUID followerId, Player owner, Player follower) {
        AnchorState anchor = anchorByFollower.getOrDefault(followerId, AnchorState.owner());
        if (anchor.type == AnchorType.ENTITY && anchor.entityId != null) {
            Entity anchorEntity = Bukkit.getEntity(anchor.entityId);
            if (anchorEntity != null && anchorEntity.isValid()) {
                ensureVisualLeash(anchorEntity, follower);
                return;
            }
        }
        ensureVisualLeash(owner, follower);
    }

    private static void ensureVisualLeash(Entity holder, Player follower) {
        Helper.removeLeash(follower.getUniqueId());
        Helper.attachLeash(follower, holder);
    }

    private static void clearFenceBinding(UUID followerId) {
        leashDataConfig.set("fence_bounds." + followerId, null);
    }

    private static boolean isFence(Material material) {
        return material.name().endsWith("_FENCE") || material.name().endsWith("_WALL") || material == Material.CHAIN;
    }

    private static boolean isSwordOrAxe(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE");
    }

    public static void clearLeashTask(UUID followerId) {
        BukkitRunnable existing = leashTasks.remove(followerId);
        if (existing != null) {
            existing.cancel();
        }
    }
}

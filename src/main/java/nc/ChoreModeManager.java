package nc;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static nc.SoulLeash.instance;

public final class ChoreModeManager implements Listener {
    public enum ChatMode {
        PRIVATE,
        BROADCAST
    }

    public enum RenameMode {
        OFF,
        ON,
        RANDOM
    }

    public enum TaskType {
        MINE,
        GATHER,
        KILL,
        DELIVER
    }

    public static final class ChoreTask {
        private final TaskType type;
        private final Material materialTarget;
        private final EntityType entityTarget;
        private final int required;
        private int progress;

        private ChoreTask(TaskType type, Material materialTarget, EntityType entityTarget, int required) {
            this.type = type;
            this.materialTarget = materialTarget;
            this.entityTarget = entityTarget;
            this.required = Math.max(1, required);
            this.progress = 0;
        }

        public static ChoreTask mine(Material material, int required) {
            return new ChoreTask(TaskType.MINE, material, null, required);
        }

        public static ChoreTask gather(Material material, int required) {
            return new ChoreTask(TaskType.GATHER, material, null, required);
        }

        public static ChoreTask kill(EntityType type, int required) {
            return new ChoreTask(TaskType.KILL, null, type, required);
        }

        public static ChoreTask deliver(Material material, int required) {
            return new ChoreTask(TaskType.DELIVER, material, null, required);
        }

        public String describe() {
            return switch (type) {
                case MINE -> "Mine " + required + "x " + materialTarget.name().toLowerCase(Locale.ROOT);
                case GATHER -> "Gather " + required + "x " + materialTarget.name().toLowerCase(Locale.ROOT);
                case KILL -> "Kill " + required + "x " + entityTarget.name().toLowerCase(Locale.ROOT);
                case DELIVER -> "Deliver " + required + "x " + materialTarget.name().toLowerCase(Locale.ROOT) + " to Taskmaster";
            };
        }

        public boolean isComplete() {
            return progress >= required;
        }

        public void increment(int amount) {
            progress = Math.min(required, progress + Math.max(1, amount));
        }

        public TaskType getType() {
            return type;
        }

        public Material getMaterialTarget() {
            return materialTarget;
        }

        public EntityType getEntityTarget() {
            return entityTarget;
        }

        public int getRequired() {
            return required;
        }

        public int getProgress() {
            return progress;
        }
    }

    private static final class ChoreSession {
        private final UUID issuer;
        private final UUID target;
        private UUID npcId;
        private final Deque<ChoreTask> queue;
        private ChoreTask active;
        private final long endsAt;
        private ChatMode chatMode;
        private RenameMode renameMode;
        private final Component originalCustomName;
        private final boolean originalCustomNameVisible;
        private long nextTeaseAt;
        private long nextCareAt;
        private long nextTugAt;
        private long nextLookAt;

        private ChoreSession(UUID issuer, UUID target, Deque<ChoreTask> queue, long endsAt, ChatMode chatMode,
                             RenameMode renameMode, Component originalCustomName, boolean originalCustomNameVisible) {
            this.issuer = issuer;
            this.target = target;
            this.queue = queue;
            this.active = queue.pollFirst();
            this.endsAt = endsAt;
            this.chatMode = chatMode;
            this.renameMode = renameMode;
            this.originalCustomName = originalCustomName;
            this.originalCustomNameVisible = originalCustomNameVisible;
            this.nextTeaseAt = System.currentTimeMillis();
            this.nextCareAt = System.currentTimeMillis();
            this.nextTugAt = System.currentTimeMillis();
            this.nextLookAt = System.currentTimeMillis();
        }
    }

    private static final Material[] RANDOM_MINE = {
            Material.STONE, Material.COBBLESTONE, Material.COAL_ORE, Material.IRON_ORE, Material.OAK_LOG
    };
    private static final Material[] RANDOM_GATHER = {
            Material.WHEAT, Material.CARROT, Material.POTATO, Material.ROTTEN_FLESH, Material.STRING
    };
    private static final EntityType[] RANDOM_KILL = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER
    };
    private static final Material[] RANDOM_DELIVER = {
            Material.BREAD, Material.COOKED_BEEF, Material.OAK_LOG, Material.COBBLESTONE
    };
    private static final String[] TEASE_LINES = {
            "Keep moving, your chores are waiting.",
            "Focus up, this list won't clear itself.",
            "No slacking, complete the objective.",
            "Taskmaster says: stay on target."
    };
    private static final Set<String> PRESET_NAMES = Set.of("builder", "hunter", "farmer", "messenger");

    private static final Map<UUID, ChoreSession> sessions = new ConcurrentHashMap<>();
    private static BukkitRunnable ticker;

    private ChoreModeManager() {
    }

    public static void init(SoulLeash plugin) {
        Bukkit.getPluginManager().registerEvents(new ChoreModeManager(), plugin);
        startTicker();
    }

    public static boolean hasSession(UUID target) {
        return sessions.containsKey(target);
    }

    public static String getStatus(UUID target) {
        ChoreSession s = sessions.get(target);
        if (s == null) {
            return "No active chore session.";
        }
        long remainingSec = Math.max(0L, (s.endsAt - System.currentTimeMillis()) / 1000L);
        if (s.active == null) {
            return "All tasks completed. (cleanup pending)";
        }
        return s.active.describe() + " [" + s.active.getProgress() + "/" + s.active.getRequired() + "], " + remainingSec + "s left";
    }

    public static boolean startRandomByMinutes(Player issuer, Player target, int minutes) {
        int clamped = Math.max(1, Math.min(Settings.choreMaxMinutes(), minutes));
        int taskCount = Math.max(1, clamped / 5);
        long endsAt = System.currentTimeMillis() + clamped * 60_000L;
        return startInternal(issuer, target, generateRandomTasks(taskCount), endsAt);
    }

    public static boolean startRandomByTaskCount(Player issuer, Player target, int tasks) {
        int count = Math.max(1, Math.min(Settings.choreMaxTasks(), tasks));
        long endsAt = System.currentTimeMillis() + Math.max(15, count * 5) * 60_000L;
        return startInternal(issuer, target, generateRandomTasks(count), endsAt);
    }

    public static boolean startPreset(Player issuer, Player target, String preset, int rounds) {
        if (preset == null || !PRESET_NAMES.contains(preset.toLowerCase(Locale.ROOT))) {
            return false;
        }
        int clampedRounds = Math.max(1, Math.min(10, rounds));
        Deque<ChoreTask> tasks = generatePresetTasks(preset.toLowerCase(Locale.ROOT), clampedRounds);
        if (tasks.isEmpty()) {
            return false;
        }
        long endsAt = System.currentTimeMillis() + Math.max(20, tasks.size() * 5) * 60_000L;
        return startInternal(issuer, target, tasks, endsAt);
    }

    public static boolean addCustomTask(UUID target, ChoreTask task) {
        ChoreSession session = sessions.get(target);
        if (session == null || task == null) {
            return false;
        }
        session.queue.addLast(task);
        return true;
    }

    public static boolean setChatMode(UUID target, ChatMode mode) {
        ChoreSession session = sessions.get(target);
        if (session == null || mode == null) {
            return false;
        }
        session.chatMode = mode;
        return true;
    }

    public static boolean setRenameMode(UUID target, RenameMode mode) {
        ChoreSession session = sessions.get(target);
        if (session == null || mode == null) {
            return false;
        }
        session.renameMode = mode;
        Player player = Bukkit.getPlayer(target);
        if (player != null && player.isOnline()) {
            applyRenameMode(session, player);
        }
        return true;
    }

    public static boolean stop(UUID target, String reasonKey) {
        ChoreSession session = sessions.remove(target);
        if (session == null) {
            return false;
        }
        cleanup(session);
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            Lang.send(targetPlayer, reasonKey);
        }
        Player issuer = Bukkit.getPlayer(session.issuer);
        if (issuer != null && issuer.isOnline()) {
            Lang.send(issuer, reasonKey);
        }
        return true;
    }

    public static void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        List<ChoreSession> values = new ArrayList<>(sessions.values());
        sessions.clear();
        for (ChoreSession session : values) {
            cleanup(session);
        }
    }

    private static boolean startInternal(Player issuer, Player target, Deque<ChoreTask> tasks, long endsAt) {
        if (target == null || !target.isOnline() || tasks.isEmpty()) {
            return false;
        }
        if (!Settings.featureChoreMode()) {
            return false;
        }
        if (sessions.containsKey(target.getUniqueId())) {
            stop(target.getUniqueId(), "command.chore.stopped");
        }

        RenameMode renameMode = defaultRenameModeFor(issuer, target);
        ChoreSession session = new ChoreSession(
                issuer.getUniqueId(),
                target.getUniqueId(),
                tasks,
                endsAt,
                Settings.chorePrivateMessagesDefault() ? ChatMode.PRIVATE : ChatMode.BROADCAST,
                renameMode,
                target.customName(),
                target.isCustomNameVisible()
        );
        Entity npc = spawnNpc(target.getLocation());
        if (npc == null) {
            return false;
        }
        session.npcId = npc.getUniqueId();
        sessions.put(target.getUniqueId(), session);

        Helper.removeLeash(target.getUniqueId());
        Helper.attachLeash(target, npc);
        applyRenameMode(session, target);

        Lang.send(target, "command.chore.started_target", "issuer", issuer.getName());
        Lang.send(issuer, "command.chore.started_issuer", "player", target.getName());
        announceActiveTask(session);
        return true;
    }

    private static void startTicker() {
        if (ticker != null) {
            ticker.cancel();
        }

        ticker = new BukkitRunnable() {
            @Override
            public void run() {
                List<UUID> toStop = new ArrayList<>();
                for (Map.Entry<UUID, ChoreSession> entry : sessions.entrySet()) {
                    UUID targetId = entry.getKey();
                    ChoreSession session = entry.getValue();
                    Player target = Bukkit.getPlayer(targetId);
                    Entity npc = session.npcId == null ? null : Bukkit.getEntity(session.npcId);

                    if (target == null || !target.isOnline() || npc == null || !npc.isValid()) {
                        toStop.add(targetId);
                        continue;
                    }
                    if (!target.hasPermission(Settings.permissionLeashable())) {
                        toStop.add(targetId);
                        continue;
                    }
                    if (System.currentTimeMillis() >= session.endsAt) {
                        toStop.add(targetId);
                        continue;
                    }

                    handleKeepAlive(session, target);
                    handleTease(session, target);
                    handleLook(session, target, npc);
                    handleTug(session, target, npc);
                    handleDeliverTurnIn(session, target, npc);
                    ensureTaskProgression(session);
                }

                for (UUID id : toStop) {
                    stop(id, "command.chore.stopped");
                }
            }
        };

        ticker.runTaskTimer(instance, 20L, 20L);
    }

    private static void handleKeepAlive(ChoreSession session, Player target) {
        long now = System.currentTimeMillis();
        if (now < session.nextCareAt) {
            return;
        }
        session.nextCareAt = now + Settings.choreCareIntervalMs();

        if (target.getHealth() <= 8.0D) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 8, 1, false, false, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 8, 0, false, false, true));
        }
        if (target.getFoodLevel() <= 10) {
            target.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 2));
        }
        if (!target.getInventory().contains(Material.WOODEN_PICKAXE)) {
            target.getInventory().addItem(new ItemStack(Material.WOODEN_PICKAXE, 1));
        }
    }

    private static void handleTease(ChoreSession session, Player target) {
        long now = System.currentTimeMillis();
        if (now < session.nextTeaseAt) {
            return;
        }
        session.nextTeaseAt = now + Settings.choreTeaseIntervalMs();
        String line = TEASE_LINES[(int) (Math.random() * TEASE_LINES.length)];
        sendToTarget(session, target, "&d[Taskmaster] &f" + line);
    }

    private static void handleLook(ChoreSession session, Player target, Entity npc) {
        long now = System.currentTimeMillis();
        if (now < session.nextLookAt) {
            return;
        }
        session.nextLookAt = now + Settings.choreLookIntervalMs();
        Location from = target.getLocation();
        Location to = npc.getLocation();
        Location look = from.clone();
        look.setDirection(to.toVector().subtract(from.toVector()));
        target.setRotation(look.getYaw(), look.getPitch());
    }

    private static void handleTug(ChoreSession session, Player target, Entity npc) {
        long now = System.currentTimeMillis();
        if (now < session.nextTugAt) {
            return;
        }
        session.nextTugAt = now + Settings.choreTugIntervalMs();
        Location targetLoc = target.getLocation();
        Location npcLoc = npc.getLocation();

        if (!targetLoc.getWorld().equals(npcLoc.getWorld())) {
            target.teleport(npcLoc);
            return;
        }

        double distance = targetLoc.distance(npcLoc);
        if (distance > Settings.leashTeleportDistance()) {
            target.teleport(npcLoc);
            return;
        }

        if (distance > 5.0D) {
            Vector pull = npcLoc.toVector().subtract(targetLoc.toVector()).normalize().multiply(0.55D);
            target.setVelocity(target.getVelocity().add(pull));
            sendToTarget(session, target, "&eTaskmaster tugs the leash.");
        }
    }

    private static void handleDeliverTurnIn(ChoreSession session, Player target, Entity npc) {
        if (session.active == null || session.active.getType() != TaskType.DELIVER) {
            return;
        }
        if (!target.getWorld().equals(npc.getWorld())) {
            return;
        }
        if (target.getLocation().distance(npc.getLocation()) > 3.0D) {
            return;
        }

        Material m = session.active.getMaterialTarget();
        int need = session.active.getRequired() - session.active.getProgress();
        if (need <= 0 || m == null) {
            return;
        }

        int available = countMaterial(target, m);
        if (available <= 0) {
            return;
        }

        int moved = Math.min(need, available);
        removeMaterial(target, m, moved);
        session.active.increment(moved);
        sendToTarget(session, target, "&aDelivered " + moved + "x " + m.name().toLowerCase(Locale.ROOT) + ".");
    }

    private static void ensureTaskProgression(ChoreSession session) {
        if (session.active != null && session.active.isComplete()) {
            session.active = session.queue.pollFirst();
            if (session.active == null) {
                stop(session.target, "command.chore.completed");
                return;
            }
            announceActiveTask(session);
        }
    }

    private static void announceActiveTask(ChoreSession session) {
        Player target = Bukkit.getPlayer(session.target);
        if (target != null && target.isOnline() && session.active != null) {
            Lang.send(target, "command.chore.task", "task", session.active.describe());
        }

        Player issuer = Bukkit.getPlayer(session.issuer);
        if (issuer != null && issuer.isOnline() && session.active != null) {
            Lang.send(issuer, "command.chore.task", "task", session.active.describe());
        }
    }

    private static void cleanup(ChoreSession session) {
        Entity npc = session.npcId == null ? null : Bukkit.getEntity(session.npcId);
        if (npc != null && npc.isValid()) {
            npc.remove();
        }

        Helper.removeLeash(session.target);
        Player target = Bukkit.getPlayer(session.target);
        if (target != null && target.isOnline()) {
            target.customName(session.originalCustomName);
            target.setCustomNameVisible(session.originalCustomNameVisible);
        }
        if (leash.isCurrentlyLeashed(session.target)) {
            UUID owner = leash.getOwner(session.target);
            if (owner != null) {
                leash.resumeLeash(owner, session.target);
            }
        }
    }

    private static Entity spawnNpc(Location origin) {
        if (origin == null || origin.getWorld() == null) {
            return null;
        }
        Location spawn = origin.clone().add(origin.getDirection().normalize().multiply(2.0D));
        return origin.getWorld().spawn(spawn, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setCollidable(false);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.customName(net.kyori.adventure.text.Component.text("Taskmaster"));
            v.setCustomNameVisible(true);
        });
    }

    private static Deque<ChoreTask> generateRandomTasks(int count) {
        Deque<ChoreTask> tasks = new ArrayDeque<>();
        for (int i = 0; i < count; i++) {
            int pick = (int) (Math.random() * 4);
            if (pick == 0) {
                Material m = RANDOM_MINE[(int) (Math.random() * RANDOM_MINE.length)];
                tasks.addLast(ChoreTask.mine(m, 8 + (int) (Math.random() * 25)));
            } else if (pick == 1) {
                Material m = RANDOM_GATHER[(int) (Math.random() * RANDOM_GATHER.length)];
                tasks.addLast(ChoreTask.gather(m, 8 + (int) (Math.random() * 25)));
            } else if (pick == 2) {
                EntityType e = RANDOM_KILL[(int) (Math.random() * RANDOM_KILL.length)];
                tasks.addLast(ChoreTask.kill(e, 3 + (int) (Math.random() * 8)));
            } else {
                Material m = RANDOM_DELIVER[(int) (Math.random() * RANDOM_DELIVER.length)];
                tasks.addLast(ChoreTask.deliver(m, 4 + (int) (Math.random() * 13)));
            }
        }
        return tasks;
    }

    private static Deque<ChoreTask> generatePresetTasks(String preset, int rounds) {
        Deque<ChoreTask> tasks = new ArrayDeque<>();
        for (int i = 0; i < rounds; i++) {
            switch (preset) {
                case "builder" -> {
                    tasks.addLast(ChoreTask.mine(Material.STONE, 24));
                    tasks.addLast(ChoreTask.gather(Material.OAK_LOG, 16));
                    tasks.addLast(ChoreTask.deliver(Material.COBBLESTONE, 32));
                }
                case "hunter" -> {
                    tasks.addLast(ChoreTask.kill(EntityType.ZOMBIE, 8));
                    tasks.addLast(ChoreTask.kill(EntityType.SKELETON, 6));
                    tasks.addLast(ChoreTask.deliver(Material.ROTTEN_FLESH, 12));
                }
                case "farmer" -> {
                    tasks.addLast(ChoreTask.gather(Material.WHEAT, 24));
                    tasks.addLast(ChoreTask.gather(Material.CARROT, 16));
                    tasks.addLast(ChoreTask.deliver(Material.BREAD, 8));
                }
                case "messenger" -> {
                    tasks.addLast(ChoreTask.deliver(Material.OAK_LOG, 16));
                    tasks.addLast(ChoreTask.deliver(Material.COOKED_BEEF, 10));
                    tasks.addLast(ChoreTask.deliver(Material.COBBLESTONE, 24));
                }
                default -> {
                }
            }
        }
        return tasks;
    }

    public static ChoreTask parseCustomTask(String typeRaw, String targetRaw, int count) {
        if (typeRaw == null || targetRaw == null) {
            return null;
        }
        String type = typeRaw.toLowerCase(Locale.ROOT);
        if (type.equals("mine")) {
            Material mat = Material.matchMaterial(targetRaw.toUpperCase(Locale.ROOT));
            return mat == null ? null : ChoreTask.mine(mat, count);
        }
        if (type.equals("gather")) {
            Material mat = Material.matchMaterial(targetRaw.toUpperCase(Locale.ROOT));
            return mat == null ? null : ChoreTask.gather(mat, count);
        }
        if (type.equals("deliver")) {
            Material mat = Material.matchMaterial(targetRaw.toUpperCase(Locale.ROOT));
            return mat == null ? null : ChoreTask.deliver(mat, count);
        }
        if (type.equals("kill")) {
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(targetRaw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
            return ChoreTask.kill(entityType, count);
        }
        return null;
    }

    private static void sendToTarget(ChoreSession session, Player target, String rawMessage) {
        if (session.chatMode == ChatMode.PRIVATE) {
            target.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', rawMessage));
            return;
        }
        Bukkit.broadcastMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', rawMessage.replace("Taskmaster", "Taskmaster@" + target.getName())));
    }

    public static ChatMode parseChatMode(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "private" -> ChatMode.PRIVATE;
            case "broadcast" -> ChatMode.BROADCAST;
            default -> null;
        };
    }

    public static RenameMode parseRenameMode(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "off" -> RenameMode.OFF;
            case "on" -> RenameMode.ON;
            case "random" -> RenameMode.RANDOM;
            default -> null;
        };
    }

    public static boolean isPresetName(String raw) {
        return raw != null && PRESET_NAMES.contains(raw.toLowerCase(Locale.ROOT));
    }

    private static RenameMode defaultRenameModeFor(Player issuer, Player target) {
        if (!Settings.choreRandomRenameNonOwner()) {
            return RenameMode.OFF;
        }
        boolean ownerIssued = leash.isOwnedBy(issuer.getUniqueId(), target.getUniqueId());
        if (!ownerIssued) {
            return RenameMode.RANDOM;
        }
        return RenameMode.OFF;
    }

    private static void applyRenameMode(ChoreSession session, Player target) {
        if (session.renameMode == RenameMode.OFF) {
            target.customName(session.originalCustomName);
            target.setCustomNameVisible(session.originalCustomNameVisible);
            return;
        }

        if (session.renameMode == RenameMode.ON) {
            target.customName(Component.text("Taskbound " + target.getName()));
            target.setCustomNameVisible(true);
            return;
        }

        String adj = pick(Settings.choreRenameAdjectives());
        String noun = pick(Settings.choreRenameNouns());
        target.customName(Component.text(adj + " " + noun));
        target.setCustomNameVisible(true);
    }

    private static String pick(List<String> array) {
        if (array == null || array.isEmpty()) {
            return "Task";
        }
        return array.get((int) (Math.random() * array.size()));
    }

    private static int countMaterial(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static void removeMaterial(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
            remaining -= take;
        }
        player.getInventory().setContents(contents);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ChoreSession session = sessions.get(player.getUniqueId());
        if (session == null || session.active == null) {
            return;
        }
        if (session.active.getType() == TaskType.MINE && session.active.getMaterialTarget() == event.getBlock().getType()) {
            session.active.increment(1);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ChoreSession session = sessions.get(player.getUniqueId());
        if (session == null || session.active == null) {
            return;
        }
        if (session.active.getType() == TaskType.GATHER && session.active.getMaterialTarget() == event.getItem().getItemStack().getType()) {
            session.active.increment(event.getItem().getItemStack().getAmount());
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        ChoreSession session = sessions.get(killer.getUniqueId());
        if (session == null || session.active == null) {
            return;
        }
        if (session.active.getType() == TaskType.KILL && session.active.getEntityTarget() == event.getEntityType()) {
            session.active.increment(1);
        }
    }
}

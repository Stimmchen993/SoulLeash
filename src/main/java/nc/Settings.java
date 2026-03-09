package nc;

import org.bukkit.configuration.file.FileConfiguration;

public final class Settings {
    private static SoulLeash plugin;
    private static FileConfiguration config;

    private Settings() {
    }

    public static void init(SoulLeash soulLeash) {
        plugin = soulLeash;
        reload();
    }

    public static void reload() {
        if (plugin == null) {
            throw new IllegalStateException("Settings must be initialized before use.");
        }
        config = plugin.getConfig();
    }

    public static String permissionAdmin() {
        return config.getString("permissions.admin", "leashplayers.admin");
    }

    public static String permissionUse() {
        return config.getString("permissions.use", "leashplayers.use");
    }

    public static String permissionLeashable() {
        return config.getString("permissions.leashable", "leashplayers.leashable");
    }

    public static boolean featureLeashInteractions() {
        return config.getBoolean("features.leash-interactions", true);
    }

    public static boolean featureLeashFollowTask() {
        return config.getBoolean("features.leash-follow-task", true);
    }

    public static boolean featureLeashEffects() {
        return config.getBoolean("features.leash-effects", true);
    }

    public static boolean featureCrossWorldSync() {
        return config.getBoolean("features.cross-world-sync", true);
    }

    public static boolean featureRespawnSync() {
        return config.getBoolean("features.respawn-sync", true);
    }

    public static boolean featurePortalSync() {
        return config.getBoolean("features.portal-sync", true);
    }

    public static boolean featureFallDamageProtection() {
        return config.getBoolean("features.fall-damage-protection", true);
    }

    public static boolean featureSummonStar() {
        return config.getBoolean("features.summon-star", true);
    }

    public static boolean featureFoodShare() {
        return config.getBoolean("features.food-share", true);
    }

    public static boolean featureBoneControl() {
        return config.getBoolean("features.bone-control", true);
    }

    public static boolean featureFenceBinding() {
        return config.getBoolean("features.fence-binding", true);
    }

    public static boolean featureLookatTotem() {
        return config.getBoolean("features.lookat-totem", true);
    }

    public static int joinResyncDelayTicks() {
        return Math.max(1, config.getInt("sync.join-resync-delay-ticks", 20));
    }

    public static int portalFollowDelayTicks() {
        return Math.max(1, config.getInt("sync.portal-follow-delay-ticks", 10));
    }

    public static int leashEffectPeriodTicks() {
        return Math.max(1, config.getInt("sync.leash-effect-task-period-ticks", 40));
    }

    public static long summonCooldownSeconds() {
        return Math.max(1L, config.getLong("settings.summon-cooldown-seconds", 600L));
    }

    public static long foodShareCooldownMs() {
        return Math.max(0L, config.getLong("food-share.cooldown-ms", 1000L));
    }

    public static int leashTaskPeriodTicks() {
        return Math.max(1, config.getInt("leash.follow.task-period-ticks", 1));
    }

    public static double leashPullStartDistance() {
        return config.getDouble("leash.follow.thresholds.pull-start", 5.0);
    }

    public static double leashPullSoftDistance() {
        return config.getDouble("leash.follow.thresholds.pull-soft", 5.5);
    }

    public static double leashPullMediumDistance() {
        return config.getDouble("leash.follow.thresholds.pull-medium", 6.0);
    }

    public static double leashPullHardDistance() {
        return config.getDouble("leash.follow.thresholds.pull-hard", 7.0);
    }

    public static double leashTeleportDistance() {
        return config.getDouble("leash.follow.thresholds.teleport", 48.0);
    }

    public static double leashPullSoftStrength() {
        return config.getDouble("leash.follow.strengths.soft", 0.1);
    }

    public static double leashPullMediumStrength() {
        return config.getDouble("leash.follow.strengths.medium", 0.15);
    }

    public static double leashPullHardStrength() {
        return config.getDouble("leash.follow.strengths.hard", 0.2);
    }

    public static double leashPullGroundStrength() {
        return config.getDouble("leash.follow.strengths.ground-hard", 0.3);
    }

    public static double leashPullGroundYBoost() {
        return config.getDouble("leash.follow.strengths.ground-y-boost", 0.3);
    }

    public static boolean leashStuckEnabled() {
        return config.getBoolean("leash.follow.stuck-detector.enabled", true);
    }

    public static double leashStuckCheckDistanceMin() {
        return config.getDouble("leash.follow.stuck-detector.check-distance-min", 7.0);
    }

    public static double leashStuckMovementXZMax() {
        return config.getDouble("leash.follow.stuck-detector.movement-xz-max", 0.1);
    }

    public static double leashStuckApproachMax() {
        return config.getDouble("leash.follow.stuck-detector.approach-max", 0.1);
    }

    public static double leashStuckDeltaYMax() {
        return config.getDouble("leash.follow.stuck-detector.delta-y-max", 3.0);
    }

    public static long leashStuckTimeoutMs() {
        return Math.max(100, config.getLong("leash.follow.stuck-detector.timeout-ms", 3000L));
    }

    public static int fenceTaskPeriodTicks() {
        return Math.max(1, config.getInt("fence.follow.task-period-ticks", 1));
    }

    public static double fencePullStartDistance() {
        return config.getDouble("fence.follow.thresholds.pull-start", 5.0);
    }

    public static double fencePullSoftDistance() {
        return config.getDouble("fence.follow.thresholds.pull-soft", 5.5);
    }

    public static double fencePullMediumDistance() {
        return config.getDouble("fence.follow.thresholds.pull-medium", 6.0);
    }

    public static double fencePullHardDistance() {
        return config.getDouble("fence.follow.thresholds.pull-hard", 7.0);
    }

    public static double fenceTeleportDistance() {
        return config.getDouble("fence.follow.thresholds.teleport", 48.0);
    }

    public static double fencePullSoftStrength() {
        return config.getDouble("fence.follow.strengths.soft", 0.2);
    }

    public static double fencePullMediumStrength() {
        return config.getDouble("fence.follow.strengths.medium", 0.3);
    }

    public static double fencePullHardStrength() {
        return config.getDouble("fence.follow.strengths.hard", 0.4);
    }

    public static double fencePullGroundStrength() {
        return config.getDouble("fence.follow.strengths.ground-hard", 0.5);
    }

    public static double fencePullGroundYBoost() {
        return config.getDouble("fence.follow.strengths.ground-y-boost", 0.3);
    }
}

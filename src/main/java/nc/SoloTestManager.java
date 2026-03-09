package nc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static nc.SoulLeash.instance;

public final class SoloTestManager {
    private static final String SUBJECT_NAME = "SoulLeash Test Subject";
    private static final double MIRROR_DEFAULT_LENGTH = 6.0D;
    private static final double MIRROR_PULL_STRENGTH = 0.35D;

    private static final Map<UUID, UUID> subjectByTester = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitRunnable> mirrorTaskByTester = new ConcurrentHashMap<>();

    private SoloTestManager() {
    }

    public static Entity spawnOrMoveSubject(Player tester) {
        Entity existing = getSubject(tester.getUniqueId());
        if (existing != null && existing.isValid()) {
            Location to = tester.getLocation().add(tester.getLocation().getDirection().normalize().multiply(2.0D));
            existing.teleport(to);
            return existing;
        }

        Location spawn = tester.getLocation().add(tester.getLocation().getDirection().normalize().multiply(2.0D));
        Villager villager = tester.getWorld().spawn(spawn, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setCollidable(false);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.customName(net.kyori.adventure.text.Component.text(SUBJECT_NAME));
            v.setCustomNameVisible(true);
        });

        subjectByTester.put(tester.getUniqueId(), villager.getUniqueId());
        return villager;
    }

    public static boolean despawnSubject(UUID tester) {
        stopMirror(tester);
        UUID entityId = subjectByTester.remove(tester);
        if (entityId == null) {
            return false;
        }
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        return true;
    }

    public static void reset(UUID tester) {
        stopMirror(tester);
        despawnSubject(tester);
    }

    public static boolean startMirror(Player tester) {
        Entity subject = spawnOrMoveSubject(tester);
        if (subject == null || !subject.isValid()) {
            return false;
        }

        stopMirror(tester.getUniqueId());
        Helper.removeLeash(tester.getUniqueId());
        Helper.attachLeash(tester, subject);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                Player current = Bukkit.getPlayer(tester.getUniqueId());
                Entity currentSubject = getSubject(tester.getUniqueId());
                if (current == null || !current.isOnline() || currentSubject == null || !currentSubject.isValid()) {
                    stopMirror(tester.getUniqueId());
                    cancel();
                    return;
                }

                Location target = currentSubject.getLocation();
                Location currentLoc = current.getLocation();
                if (!target.getWorld().equals(currentLoc.getWorld())) {
                    current.teleport(target);
                    return;
                }

                double distance = target.distance(currentLoc);
                if (distance > Settings.leashTeleportDistance()) {
                    current.teleport(target);
                    return;
                }

                if (distance > MIRROR_DEFAULT_LENGTH) {
                    Vector toward = target.toVector().subtract(currentLoc.toVector()).normalize().multiply(MIRROR_PULL_STRENGTH);
                    current.setVelocity(current.getVelocity().add(toward));
                }
            }
        };

        task.runTaskTimer(instance, 0L, 2L);
        mirrorTaskByTester.put(tester.getUniqueId(), task);
        return true;
    }

    public static void stopMirror(UUID tester) {
        BukkitRunnable task = mirrorTaskByTester.remove(tester);
        if (task != null) {
            task.cancel();
        }
        Helper.removeLeash(tester);
    }

    public static void shutdown() {
        for (UUID tester : mirrorTaskByTester.keySet()) {
            stopMirror(tester);
        }
        for (UUID tester : subjectByTester.keySet()) {
            despawnSubject(tester);
        }
        mirrorTaskByTester.clear();
        subjectByTester.clear();
    }

    public static boolean tug(Player tester) {
        Entity subject = getSubject(tester.getUniqueId());
        if (subject == null || !subject.isValid()) {
            return false;
        }

        Location from = tester.getLocation();
        Location to = subject.getLocation();
        if (!from.getWorld().equals(to.getWorld())) {
            tester.teleport(to);
            return true;
        }

        double distance = from.distance(to);
        if (distance > Settings.leashTeleportDistance()) {
            tester.teleport(to);
        } else {
            Vector pull = to.toVector().subtract(from.toVector()).normalize().multiply(Settings.leashTapPullStrength());
            tester.setVelocity(tester.getVelocity().add(pull));
            Location look = tester.getLocation().clone();
            look.setDirection(to.toVector().subtract(from.toVector()));
            tester.setRotation(look.getYaw(), look.getPitch());
        }
        return true;
    }

    public static boolean hasMirror(UUID tester) {
        return mirrorTaskByTester.containsKey(tester);
    }

    public static Entity getSubject(UUID tester) {
        UUID subjectId = subjectByTester.get(tester);
        if (subjectId == null) {
            return null;
        }
        return Bukkit.getEntity(subjectId);
    }
}

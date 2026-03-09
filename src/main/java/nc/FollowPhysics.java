package nc;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class FollowPhysics {
    private FollowPhysics() {
    }

    public static Vector computePull(Location source, Location target, double distance,
                                     double pullStart, double pullSoft, double pullMedium, double pullHard,
                                     double strengthSoft, double strengthMedium, double strengthHard, double strengthGroundHard,
                                     boolean onGround) {
        if (distance <= pullStart) {
            return null;
        }

        double strength;
        if (distance > pullHard && onGround) {
            strength = strengthGroundHard;
        } else if (distance > pullMedium) {
            strength = strengthHard;
        } else if (distance > pullSoft) {
            strength = strengthMedium;
        } else {
            strength = strengthSoft;
        }

        return target.toVector().subtract(source.toVector()).normalize().multiply(strength);
    }

    public static void applyGroundBoost(Player player, double yBoost) {
        player.setVelocity(player.getVelocity().setY(yBoost));
    }
}

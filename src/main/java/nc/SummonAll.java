package nc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import static nc.SoulLeash.leashMap;

public class SummonAll implements Listener {

    private final Map<UUID, Long> cooldownMap = new HashMap<>();

    @EventHandler
    public void onUseStar(PlayerInteractEvent e) {
        if (!Settings.featureSummonStar()) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType() != Material.NETHER_STAR) return;

        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMs = getCooldownMillis();

        if (cooldownMap.containsKey(id) && now - cooldownMap.get(id) < cooldownMs) {
            long left = (cooldownMs - (now - cooldownMap.get(id))) / 1000;
            Lang.send(p, "summon.cooldown", "seconds", left);
            return;
        }

        List<UUID> list = leashMap.get(id);
        if (list == null || list.isEmpty()) {
            return;
        }

        Location loc = p.getLocation();

        for (UUID uid : list) {
            Player f = Bukkit.getPlayer(uid);
            if (f != null && f.isOnline()) {
                f.teleport(loc);
//                Helper.attachLeash(f, p);
                f.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            }
        }

        cooldownMap.put(id, now);
        Lang.send(p, "summon.success");
    }

    private long getCooldownMillis() {
        return Settings.summonCooldownSeconds() * 1000L;
    }
}

# SoulLeash
Minecraft 1.21 Plugin (Paper)

Allows players to be leashed!


Right-click a player with a lead to leash them.

The owner can remove the leash by using a sword.

The owner can make the leashed player look at them by using a Totem of Undying.

The owner can teleport the leashed player to themselves by using a Nether Star.

The owner can also tie the leashed player to a fence.



Leashed players will receive a regeneration effect.

The owner can also feed food items to their leashed players.



A distance check is included:


If the player is more than 48 blocks away, they will be teleported directly.

If the distance exceeds 10 blocks, a warning will be shown.


Permissions (none are granted by default, recommend using LuckPerms):

leashplayers.use — Allows using leads on players

leashplayers.leashable — Allows being leashed by others

## New in Stardust overhaul (local branch)

- Reloadable language files in `plugins/SouiLeash/lang/` (`en_US`, `de_DE`)
- Configurable permissions in `config.yml`:
  - `permissions.admin`
  - `permissions.use`
  - `permissions.leashable`
- Feature toggles in `config.yml` under `features.*`
- Tunable timings and leash/fence behavior in `sync.*`, `leash.*`, `fence.*`, `food-share.*`
- Shared follow physics extracted to `FollowPhysics` for easier maintenance
- Legacy unregistered duplicate listener path (`Listeners.java`) removed

Admin command:

- `/leashplayers reload`
- `/leashplayers lang <en_US|de_DE>`
- `/leashplayers status`
- `/leashplayers debug`
- `/leashplayers select <player>`
- `/leashplayers temp <player>`
- `/leashplayers permanent <player>`
- `/leashplayers length <player> <value> [set|add]`
- `/leashplayers anchor <player> <owner|entity|block>`
- `/leashplayers clearname [player]`
- `/leashplayers test spawn`
- `/leashplayers test despawn`
- `/leashplayers test reset`
- `/leashplayers test run basic`
- `/leashplayers test mirror <start|stop|tug>`

## Build (local)

- Recommended one-liner (works without local Maven):
  - `./scripts/build.sh`
- If `target/` is root-owned, the script automatically builds to `.build/target`.


# SoulLeash

minecraft 1.21 plugins！（paper）

  允许对玩家使用拴绳


使用拴绳对玩家右键即可

主人使用剑可以解除

主人使用不死图腾可以让玩家看自己

主人使用下届之星可以让玩家传送过来

主人也可以把玩家拴在栅栏上


被拴住的玩家会获得生命恢复效果

主人还可以使用吃的喂给自己的玩家


添加距离判断，超过48格将直接传送

超过10格会给提示


权限：默认都没有权限，建议通过LP插件添加
  
  leashplayers.use 允许使用拴绳
  
  leashplayers.leashable 允许被使用拴绳

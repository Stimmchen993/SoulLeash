package nc;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class SoulLeash extends JavaPlugin {
    public static SoulLeash instance;
    public static final Map<UUID, List<UUID>> leashMap = new HashMap<>();
    public static final Map<UUID, BukkitRunnable> leashTasks = new HashMap<>();
    private File leashDataFile;
    public static FileConfiguration leashDataConfig;
    private static Fence fence;
    public static final Map<UUID, List<UUID>> fenceLeashMap = new HashMap<>();
    private static File fenceLeashFile;
    public static FileConfiguration fenceLeashDataConfig;
    public static Map<UUID, List<String>> fenceData = new HashMap<>();


    @Override
    public void onEnable() {
        if (instance != null) {
            throw new IllegalStateException("Plugin already initialized!"); // 如果已初始化则抛出异常
        }
        instance = this;  // 插件实例化
        saveDefaultConfig(); // 保存默认配置
        Settings.init(this);
        Lang.init(this);

        // 初始化所有必要的数据
        initializeLeashData();  // 初始化配置文件
        initializeFenceLeashData();
        initializeManagers();


        // 加载数据
        loadLeashData();  // 现在加载数据
        leash.rebuildOwnershipIndex();
        leash.loadAdvancedState();
        leash.startOptOutComplianceTask();

        // 注册事件
        registerEvents();

        // 命令注册
        Executors executor = new Executors(this);
        Objects.requireNonNull(getCommand("leashplayers")).setExecutor(executor);
        Objects.requireNonNull(getCommand("leashplayers")).setTabCompleter(executor);


        // 启动任务
        task.startLeashEffectTask(this);
        Helper leashHelper = new Helper(this);


        getServer().getPluginManager().registerEvents(new FoodShare(this), this);
        getServer().getPluginManager().registerEvents(new BoneControl(), this);
        getServer().getPluginManager().registerEvents(new SummonAll(), this);





        getLogger().info(Lang.plain("plugin.enabled"));
    }


    @Override
    public void onDisable() {
        SoloTestManager.shutdown();
        if (fence != null) {
            fence.saveFenceLeashData();
        }
        saveLeashData();
        getLogger().info(Lang.plain("plugin.disabled"));

    }

    // 初始化 leash 数据文件
    void initializeLeashData() {
        leashDataFile = new File(getDataFolder(), "leash_data.yml");
        if (!leashDataFile.exists()) {
            try {
                leashDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        leashDataConfig = YamlConfiguration.loadConfiguration(leashDataFile);
    }

    // 初始化 fence 数据文件
    private void initializeFenceLeashData() {
        fenceLeashFile = new File(getDataFolder(), "fence_leash_data.yml");
        if (!fenceLeashFile.exists()) {
            try {
                fenceLeashFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        fenceLeashDataConfig = YamlConfiguration.loadConfiguration(fenceLeashFile);
    }

    // 初始化管理器
    private void initializeManagers() {
        if (fence == null) {
            fence = new Fence(this);  // 传递插件实例
            fence.loadFenceLeashData();
        }
    }

    // 注册事件
    private void registerEvents() {
        getServer().getPluginManager().registerEvents(new Lookat(), this);
        getServer().getPluginManager().registerEvents(new leash(), this);
        getServer().getPluginManager().registerEvents(new dimension(), this);
        getServer().getPluginManager().registerEvents(new kill(), this);
        getServer().getPluginManager().registerEvents(new task(), this);
    }

    // 加载绑定数据
    public void loadLeashData() {
        if (leashDataConfig == null) {
            getLogger().warning("leashDataConfig is not initialized!");
            return;
        }
        leashMap.clear();

        if (leashDataConfig.isConfigurationSection("state.owners")) {
            leashDataConfig.getConfigurationSection("state.owners").getKeys(false).forEach(key -> {
                try {
                    UUID owner = UUID.fromString(key);
                    List<UUID> followers = leashDataConfig.getStringList("state.owners." + key).stream()
                            .map(UUID::fromString)
                            .collect(Collectors.toList());
                    leashMap.put(owner, followers);
                } catch (IllegalArgumentException ignored) {
                }
            });
            return;
        }

        leashDataConfig.getKeys(false).forEach(key -> {
            if (key.equals("state") || key.equals("pendingTeleport") || key.equals("fence_bounds")) {
                return;
            }
            try {
                UUID owner = UUID.fromString(key);
                List<UUID> followers = leashDataConfig.getStringList(key).stream()
                        .map(UUID::fromString)
                        .collect(Collectors.toList());
                leashMap.put(owner, followers);
            } catch (IllegalArgumentException ignored) {
            }
        });
    }

    public void saveLeashData() {
        if (leashDataConfig == null) {
            getLogger().warning("leashDataConfig is not initialized!");
            return;
        }
        leashMap.forEach((uuid, mUUIDs) -> {
            leashDataConfig.set(uuid.toString(), mUUIDs.stream().map(UUID::toString).collect(Collectors.toList()));
        });
        leashDataConfig.set("state.owners", null);
        leashMap.forEach((uuid, mUUIDs) -> {
            leashDataConfig.set("state.owners." + uuid, mUUIDs.stream().map(UUID::toString).collect(Collectors.toList()));
        });
        try {
            leashDataConfig.save(leashDataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 获取插件实例
    public static SoulLeash getInstance() {
        return instance;
    }

    // 获取 Fence 实例
    public static Fence getFenceLeashManager() {
        return fence;
    }

    public static int getPendingTeleportCount() {
        if (leashDataConfig == null || !leashDataConfig.isConfigurationSection("pendingTeleport")) {
            return 0;
        }
        return leashDataConfig.getConfigurationSection("pendingTeleport").getKeys(false).size();
    }

}

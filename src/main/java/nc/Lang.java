package nc;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public final class Lang {
    private static final String FALLBACK_LANGUAGE = "en_US";

    private static SoulLeash plugin;
    private static FileConfiguration langConfig;
    private static FileConfiguration fallbackConfig;
    private static String currentLanguage = FALLBACK_LANGUAGE;
    private static final Set<String> missingKeys = new HashSet<>();

    private Lang() {
    }

    public static void init(SoulLeash soulLeash) {
        plugin = soulLeash;
        ensureLanguageResources();
        reload();
    }

    public static void reload() {
        if (plugin == null) {
            throw new IllegalStateException("Lang must be initialized before use.");
        }

        String requested = plugin.getConfig().getString("language", FALLBACK_LANGUAGE);
        currentLanguage = requested == null || requested.isBlank() ? FALLBACK_LANGUAGE : requested;

        File languageFile = new File(plugin.getDataFolder(), "lang/" + currentLanguage + ".yml");
        if (!languageFile.exists()) {
            plugin.getLogger().warning("Language file not found for '" + currentLanguage + "', falling back to " + FALLBACK_LANGUAGE + ".");
            currentLanguage = FALLBACK_LANGUAGE;
            languageFile = new File(plugin.getDataFolder(), "lang/" + currentLanguage + ".yml");
        }

        langConfig = YamlConfiguration.loadConfiguration(languageFile);
        fallbackConfig = loadFromResource("lang/" + FALLBACK_LANGUAGE + ".yml");
        missingKeys.clear();
    }

    public static String getCurrentLanguage() {
        return currentLanguage;
    }

    public static String tr(String key, Object... placeholders) {
        String value = getRawValue(key);
        value = applyPlaceholders(value, placeholders);
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public static String plain(String key, Object... placeholders) {
        return ChatColor.stripColor(tr(key, placeholders));
    }

    public static void send(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(tr(key, placeholders));
    }

    private static String getRawValue(String key) {
        String value = langConfig.getString(key);
        if (value != null) {
            return value;
        }

        String fallback = fallbackConfig.getString(key);
        if (fallback != null) {
            if (missingKeys.add(key)) {
                plugin.getLogger().warning("Missing translation key '" + key + "' in " + currentLanguage + ". Using fallback.");
            }
            return fallback;
        }

        if (missingKeys.add(key)) {
            plugin.getLogger().warning("Missing translation key '" + key + "' in both " + currentLanguage + " and fallback language.");
        }
        return key;
    }

    private static String applyPlaceholders(String value, Object... placeholders) {
        if (placeholders == null || placeholders.length == 0) {
            return value;
        }

        if (placeholders.length % 2 != 0) {
            plugin.getLogger().warning("Invalid placeholder pairs supplied for message: " + value);
            return value;
        }

        String output = value;
        for (int i = 0; i < placeholders.length; i += 2) {
            String token = String.valueOf(placeholders[i]);
            String replacement = String.valueOf(placeholders[i + 1]);
            output = output.replace("{" + token + "}", replacement);
        }
        return output;
    }

    private static void ensureLanguageResources() {
        saveIfMissing("lang/en_US.yml");
        saveIfMissing("lang/de_DE.yml");
    }

    private static void saveIfMissing(String path) {
        File target = new File(plugin.getDataFolder(), path);
        if (!target.exists()) {
            plugin.saveResource(path, false);
        }
    }

    private static FileConfiguration loadFromResource(String path) {
        InputStream stream = plugin.getResource(path);
        if (stream == null) {
            throw new IllegalStateException("Missing bundled language resource: " + path);
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load language resource: " + path, ex);
        }
    }
}

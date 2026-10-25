package de.raindancer.modules.wrapper;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Which of the plugins a plugin requires are not installed — asked by its bootstrapper, before it registers anything.
 *
 * <p>Paper runs every bootstrapper before it checks the plugins' required dependencies. A plugin that then fails the
 * check (RainsHungerGames without WorldEdit) is never loaded, but the commands its bootstrapper registered stay in
 * the tree, owned by a plugin that does not exist; building the help index from them throws and the whole server
 * stops during startup — one missing jar took every plugin with it. Paper offers no way to see the other plugins
 * during bootstrap, and when the commands are registered there is no server yet to ask, so this reads the plugins
 * folder itself: every jar's {@code plugin.yml} or {@code paper-plugin.yml}, its name and what it {@code provides}.
 *
 * <p>Cautious in one direction only: anything unreadable counts as present. A wrong "missing" would silently take a
 * working plugin's commands away; a wrong "present" is just the old behaviour.
 */
final class RequiredPlugins {

    private RequiredPlugins() {
    }

    /** The names in {@code required} that no jar in {@code folder} is or provides, case-blind. */
    static List<String> missing(List<String> required, Path folder) {
        if (required == null || required.isEmpty()) {
            return List.of();
        }
        Set<String> installed;
        try {
            installed = installed(folder);
        } catch (IOException unreadable) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (String name : required) {
            if (!installed.contains(name.toLowerCase(Locale.ROOT))) {
                missing.add(name);
            }
        }
        return List.copyOf(missing);
    }

    /**
     * What the plugin in {@code jar} requires on the server: its {@code paper-plugin.yml}'s {@code dependencies.server}
     * entries whose {@code required} is not false (Paper's default is true). Read from the jar because Paper's own
     * {@code PluginMeta} does not report them during bootstrap.
     */
    static List<String> requiredBy(Path jar) {
        List<String> required = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("paper-plugin.yml");
            if (entry == null) {
                return List.of();
            }
            try (InputStream in = zip.getInputStream(entry)) {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                var server = yaml.getConfigurationSection("dependencies.server");
                if (server != null) {
                    for (String name : server.getKeys(false)) {
                        if (server.getBoolean(name + ".required", true)) {
                            required.add(name);
                        }
                    }
                }
            }
        } catch (Exception unreadable) {
            return List.of();
        }
        return List.copyOf(required);
    }

    private static Set<String> installed(Path folder) throws IOException {
        Set<String> names = new HashSet<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path jar : files.filter(path -> path.getFileName().toString().endsWith(".jar")).toList()) {
                read(jar, names);
            }
        }
        return names;
    }

    private static void read(Path jar, Set<String> names) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String descriptor : List.of("paper-plugin.yml", "plugin.yml")) {
                ZipEntry entry = zip.getEntry(descriptor);
                if (entry == null) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    YamlConfiguration yaml = new YamlConfiguration();
                    yaml.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    String name = yaml.getString("name");
                    if (name != null) {
                        names.add(name.toLowerCase(Locale.ROOT));
                    }
                    yaml.getStringList("provides").forEach(other -> names.add(other.toLowerCase(Locale.ROOT)));
                } catch (Exception unreadable) {
                    // Not a plugin, or not one we can read: it neither proves nor disproves anything.
                }
            }
        } catch (IOException notAJar) {
            // Same.
        }
    }
}

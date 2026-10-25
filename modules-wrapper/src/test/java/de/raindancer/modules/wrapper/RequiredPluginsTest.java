package de.raindancer.modules.wrapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class RequiredPluginsTest {

    @TempDir
    Path plugins;

    private void jar(String file, String descriptor, String yaml) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(plugins.resolve(file)))) {
            out.putNextEntry(new JarEntry(descriptor));
            out.write(yaml.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }

    @Test
    @DisplayName("what is installed is read from every jar's plugin.yml or paper-plugin.yml, names and provides")
    void installed() throws IOException {
        jar("RainsCore-2.5.0.jar", "paper-plugin.yml", "name: RainsCore\nversion: '2.5.0'\nmain: x.Y\n");
        jar("worldedit-bukkit.jar", "plugin.yml", "name: WorldEdit\nversion: 7\nmain: a.B\nprovides: [FastAsyncWorldEdit]\n");
        Files.writeString(plugins.resolve("notes.txt"), "not a jar");
        jar("broken.jar", "plugin.yml", ": : not yaml [");

        assertThat(RequiredPlugins.missing(List.of("RainsCore", "worldedit"), plugins)).isEmpty();
        assertThat(RequiredPlugins.missing(List.of("RainsCore", "voicechat"), plugins)).containsExactly("voicechat");
        assertThat(RequiredPlugins.missing(List.of("FastAsyncWorldEdit"), plugins)).isEmpty();
    }

    @Test
    @DisplayName("a folder that cannot be read rules nothing out")
    void unreadable() {
        assertThat(RequiredPlugins.missing(List.of("WorldEdit"), plugins.resolve("nowhere"))).isEmpty();
    }

    @Test
    @DisplayName("a plugin's own requirements are its server dependencies marked required, read from its jar")
    void requiredOfAJar() throws IOException {
        jar("RainsHungerGames.jar", "paper-plugin.yml", """
                name: RainsHungerGames
                dependencies:
                  bootstrap:
                    RainsCore: { load: BEFORE, required: true, join-classpath: true }
                  server:
                    RainsCore: { load: BEFORE, required: true, join-classpath: true }
                    WorldEdit: { load: BEFORE, required: true }
                    Vault: { load: BEFORE, required: false }
                    PlaceholderAPI: { load: BEFORE }
                """);
        assertThat(RequiredPlugins.requiredBy(plugins.resolve("RainsHungerGames.jar")))
                .containsExactlyInAnyOrder("RainsCore", "WorldEdit", "PlaceholderAPI");
    }
}

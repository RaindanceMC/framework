package de.raindancer.modules.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The plain-tone half of {@link WordingContract}, run against a module that breaks it and one that keeps it. */
class PlainWordingContractTest {

    private static WordingContract module(String which) {
        Path root = Path.of("src/test/resources/plain-wording", which, "src/main");
        return new WordingContract() {
            public Path moduleSource() {
                return root.resolve("java/de/raindancer/modules/demo");
            }

            public Path messagesFile() {
                return root.resolve("resources/de/raindancer/modules/demo/messages.yml");
            }
        };
    }

    @Test
    @DisplayName("the rules catch dashes, emoji, shouting and cheer, and leave regexes and ranges alone")
    void rules() {
        assertThat(WordingContract.plainProblems("Saved — welcome back", false)).containsExactly("dash");
        assertThat(WordingContract.plainProblems("Saved - welcome back", false)).containsExactly("dash");
        assertThat(WordingContract.plainProblems("Done!! 🎉", false)).containsExactly("emoji", "!!/?!");
        assertThat(WordingContract.plainProblems("Oops, no.", true)).containsExactly("'Oops'");
        assertThat(WordingContract.plainProblems("(?![a-z]) 1-10 well-known", true)).isEmpty();
    }

    @Test
    @DisplayName("a module that breaks the plain tone fails every one of its checks")
    void badFails() {
        WordingContract bad = module("bad");
        assertThatThrownBy(bad::wordingIsPlain).hasMessageContaining("saved");
        assertThatThrownBy(bad::oneToneOnly).hasMessageContaining("saved");
        assertThatThrownBy(bad::helpIsPlain).hasMessageContaining("demo.yml");
        assertThatThrownBy(bad::sourceStringsArePlain).hasMessageContaining("Done!!");
    }

    @Test
    @DisplayName("a plain module passes, joke lists and comments included")
    void goodPasses() {
        WordingContract good = module("good");
        good.wordingIsPlain();
        good.oneToneOnly();
        good.helpIsPlain();
        good.sourceStringsArePlain();
    }

    @Test
    @DisplayName("a module needs no messages-serious.yml and no seriousFrom call any more")
    void noSeriousWording() {
        assertThat(Arrays.stream(WordingContract.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("serious"));
        module("good").oneToneOnly();
    }
}

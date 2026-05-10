package kr.daboyeo.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RootDotenvLoaderTests {

    @Test
    void findsRootDotenvByWalkingUpParents() throws IOException {
        Path root = createTempTree("root-dotenv-find");
        try {
            Path repo = Files.createDirectories(root.resolve("repo"));
            Path backend = Files.createDirectories(repo.resolve("backend"));
            Path nested = Files.createDirectories(backend.resolve("build").resolve("tmp"));
            Files.writeString(repo.resolve(".env"), "DABOYEO_DB_USERNAME=root\n");

            Path found = RootDotenvLoader.findDotenvPath(nested);

            assertThat(found).isEqualTo(repo.resolve(".env").toAbsolutePath());
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    void loadsQuotedValuesAndSkipsComments() throws IOException {
        Path root = createTempTree("root-dotenv-load");
        try {
            Path repo = Files.createDirectories(root.resolve("repo"));
            Files.writeString(
                repo.resolve(".env"),
                """
                # comment
                DABOYEO_DB_URL="jdbc:mysql://example:4000/daboyeo"
                DABOYEO_DB_USERNAME='root'
                DABOYEO_DB_PASSWORD=secret

                INVALID_LINE
                """
            );

            Map<String, Object> values = RootDotenvLoader.load(repo.resolve("backend"));

            assertThat(values)
                .containsEntry("DABOYEO_DB_URL", "jdbc:mysql://example:4000/daboyeo")
                .containsEntry("DABOYEO_DB_USERNAME", "root")
                .containsEntry("DABOYEO_DB_PASSWORD", "secret")
                .doesNotContainKey("INVALID_LINE");
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    void stripsUtf8BomFromFirstKey() throws IOException {
        Path root = createTempTree("root-dotenv-bom");
        try {
            Path repo = Files.createDirectories(root.resolve("repo"));
            Files.writeString(
                repo.resolve(".env"),
                "\ufeffTIDB_HOST=gateway01.ap-northeast-1.prod.aws.tidbcloud.com\nTIDB_DATABASE=daboyeo_dev\n"
            );

            Map<String, Object> values = RootDotenvLoader.load(repo.resolve("backend"));

            assertThat(values)
                .containsEntry("TIDB_HOST", "gateway01.ap-northeast-1.prod.aws.tidbcloud.com")
                .containsEntry("TIDB_DATABASE", "daboyeo_dev")
                .doesNotContainKey("\ufeffTIDB_HOST");
        } finally {
            deleteRecursively(root);
        }
    }

    private static Path createTempTree(String prefix) throws IOException {
        Path parent = Files.createDirectories(Path.of("build", "tmp", "tests"));
        return Files.createTempDirectory(parent, prefix);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }
}

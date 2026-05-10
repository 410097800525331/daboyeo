package kr.daboyeo.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class RootDotenvEnvironmentPostProcessorTests {

    @Test
    void dotenvValuesAreAvailableWithoutOverridingExistingEnvironmentValues() throws IOException {
        Path root = createTempTree("root-dotenv-env");
        try {
            Path repo = Files.createDirectories(root.resolve("repo"));
            Path backend = Files.createDirectories(repo.resolve("backend"));
            Files.writeString(
                repo.resolve(".env"),
                """
                DABOYEO_DB_URL=jdbc:mysql://dotenv-host:4000/daboyeo
                DABOYEO_DB_USERNAME=dotenv-user
                """
            );

            StandardEnvironment environment = new StandardEnvironment();
            environment.getPropertySources().addFirst(
                new MapPropertySource(
                    "testOverrides",
                    Map.of("DABOYEO_DB_USERNAME", "os-user")
                )
            );

            String originalStartDir = System.getProperty("daboyeo.dotenv.start-dir");
            try {
                System.setProperty("daboyeo.dotenv.start-dir", backend.toString());
                new RootDotenvEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());
            } finally {
                if (originalStartDir == null) {
                    System.clearProperty("daboyeo.dotenv.start-dir");
                } else {
                    System.setProperty("daboyeo.dotenv.start-dir", originalStartDir);
                }
            }

            assertThat(environment.getProperty("DABOYEO_DB_URL")).isEqualTo("jdbc:mysql://dotenv-host:4000/daboyeo");
            assertThat(environment.getProperty("DABOYEO_DB_USERNAME")).isEqualTo("os-user");
            assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:mysql://dotenv-host:4000/daboyeo");
            assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("os-user");
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    void derivesSpringDatabaseSettingsFromTidbValues() throws IOException {
        Path root = createTempTree("root-dotenv-tidb");
        try {
            Path repo = Files.createDirectories(root.resolve("repo"));
            Path backend = Files.createDirectories(repo.resolve("backend"));
            Files.writeString(
                repo.resolve(".env"),
                """
                TIDB_HOST=gateway01.ap-northeast-1.prod.aws.tidbcloud.com
                TIDB_PORT=4000
                TIDB_USER=demo.root
                TIDB_PASSWORD=secret
                TIDB_DATABASE=daboyeo_dev
                """
            );

            StandardEnvironment environment = postProcessFrom(backend);

            assertThat(environment.getProperty("DABOYEO_DB_URL"))
                .isEqualTo("jdbc:mysql://gateway01.ap-northeast-1.prod.aws.tidbcloud.com:4000/daboyeo_dev?serverTimezone=Asia/Seoul&characterEncoding=utf8&useSSL=true&connectTimeout=8000&socketTimeout=8000");
            assertThat(environment.getProperty("DABOYEO_DB_USERNAME")).isEqualTo("demo.root");
            assertThat(environment.getProperty("DABOYEO_DB_PASSWORD")).isEqualTo("secret");
            assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:mysql://gateway01.ap-northeast-1.prod.aws.tidbcloud.com:4000/daboyeo_dev?serverTimezone=Asia/Seoul&characterEncoding=utf8&useSSL=true&connectTimeout=8000&socketTimeout=8000");
            assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("demo.root");
            assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("secret");
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    void explicitDaboyeoDatabaseSettingsWinOverDerivedTidbValues() throws IOException {
        Path root = createTempTree("root-dotenv-explicit-db");
        try {
            Path repo = Files.createDirectories(root.resolve("repo"));
            Path backend = Files.createDirectories(repo.resolve("backend"));
            Files.writeString(
                repo.resolve(".env"),
                """
                TIDB_HOST=gateway01.ap-northeast-1.prod.aws.tidbcloud.com
                TIDB_PORT=4000
                TIDB_USER=tidb-user
                TIDB_PASSWORD=tidb-secret
                TIDB_DATABASE=daboyeo_dev
                DABOYEO_DB_URL=jdbc:mysql://explicit-host:3306/explicit_db
                DABOYEO_DB_USERNAME=explicit-user
                DABOYEO_DB_PASSWORD=explicit-secret
                """
            );

            StandardEnvironment environment = postProcessFrom(backend);

            assertThat(environment.getProperty("DABOYEO_DB_URL")).isEqualTo("jdbc:mysql://explicit-host:3306/explicit_db");
            assertThat(environment.getProperty("DABOYEO_DB_USERNAME")).isEqualTo("explicit-user");
            assertThat(environment.getProperty("DABOYEO_DB_PASSWORD")).isEqualTo("explicit-secret");
            assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:mysql://explicit-host:3306/explicit_db");
            assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("explicit-user");
            assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("explicit-secret");
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    void nonTidbCloudHostsCanDisableSslForLocalMysql() throws IOException {
        Path root = createTempTree("root-dotenv-local-mysql");
        try {
            Path repo = Files.createDirectories(root.resolve("repo"));
            Path backend = Files.createDirectories(repo.resolve("backend"));
            Files.writeString(
                repo.resolve(".env"),
                """
                TIDB_HOST=127.0.0.1
                TIDB_PORT=3306
                TIDB_USER=root
                TIDB_PASSWORD=
                TIDB_DATABASE=daboyeo
                TIDB_SSL=false
                """
            );

            StandardEnvironment environment = postProcessFrom(backend);

            assertThat(environment.getProperty("DABOYEO_DB_URL"))
                .isEqualTo("jdbc:mysql://127.0.0.1:3306/daboyeo?serverTimezone=Asia/Seoul&characterEncoding=utf8&useSSL=false&connectTimeout=8000&socketTimeout=8000");
            assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:mysql://127.0.0.1:3306/daboyeo?serverTimezone=Asia/Seoul&characterEncoding=utf8&useSSL=false&connectTimeout=8000&socketTimeout=8000");
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    void directSpringDatasourceOverrideWinsOverDotenvAlias() throws IOException {
        Path root = createTempTree("root-dotenv-spring-datasource-override");
        try {
            Path repo = Files.createDirectories(root.resolve("repo"));
            Path backend = Files.createDirectories(repo.resolve("backend"));
            Files.writeString(
                repo.resolve(".env"),
                """
                TIDB_HOST=gateway01.ap-northeast-1.prod.aws.tidbcloud.com
                TIDB_PORT=4000
                TIDB_USER=tidb-user
                TIDB_PASSWORD=tidb-secret
                TIDB_DATABASE=daboyeo_dev
                """
            );

            StandardEnvironment environment = new StandardEnvironment();
            environment.getPropertySources().addFirst(
                new MapPropertySource(
                    "testOverrides",
                    Map.of("spring.datasource.url", "jdbc:mysql://override-host:3306/override_db")
                )
            );
            postProcessFrom(environment, backend);

            assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:mysql://override-host:3306/override_db");
        } finally {
            deleteRecursively(root);
        }
    }

    private static Path createTempTree(String prefix) throws IOException {
        Path parent = Files.createDirectories(Path.of("build", "tmp", "tests"));
        return Files.createTempDirectory(parent, prefix);
    }

    private static StandardEnvironment postProcessFrom(Path startDirectory) {
        StandardEnvironment environment = new StandardEnvironment();
        postProcessFrom(environment, startDirectory);
        return environment;
    }

    private static void postProcessFrom(StandardEnvironment environment, Path startDirectory) {
        String originalStartDir = System.getProperty("daboyeo.dotenv.start-dir");
        try {
            System.setProperty("daboyeo.dotenv.start-dir", startDirectory.toString());
            new RootDotenvEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());
        } finally {
            if (originalStartDir == null) {
                System.clearProperty("daboyeo.dotenv.start-dir");
            } else {
                System.setProperty("daboyeo.dotenv.start-dir", originalStartDir);
            }
        }
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

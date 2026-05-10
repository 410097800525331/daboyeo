package kr.daboyeo.backend.config;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

public class RootDotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "daboyeoRootDotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path workingDirectory = Path.of(System.getProperty("daboyeo.dotenv.start-dir", System.getProperty("user.dir", ".")));
        Map<String, Object> dotenvValues = new LinkedHashMap<>(RootDotenvLoader.load(workingDirectory));
        addDerivedTidbValues(environment, dotenvValues);
        addSpringDatasourceAliases(environment, dotenvValues);
        if (dotenvValues.isEmpty()) {
            return;
        }

        addDotenvPropertySource(environment, new MapPropertySource(PROPERTY_SOURCE_NAME, dotenvValues));
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    private static void addDerivedTidbValues(ConfigurableEnvironment environment, Map<String, Object> values) {
        String host = effectiveValue(environment, values, "TIDB_HOST");
        String database = effectiveValue(environment, values, "TIDB_DATABASE");
        if (!hasText(host) || !hasText(database)) {
            return;
        }

        String port = hasText(effectiveValue(environment, values, "TIDB_PORT"))
            ? effectiveValue(environment, values, "TIDB_PORT")
            : "4000";
        if (!hasText(effectiveValue(environment, values, "DABOYEO_DB_URL"))) {
            values.put("DABOYEO_DB_URL", buildJdbcUrl(host, port, database, effectiveValue(environment, values, "TIDB_SSL")));
        }
        if (!hasText(effectiveValue(environment, values, "DABOYEO_DB_USERNAME")) && hasText(effectiveValue(environment, values, "TIDB_USER"))) {
            values.put("DABOYEO_DB_USERNAME", effectiveValue(environment, values, "TIDB_USER"));
        }
        if (!hasText(effectiveValue(environment, values, "DABOYEO_DB_PASSWORD")) && hasText(effectiveValue(environment, values, "TIDB_PASSWORD"))) {
            values.put("DABOYEO_DB_PASSWORD", effectiveValue(environment, values, "TIDB_PASSWORD"));
        }
    }

    private static void addSpringDatasourceAliases(ConfigurableEnvironment environment, Map<String, Object> values) {
        putAliasIfAbsent(environment, values, "DABOYEO_DB_URL", "spring.datasource.url");
        putAliasIfAbsent(environment, values, "DABOYEO_DB_USERNAME", "spring.datasource.username");
        putAliasIfAbsent(environment, values, "DABOYEO_DB_PASSWORD", "spring.datasource.password");
    }

    private static void putAliasIfAbsent(
        ConfigurableEnvironment environment,
        Map<String, Object> values,
        String sourceKey,
        String targetKey
    ) {
        if (hasHighPriorityProperty(environment, targetKey)) {
            return;
        }
        String sourceValue = effectiveValue(environment, values, sourceKey);
        if (hasText(sourceValue)) {
            values.put(targetKey, sourceValue);
        }
    }

    private static void addDotenvPropertySource(ConfigurableEnvironment environment, MapPropertySource propertySource) {
        MutablePropertySources propertySources = environment.getPropertySources();
        if (propertySources.contains(PROPERTY_SOURCE_NAME)) {
            propertySources.replace(PROPERTY_SOURCE_NAME, propertySource);
            return;
        }
        if (propertySources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            propertySources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource);
            return;
        }
        if (propertySources.contains(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)) {
            propertySources.addAfter(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, propertySource);
            return;
        }
        propertySources.addFirst(propertySource);
    }

    private static String buildJdbcUrl(String host, String port, String database, String sslValue) {
        boolean useSsl = shouldUseSsl(host, sslValue);
        return "jdbc:mysql://%s:%s/%s?serverTimezone=Asia/Seoul&characterEncoding=utf8&useSSL=%s&connectTimeout=8000&socketTimeout=8000"
            .formatted(host.trim(), port.trim(), database.trim(), Boolean.toString(useSsl));
    }

    private static boolean shouldUseSsl(String host, String sslValue) {
        if (isTidbCloudHost(host)) {
            return true;
        }
        return hasText(sslValue) && Boolean.parseBoolean(sslValue.trim());
    }

    private static boolean isTidbCloudHost(String host) {
        return hasText(host) && host.toLowerCase(Locale.ROOT).contains("tidbcloud.com");
    }

    private static String value(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString();
    }

    private static String effectiveValue(ConfigurableEnvironment environment, Map<String, Object> values, String key) {
        String environmentValue = environment.getProperty(key);
        return hasText(environmentValue) ? environmentValue : value(values, key);
    }

    private static boolean hasHighPriorityProperty(ConfigurableEnvironment environment, String key) {
        return hasText(highPriorityValue(environment, key));
    }

    private static String highPriorityValue(ConfigurableEnvironment environment, String key) {
        String systemPropertyValue = propertySourceValue(environment, StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, key);
        if (hasText(systemPropertyValue)) {
            return systemPropertyValue;
        }
        return propertySourceValue(environment, StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, key);
    }

    private static String propertySourceValue(ConfigurableEnvironment environment, String propertySourceName, String key) {
        PropertySource<?> propertySource = environment.getPropertySources().get(propertySourceName);
        if (propertySource == null || !propertySource.containsProperty(key)) {
            return "";
        }
        Object value = propertySource.getProperty(key);
        return value == null ? "" : value.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

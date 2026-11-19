package dev.langchain4j.wildfly.opa;

import dev.langchain4j.cdi.core.config.spi.LLMConfig;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * {@link LLMConfig} backed by {@code /llm.properties} (classpath) with JVM system properties taking precedence, so
 * the application needs neither MicroProfile Config nor any other configuration library.
 *
 * <p>Registered in {@code META-INF/services/dev.langchain4j.cdi.core.config.spi.LLMConfig}.
 */
public class PropertiesLLMConfig extends LLMConfig {

    private static final String RESOURCE = "/llm.properties";

    private final Properties properties = new Properties();

    @Override
    public void init() {
        try (InputStream is = PropertiesLLMConfig.class.getResourceAsStream(RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException(RESOURCE + " not found on the classpath");
            }
            properties.load(is);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
    }

    @Override
    public Set<String> getPropertyKeys() {
        Set<String> keys = new HashSet<>(properties.stringPropertyNames());
        keys.addAll(System.getProperties().stringPropertyNames());
        keys.removeIf(key -> !key.startsWith(PREFIX));
        return keys;
    }

    @Override
    public String getValue(String key) {
        return System.getProperty(key, properties.getProperty(key));
    }
}

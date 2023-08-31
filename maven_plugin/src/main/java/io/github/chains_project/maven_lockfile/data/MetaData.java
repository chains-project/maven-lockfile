package io.github.chains_project.maven_lockfile.data;

public class MetaData {

    private final Environment environment;
    private final Config config;

    public MetaData(Environment environment, Config config) {
        this.environment = environment;
        this.config = config;
    }

    /**
     * @return the config
     */
    public Config getConfig() {
        return config;
    }

    /**
     * @return the environment
     */
    public Environment getEnvironment() {
        return environment;
    }
}

package io.github.chains_project.maven_lockfile.data;

import java.util.Objects;

/**
 * Metadata about the environment in which the lock file was generated. This includes the OS name, the Maven version and the Java version.
 */
public class Environment {

    private final String osName;
    private final String mavenVersion;
    private final String javaVersion;

    public Environment(String osName, String mavenVersion, String javaVersion) {
        this.osName = osName;
        this.mavenVersion = mavenVersion;
        this.javaVersion = javaVersion;
    }

    /**
     * @return the java version of the environment in which the lock file was generated
     */
    public String getJavaVersion() {
        return javaVersion;
    }
    /**
     * @return the mavenVersion of the environment in which the lock file was generated
     */
    public String getMavenVersion() {
        return mavenVersion;
    }
    /**
     * @return the osName of the environment in which the lock file was generated
     */
    public String getOsName() {
        return osName;
    }

    @Override
    public String toString() {
        return "Metadata [osName=" + osName + ", mavenVersion=" + mavenVersion + ", javaVersion=" + javaVersion + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(osName, mavenVersion, javaVersion);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Environment)) {
            return false;
        }
        Environment other = (Environment) obj;
        return Objects.equals(osName, other.osName)
                && Objects.equals(mavenVersion, other.mavenVersion)
                && Objects.equals(javaVersion, other.javaVersion);
    }
}

package io.github.chains_project.maven_lockfile.checksum;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DependencyTypeUtilsTest {

    @Test
    void jarTypeReturnsJar() {
        assertThat(DependencyTypeUtils.getExtension("jar")).isEqualTo("jar");
    }

    @Test
    void pomTypeReturnsPom() {
        assertThat(DependencyTypeUtils.getExtension("pom")).isEqualTo("pom");
    }

    @Test
    void mavenPluginTypeReturnsJar() {
        assertThat(DependencyTypeUtils.getExtension("maven-plugin")).isEqualTo("jar");
    }

    @Test
    void earTypeReturnsEar() {
        assertThat(DependencyTypeUtils.getExtension("ear")).isEqualTo("ear");
    }

    @Test
    void ejbTypeReturnsJar() {
        assertThat(DependencyTypeUtils.getExtension("ejb")).isEqualTo("jar");
    }

    @Test
    void ejbClientTypeReturnsJar() {
        assertThat(DependencyTypeUtils.getExtension("ejb-client")).isEqualTo("jar");
    }

    @Test
    void javadocTypeReturnsJar() {
        assertThat(DependencyTypeUtils.getExtension("javadoc")).isEqualTo("jar");
    }

    @Test
    void javaSourceTypeReturnsJar() {
        assertThat(DependencyTypeUtils.getExtension("java-source")).isEqualTo("jar");
    }

    @Test
    void rarTypeReturnsRar() {
        assertThat(DependencyTypeUtils.getExtension("rar")).isEqualTo("rar");
    }

    @Test
    void testJarTypeReturnsJar() {
        assertThat(DependencyTypeUtils.getExtension("test-jar")).isEqualTo("jar");
    }

    @Test
    void warTypeReturnsWar() {
        assertThat(DependencyTypeUtils.getExtension("war")).isEqualTo("war");
    }

    @Test
    void unknownTypeReturnsSelf() {
        assertThat(DependencyTypeUtils.getExtension("bundle")).isEqualTo("bundle");
    }

    @Test
    void nullTypeReturnsJar() {
        assertThat(DependencyTypeUtils.getExtension(null)).isEqualTo("jar");
    }

    @Test
    void emptyTypeReturnsJar() {
        assertThat(DependencyTypeUtils.getExtension("")).isEqualTo("jar");
    }
}

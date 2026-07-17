package io.github.chains_project.maven_lockfile.resolvers;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SurefirePluginResolverTest {

    private SurefirePluginResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SurefirePluginResolver();
    }

    @Test
    void isNotApplicableWhenSurefirePluginAbsent() {
        MavenProject project = new MavenProject(new Model());
        assertThat(resolver.isApplicable(project)).isFalse();
    }

    @Test
    void isNotApplicableWhenSurefirePresentButNoTestFramework() {
        Model model = new Model();
        Build build = new Build();
        Plugin surefire = new Plugin();
        surefire.setGroupId("org.apache.maven.plugins");
        surefire.setArtifactId("maven-surefire-plugin");
        surefire.setVersion("3.2.5");
        build.addPlugin(surefire);
        model.setBuild(build);

        MavenProject project = new MavenProject(model);
        assertThat(resolver.isApplicable(project)).isFalse();
    }

    @Test
    void isApplicableWhenSurefireAndJUnit5Present() {
        MavenProject project = projectWithSurefireAndTestDep("org.junit.jupiter", "junit-jupiter", "5.10.2");
        assertThat(resolver.isApplicable(project)).isTrue();
    }

    @Test
    void isApplicableWhenSurefireAndTestNGPresent() {
        MavenProject project = projectWithSurefireAndTestDep("org.testng", "testng", "7.9.0");
        assertThat(resolver.isApplicable(project)).isTrue();
    }

    @Test
    void isApplicableWhenSurefireAndJUnit4Present() {
        MavenProject project = projectWithSurefireAndTestDep("junit", "junit", "4.13.2");
        assertThat(resolver.isApplicable(project)).isTrue();
    }

    @Test
    void isNotApplicableWhenOnlyCompileScopeDeps() {
        Model model = new Model();
        Build build = new Build();
        Plugin surefire = new Plugin();
        surefire.setGroupId("org.apache.maven.plugins");
        surefire.setArtifactId("maven-surefire-plugin");
        surefire.setVersion("3.2.5");
        build.addPlugin(surefire);
        model.setBuild(build);

        Dependency guava = new Dependency();
        guava.setGroupId("com.google.guava");
        guava.setArtifactId("guava");
        guava.setVersion("33.0.0-jre");
        guava.setScope("compile");
        model.addDependency(guava);

        MavenProject project = new MavenProject(model);
        assertThat(resolver.isApplicable(project)).isFalse();
    }

    @Test
    void displayNameContainsSurefire() {
        assertThat(resolver.getDisplayName()).contains("surefire");
    }

    private static MavenProject projectWithSurefireAndTestDep(String groupId, String artifactId, String version) {
        Model model = new Model();
        Build build = new Build();
        Plugin surefire = new Plugin();
        surefire.setGroupId("org.apache.maven.plugins");
        surefire.setArtifactId("maven-surefire-plugin");
        surefire.setVersion("3.2.5");
        build.addPlugin(surefire);
        model.setBuild(build);

        Dependency dep = new Dependency();
        dep.setGroupId(groupId);
        dep.setArtifactId(artifactId);
        dep.setVersion(version);
        dep.setScope("test");
        model.addDependency(dep);

        return new MavenProject(model);
    }
}

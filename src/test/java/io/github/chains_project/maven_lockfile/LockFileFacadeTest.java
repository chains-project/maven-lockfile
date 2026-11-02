package io.github.chains_project.maven_lockfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

class LockFileFacadeTest {

    private static MavenProject project(String groupId, String artifactId, String version) {
        MavenProject project = new MavenProject();
        project.setGroupId(groupId);
        project.setArtifactId(artifactId);
        project.setVersion(version);
        return project;
    }

    private static MavenSession sessionOf(List<MavenProject> allProjects, List<MavenProject> projects) {
        MavenSession session = mock(MavenSession.class);
        when(session.getAllProjects()).thenReturn(allProjects);
        when(session.getProjects()).thenReturn(projects);
        return session;
    }

    @Test
    void reactorGavsIncludesReleaseSiblingNotInGetProjects() {
        // --projects module-a (no -am): getProjects() has only module-a, getAllProjects() has both.
        MavenProject moduleA = project("com.mycompany.app", "module-a", "1.0.0");
        MavenProject moduleB = project("com.mycompany.app", "module-b", "1.0.0");
        MavenSession session = sessionOf(List.of(moduleA, moduleB), List.of(moduleA));

        assertThat(LockFileFacade.reactorGavs(session))
                .containsExactlyInAnyOrder("com.mycompany.app:module-a:1.0.0", "com.mycompany.app:module-b:1.0.0");
    }

    @Test
    void reactorGavsCoversReleaseAndSnapshotModules() {
        // dependency checksums are skipped for every reactor member, release or SNAPSHOT.
        MavenProject release = project("com.mycompany.app", "module-a", "1.0.0");
        MavenProject snapshot = project("com.mycompany.app", "module-b", "2.0.0-SNAPSHOT");
        MavenSession session = sessionOf(List.of(release, snapshot), List.of(release, snapshot));

        assertThat(LockFileFacade.reactorGavs(session))
                .containsExactlyInAnyOrder(
                        "com.mycompany.app:module-a:1.0.0", "com.mycompany.app:module-b:2.0.0-SNAPSHOT");
    }

    @Test
    void reactorGavsFallsBackToGetProjectsWhenGetAllProjectsIsNull() {
        MavenProject moduleA = project("com.mycompany.app", "module-a", "1.0.0");
        MavenProject moduleB = project("com.mycompany.app", "module-b", "1.0.0");
        MavenSession session = sessionOf(null, List.of(moduleA, moduleB));

        assertThat(LockFileFacade.reactorGavs(session))
                .containsExactlyInAnyOrder("com.mycompany.app:module-a:1.0.0", "com.mycompany.app:module-b:1.0.0");
    }

    @Test
    void reactorGavsIsEmptyForSingleModuleBuild() {
        // a single-project reactor is the root project itself, not a sibling.
        MavenProject only = project("com.mycompany.app", "solo", "1.0.0");
        MavenSession session = sessionOf(List.of(only), List.of(only));

        assertThat(LockFileFacade.reactorGavs(session)).isEmpty();
    }

    @Test
    void reactorSnapshotGavsKeepsSnapshotFilter() {
        // pom checksums are skipped only for SNAPSHOTs; the release module is excluded.
        MavenProject release = project("com.mycompany.app", "module-a", "1.0.0");
        MavenProject snapshot = project("com.mycompany.app", "module-b", "2.0.0-SNAPSHOT");
        MavenSession session = sessionOf(List.of(release, snapshot), List.of(release, snapshot));

        assertThat(LockFileFacade.reactorSnapshotGavs(session))
                .containsExactly("com.mycompany.app:module-b:2.0.0-SNAPSHOT");
    }

    @Test
    void reactorSnapshotGavsAlsoUsesGetAllProjects() {
        // the SNAPSHOT pom skip sees siblings outside getProjects() too.
        MavenProject moduleA = project("com.mycompany.app", "module-a", "1.0.0");
        MavenProject snapshotSibling = project("com.mycompany.app", "module-b", "1.0.0-SNAPSHOT");
        MavenSession session = sessionOf(List.of(moduleA, snapshotSibling), List.of(moduleA));

        assertThat(LockFileFacade.reactorSnapshotGavs(session))
                .containsExactly("com.mycompany.app:module-b:1.0.0-SNAPSHOT");
    }
}

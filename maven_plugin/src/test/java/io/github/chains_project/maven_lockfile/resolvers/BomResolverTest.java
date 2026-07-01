package io.github.chains_project.maven_lockfile.resolvers;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.RepositoryInformation;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.Pom;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.*;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BomResolverTest {

    private MavenSession session;
    private List<ArtifactRepository> repositories;
    private AbstractChecksumCalculator checksumCalculator;

    @BeforeEach
    void setUp() {
        session = mock(MavenSession.class);
        repositories = new ArrayList<>();
        checksumCalculator = mock(AbstractChecksumCalculator.class);

        // Mock checksum calculator to return empty/unresolved values
        when(checksumCalculator.getArtifactResolvedField(org.mockito.ArgumentMatchers.any(Artifact.class)))
                .thenReturn(RepositoryInformation.Unresolved());
        when(checksumCalculator.calculateArtifactChecksum(org.mockito.ArgumentMatchers.any(Artifact.class)))
                .thenReturn("");
        when(checksumCalculator.getChecksumAlgorithm()).thenReturn("SHA-256");
    }

    @Test
    void resolveForProject_interpolatesCustomProperty() {
        // Arrange: Create a Maven project with a custom property
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId("com.example");
        model.setArtifactId("test-project");
        model.setVersion("1.0.0");

        // Define custom property maven-lockfile-test-version = 1.1.1
        Properties properties = new Properties();
        properties.setProperty("maven-lockfile-test-version", "1.1.1");
        model.setProperties(properties);

        // Create dependencyManagement with a BOM using ${maven-lockfile-test-version}
        DependencyManagement depMgmt = new DependencyManagement();
        Dependency bomDep = new Dependency();
        bomDep.setGroupId("com.example.bom");
        bomDep.setArtifactId("test-bom");
        bomDep.setVersion("${maven-lockfile-test-version}");
        bomDep.setType("pom");
        bomDep.setScope("import");
        depMgmt.addDependency(bomDep);
        model.setDependencyManagement(depMgmt);

        // Build MavenProject with interpolated model
        MavenProject project = buildMavenProject(model);

        BomResolver resolver = new BomResolver(session, repositories, checksumCalculator);

        // Act & Assert: The resolver will try to resolve the BOM and fail
        // However, if the error message does NOT contain "${maven-lockfile-test-version}"
        // it proves interpolation worked (the property was resolved to "1.1.1")
        try {
            Set<Pom> poms = resolver.resolveForProject(project);
            // If we get here without exception, the test setup needs adjustment
            assert false;
        } catch (RuntimeException e) {
            // Verify the error message does NOT contain the unresolved property placeholder
            // If interpolation failed, the error would mention "${maven-lockfile-test-version}"
            // If interpolation worked, it would try to resolve "com.example.bom:test-bom:1.1.1"
            String errorMessage = e.getMessage();
            assertThat(errorMessage)
                    .as("Property interpolation should have resolved ${maven-lockfile-test-version} to 1.1.1")
                    .doesNotContain("${maven-lockfile-test-version}")
                    .doesNotContain("${");  // Should not contain any unresolved properties
        }
    }

    @Test
    void resolveForProject_interpolatesProjectVersion() {
        // Arrange: Create a Maven project using ${project.version}
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId("com.example");
        model.setArtifactId("test-project");
        model.setVersion("2.0.0");

        // Create dependencyManagement with a BOM using ${project.version}
        DependencyManagement depMgmt = new DependencyManagement();
        Dependency bomDep = new Dependency();
        bomDep.setGroupId("com.example.bom");
        bomDep.setArtifactId("test-bom");
        bomDep.setVersion("${project.version}");
        bomDep.setType("pom");
        bomDep.setScope("import");
        depMgmt.addDependency(bomDep);
        model.setDependencyManagement(depMgmt);

        // Build MavenProject with interpolated model
        MavenProject project = buildMavenProject(model);

        BomResolver resolver = new BomResolver(session, repositories, checksumCalculator);

        // Act & Assert: Property interpolation should resolve ${project.version} to 2.0.0
        try {
            Set<Pom> poms = resolver.resolveForProject(project);
            // If we get here without exception, the test setup needs adjustment
            assert false;
        } catch (RuntimeException e) {
            // Verify the error message does NOT contain the unresolved property placeholder
            String errorMessage = e.getMessage();
            assertThat(errorMessage)
                    .as("Property interpolation should have resolved ${project.version} to 2.0.0")
                    .doesNotContain("${project.version}")
                    .doesNotContain("${");  // Should not contain any unresolved properties
        }
    }

    @Test
    void resolveForProject_interpolatesProjectGroupId() {
        // Arrange: Create a Maven project using ${project.groupId}
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId("io.github.test");
        model.setArtifactId("test-project");
        model.setVersion("1.0.0");

        // Create dependencyManagement with a BOM using ${project.groupId}
        DependencyManagement depMgmt = new DependencyManagement();
        Dependency bomDep = new Dependency();
        bomDep.setGroupId("${project.groupId}");
        bomDep.setArtifactId("test-bom");
        bomDep.setVersion("1.0.0");
        bomDep.setType("pom");
        bomDep.setScope("import");
        depMgmt.addDependency(bomDep);
        model.setDependencyManagement(depMgmt);

        // Build MavenProject with interpolated model
        MavenProject project = buildMavenProject(model);

        BomResolver resolver = new BomResolver(session, repositories, checksumCalculator);

        // Act & Assert: The resolver will try to resolve the BOM and fail
        // However, if the error message does NOT contain "${project.groupId}"
        // it proves interpolation worked (the property was resolved to "io.github.test")
        try {
            Set<Pom> poms = resolver.resolveForProject(project);
            // If we get here without exception, the test setup needs adjustment
            assert false;
        } catch (RuntimeException e) {
            // Verify the error message does NOT contain the unresolved property placeholder
            String errorMessage = e.getMessage();
            assertThat(errorMessage)
                    .as("Property interpolation should have resolved ${project.groupId} to io.github.test")
                    .doesNotContain("${project.groupId}")
                    .doesNotContain("${");  // Should not contain any unresolved properties
        }
    }

    @Test
    void resolveForProject_returnsEmptySetWhenNoDependencyManagement() {
        // Arrange: Create a Maven project without dependencyManagement
        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("test-project");
        model.setVersion("1.0.0");

        MavenProject project = new MavenProject(model);
        project.setOriginalModel(model);
        BomResolver resolver = new BomResolver(session, repositories, checksumCalculator);

        // Act
        Set<Pom> result = resolver.resolveForProject(project);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void resolveForProject_interpolatesPropertiesInNestedTransitiveBom() {
        // Arrange: Create a parent project with properties
        Model parentModel = new Model();
        parentModel.setModelVersion("4.0.0");
        parentModel.setGroupId("com.example");
        parentModel.setArtifactId("parent-project");
        parentModel.setVersion("1.0.0");

        Properties parentProperties = new Properties();
        parentProperties.setProperty("bom.version", "2.0.0");
        parentProperties.setProperty("transitive.bom.version", "3.0.0");
        parentModel.setProperties(parentProperties);
        // Parent BOM imports another BOM using ${bom.version}
        DependencyManagement parentDepMgmt = new DependencyManagement();
        Dependency parentBomDep = new Dependency();
        parentBomDep.setGroupId("com.example");
        parentBomDep.setArtifactId("parent-bom");
        parentBomDep.setVersion("${bom.version}");
        parentBomDep.setType("pom");
        parentBomDep.setScope("import");
        parentDepMgmt.addDependency(parentBomDep);
        parentModel.setDependencyManagement(parentDepMgmt);

        // Create the nested BOM that imports a transitive BOM
        Model nestedBomModel = new Model();
        nestedBomModel.setModelVersion("4.0.0");
        nestedBomModel.setGroupId("com.example");
        nestedBomModel.setArtifactId("parent-bom");
        nestedBomModel.setVersion("2.0.0");

        Properties nestedBomProperties = new Properties();
        nestedBomProperties.setProperty("transitive.artifact", "transitive-bom");
        nestedBomModel.setProperties(nestedBomProperties);

        DependencyManagement nestedDepMgmt = new DependencyManagement();
        Dependency transitiveBomDep = new Dependency();
        transitiveBomDep.setGroupId("com.example");
        transitiveBomDep.setArtifactId("${transitive.artifact}");
        transitiveBomDep.setVersion("${project.version}");  // Uses project.version from nested BOM
        transitiveBomDep.setType("pom");
        transitiveBomDep.setScope("import");
        nestedDepMgmt.addDependency(transitiveBomDep);
        nestedBomModel.setDependencyManagement(nestedDepMgmt);

        // Create the transitive BOM (leaf)
        Model transitiveBomModel = new Model();
        transitiveBomModel.setModelVersion("4.0.0");
        transitiveBomModel.setGroupId("com.example");
        transitiveBomModel.setArtifactId("transitive-bom");
        transitiveBomModel.setVersion("2.0.0");

        // Build MavenProjects with interpolation
        MavenProject parentProject = buildMavenProject(parentModel);
        MavenProject nestedBomProject = buildMavenProject(nestedBomModel);
        MavenProject transitiveBomProject = buildMavenProject(transitiveBomModel);

        // Ensure artifacts are not null (MavenProject constructor should set them, but let's be explicit)
        if (nestedBomProject.getArtifact() == null) {
            nestedBomProject.setArtifact(new org.apache.maven.artifact.DefaultArtifact(
                    "com.example", "parent-bom", "2.0.0", "compile", "pom", "",
                    new org.apache.maven.artifact.handler.DefaultArtifactHandler("pom")));
        }
        if (transitiveBomProject.getArtifact() == null) {
            transitiveBomProject.setArtifact(new org.apache.maven.artifact.DefaultArtifact(
                    "com.example", "transitive-bom", "2.0.0", "compile", "pom", "",
                    new org.apache.maven.artifact.handler.DefaultArtifactHandler("pom")));
        }

        // Mock ProjectBuilder to return our test BOMs when buildFromGav is called
        try (var mockedProjectBuilder = mockConstruction(ProjectBuilder.class,
                (mock, context) -> {
                    // When buildFromGav is called with interpolated coordinates, return the appropriate BOM
                    // The key here is that if properties are interpolated correctly:
                    // - "${bom.version}" should become "2.0.0"
                    // - "${transitive.artifact}" should become "transitive-bom"
                    // - "${project.version}" should become "2.0.0" (from nested BOM)

                    when(mock.buildFromGav(anyString(), anyString(), anyString()))
                            .thenAnswer(invocation -> {
                                String groupId = invocation.getArgument(0);
                                String artifactId = invocation.getArgument(1);
                                String version = invocation.getArgument(2);

                                // Parent BOM: com.example:parent-bom:2.0.0 (from ${bom.version})
                                if ("com.example".equals(groupId) &&
                                        "parent-bom".equals(artifactId) &&
                                        "2.0.0".equals(version)) {
                                    return Optional.of(nestedBomProject);
                                }

                                // Transitive BOM: com.example:transitive-bom:2.0.0
                                // (artifactId from ${transitive.artifact}, version from ${project.version})
                                if ("com.example".equals(groupId) &&
                                        "transitive-bom".equals(artifactId) &&
                                        "2.0.0".equals(version)) {
                                    return Optional.of(transitiveBomProject);
                                }

                                // If we get unresolved properties, the test should fail
                                if (groupId.contains("${") || artifactId.contains("${") || version.contains("${")) {
                                    throw new AssertionError("Property not interpolated: " +
                                            groupId + ":" + artifactId + ":" + version);
                                }

                                return Optional.empty();
                            });
                })) {

            BomResolver resolver = new BomResolver(session, repositories, checksumCalculator);

            // Act
            Set<Pom> result = resolver.resolveForProject(parentProject);

            // Assert
            assertThat(result).isNotEmpty();
            assertThat(result).hasSize(1);  // Parent BOM was resolved

            // Verify the parent BOM was resolved with interpolated version "2.0.0"
            Pom parentBom = result.iterator().next();
            assertThat(parentBom.getGroupId()).isEqualTo(GroupId.of("com.example"));
            assertThat(parentBom.getArtifactId()).isEqualTo(ArtifactId.of("parent-bom"));
            assertThat(parentBom.getVersion()).isEqualTo(VersionNumber.of("2.0.0"));

            // Verify the nested BOM contains the transitive BOM
            assertThat(parentBom.getBoms()).isNotEmpty();
            assertThat(parentBom.getBoms()).hasSize(1);

            Pom transitiveBom = parentBom.getBoms().iterator().next();
            assertThat(transitiveBom.getArtifactId()).isEqualTo(ArtifactId.of("transitive-bom"));
            assertThat(transitiveBom.getVersion()).isEqualTo(VersionNumber.of("2.0.0"));
        }
    }

    @Test
    void resolveForProject_returnsEmptySetWhenNoBomDependencies() {
        // Arrange: Create a Maven project with dependencyManagement but no BOM imports
        Model model = getModelArtifactPlaceholder();

        MavenProject project = new MavenProject(model);
        project.setOriginalModel(model);
        BomResolver resolver = new BomResolver(session, repositories, checksumCalculator);

        // Act
        Set<Pom> result = resolver.resolveForProject(project);

        // Assert
        assertThat(result).isEmpty();
    }

    private static @NonNull Model getModelArtifactPlaceholder() {
        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("test-project");
        model.setVersion("1.0.0");

        DependencyManagement depMgmt = new DependencyManagement();
        Dependency regularDep = new Dependency();
        regularDep.setGroupId("junit");
        regularDep.setArtifactId("${junit.artifactId}");
        regularDep.setVersion("4.13.2");
        regularDep.setType("jar");  // Not a BOM (type != pom)
        regularDep.setScope("test");
        depMgmt.addDependency(regularDep);
        model.setDependencyManagement(depMgmt);
        return model;
    }

    /**
     * Build a MavenProject from a Model with property interpolation.
     * Uses ModelBuilder to get the effective (interpolated) model.
     */
    private @NonNull MavenProject buildMavenProject(Model myModel) {
        try {
            // Use ModelBuilder to interpolate properties in the model
            DefaultModelBuilder modelBuilder = new DefaultModelBuilderFactory().newInstance();
            DefaultModelBuildingRequest request = new DefaultModelBuildingRequest();

            // Set the raw model
            request.setRawModel(myModel);

            // Configure the request
            request.setSystemProperties(System.getProperties());
            request.setUserProperties(new Properties());
            request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
            request.setProcessPlugins(false);
            request.setTwoPhaseBuilding(false);

            // Add a no-op ModelResolver to prevent trying to resolve import BOMs
            request.setModelResolver(new NoOpModelResolver());

            // Build the model - this interpolates properties
            ModelBuildingResult result = modelBuilder.build(request);
            Model effectiveModel = result.getEffectiveModel();

            // Create MavenProject with the interpolated model
            MavenProject project = new MavenProject(effectiveModel);
            project.setOriginalModel(myModel);

            return project;

        } catch (ModelBuildingException e) {
            throw new RuntimeException("Failed to build MavenProject for testing", e);
        }
    }

    /**
     * No-op ModelResolver that returns minimal dummy POMs.
     * This allows property interpolation to work without accessing repositories.
     */
    private static class NoOpModelResolver implements ModelResolver {
        @Override
        public ModelSource resolveModel(String groupId, String artifactId, String version) {
            // Return a minimal POM to prevent null errors
            return createDummyModelSource(groupId, artifactId, version);
        }

        @Override
        public ModelSource resolveModel(org.apache.maven.model.Parent parent) {
            return createDummyModelSource(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
        }

        @Override
        public ModelSource resolveModel(org.apache.maven.model.Dependency dependency) {
            return createDummyModelSource(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
        }

        private ModelSource createDummyModelSource(String groupId, String artifactId, String version) {
            // Create a minimal POM XML
            String pomXml = String.format(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
                            "  <modelVersion>4.0.0</modelVersion>\n" +
                            "  <groupId>%s</groupId>\n" +
                            "  <artifactId>%s</artifactId>\n" +
                            "  <version>%s</version>\n" +
                            "</project>",
                    groupId, artifactId, version
            );
            return new StringModelSource(pomXml, groupId + ":" + artifactId + ":" + version);
        }

        @Override
        public void addRepository(org.apache.maven.model.Repository repository) {
            // No-op
        }

        @Override
        public void addRepository(org.apache.maven.model.Repository repository, boolean replace) {
            // No-op
        }

        @Override
        public ModelResolver newCopy() {
            return this;
        }
    }

}

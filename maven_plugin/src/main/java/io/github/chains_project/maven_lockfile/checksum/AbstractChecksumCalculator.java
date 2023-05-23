package io.github.chains_project.maven_lockfile.checksum;

import org.apache.maven.artifact.Artifact;

public abstract class AbstractChecksumCalculator {

    public abstract String calculateChecksum(Artifact artifact);
}

package io.github.chains_project.maven_lockfile.data;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import org.apache.maven.project.MavenProject;

public class Pom implements Comparable<Pom> {

    private final String path;
    private final String checksumAlgorithm;
    private final String checksum;

    public Pom(MavenProject project, AbstractChecksumCalculator checksumCalculator) {
        this.path = project.getBasedir()
                .toPath()
                .relativize(project.getFile().toPath())
                .toString();
        this.checksumAlgorithm = checksumCalculator.getChecksumAlgorithm();
        this.checksum =
                checksumCalculator.calculatePomChecksum(project.getFile().toPath());
    }

    public Pom(String path, String checksumAlgorithm, String checksum) {
        this.path = path;
        this.checksumAlgorithm = checksumAlgorithm;
        this.checksum = checksum;
    }

    public String getPath() {
        return path;
    }

    public String getChecksumAlgorithm() {
        return checksumAlgorithm;
    }

    public String getChecksum() {
        return checksum;
    }

    @Override
    public int compareTo(Pom o) {
        if (this.path.compareTo(o.path) != 0) {
            return this.path.compareTo(o.path);
        }

        if (this.checksumAlgorithm.compareTo(o.checksumAlgorithm) != 0) {
            return this.checksumAlgorithm.compareTo(o.checksumAlgorithm);
        }

        if (this.checksum.compareTo(o.checksum) != 0) {
            return this.checksum.compareTo(o.checksum);
        }

        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Pom)) {
            return false;
        }
        Pom other = (Pom) obj;
        return this.path.equals(other.path)
                && this.checksumAlgorithm.equals(other.checksumAlgorithm)
                && this.checksum.equals(other.checksum);
    }
}

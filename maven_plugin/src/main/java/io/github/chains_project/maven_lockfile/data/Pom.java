package io.github.chains_project.maven_lockfile.data;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import java.util.ArrayList;
import org.apache.maven.project.MavenProject;

public class Pom implements Comparable<Pom> {

    private final String path;
    private final String checksumAlgorithm;
    private final String checksum;
    private final Pom parent;

    public Pom(String path, String checksumAlgorithm, String checksum, Pom parent) {
        this.path = path;
        this.checksumAlgorithm = checksumAlgorithm;
        this.checksum = checksum;
        this.parent = parent;
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

    public static Pom ConstructRecursivePom(MavenProject initialProject, AbstractChecksumCalculator checksumCalculator) {
        String checksumAlgorithm = checksumCalculator.getChecksumAlgorithm();

        ArrayList<MavenProject> recursiveProjects = new ArrayList<MavenProject>();
        recursiveProjects.add(initialProject);
        while (recursiveProjects.get(recursiveProjects.size() - 1).hasParent()) {
            recursiveProjects.add(
                    recursiveProjects.get(recursiveProjects.size() - 1).getParent());
        }

        Pom lastPom = null;
        for (MavenProject project : recursiveProjects.reversed()) {
            String path = initialProject
                    .getBasedir()
                    .toPath()
                    .relativize(project.getFile().toPath())
                    .toString();
            String checksum =
                    checksumCalculator.calculatePomChecksum(project.getFile().toPath());
            lastPom = new Pom(path, checksumAlgorithm, checksum, lastPom);
        }

        return lastPom;
    }
}

package io.github.chains_project.maven_lockfile.data;

/** Whether the lockfile includes a feature, such as BOMs, and what a validation mismatch in it does. */
public final class FeatureConfig {

    private final boolean include;
    private final ValidationPolicy onFailure;

    public FeatureConfig(boolean include, ValidationPolicy onFailure) {
        this.include = include;
        this.onFailure = onFailure;
    }

    public boolean isIncluded() {
        return include;
    }

    public ValidationPolicy getOnFailure() {
        return onFailure;
    }
}

package io.github.chains_project.maven_lockfile.resolvers;

import org.apache.maven.model.building.ModelBuildingException;

public class BomResolutionException extends RuntimeException {
    public BomResolutionException(String message) {
        super(message);
    }

    public BomResolutionException(String message, ModelBuildingException e) {
        super(message, e);
    }
}

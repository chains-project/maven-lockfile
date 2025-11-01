package org.apache.maven.shared.dependency.graph.internal;

import java.lang.reflect.Field;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.shared.dependency.graph.DependencyNode;

public class SpyingDependencyNodeUtils {

    private static final Logger LOGGER = LogManager.getLogger(SpyingDependencyNodeUtils.class);
    /**
     * Resolves the conflict data from a dependency node. This is a hack, because the conflict data is not exposed by the API.
     * The winner version is used to determine the version of a dependency.
     * @param node  The node to get the conflict data from.
     * @return  the included version as a string or null if the version could not be determined.
     */
    public static Optional<String> getWinnerVersion(DependencyNode node) {
        if (node instanceof VerboseDependencyNode) {
            VerboseDependencyNode newNode = (VerboseDependencyNode) node;
            try {
                Field dataField = VerboseDependencyNode.class.getDeclaredField("data");
                dataField.setAccessible(true);
                ConflictData data = (ConflictData) dataField.get(newNode);
                data.getWinnerVersion();
                return Optional.ofNullable(data.getWinnerVersion());
            } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                LOGGER.warn("Could not get winner dependency version.", e);
            }
        }
        return Optional.empty();
    }
}

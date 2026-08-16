package io.github.chains_project.maven_lockfile.recorder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/** Reads/writes the JSON file that hands artifacts off from {@link DynamicResolutionSpy} (a core extension) to {@code LockFileFacade} (a Mojo, different realm). */
public final class DynamicResolutionStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<RecordedArtifact>>() {}.getType();

    private DynamicResolutionStore() {}

    public static Path defaultPath(Path multiModuleProjectDirectory) {
        return multiModuleProjectDirectory
                .resolve("target")
                .resolve("maven-lockfile")
                .resolve("dynamic-resolutions.json");
    }

    public static void write(Path path, Collection<RecordedArtifact> artifacts) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(new TreeSet<>(artifacts), LIST_TYPE));
    }

    public static List<RecordedArtifact> read(Path path) throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        List<RecordedArtifact> result = GSON.fromJson(Files.readString(path), LIST_TYPE);
        return result == null ? List.of() : result;
    }
}

package io.github.chains_project.maven_lockfile.recorder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        // Write to a sibling temp file and move it into place, so a concurrent reader never sees a
        // truncated or partially-written file.
        Path tmp = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tmp, GSON.toJson(new TreeSet<>(artifacts), LIST_TYPE));
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static List<RecordedArtifact> read(Path path) throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        List<RecordedArtifact> result = GSON.fromJson(Files.readString(path), LIST_TYPE);
        return result == null ? List.of() : result;
    }
}

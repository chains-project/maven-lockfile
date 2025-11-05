package io.github.chains_project.maven_lockfile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.Classifier;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.MavenScope;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import io.github.chains_project.maven_lockfile.graph.NodeId;

public class JsonUtils {
    private JsonUtils() {}

    private static Gson getGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(VersionNumber.class, (JsonSerializer<VersionNumber>)
                        (it, type, ignore) -> new JsonPrimitive(it.getValue()))
                .registerTypeAdapter(VersionNumber.class, (JsonDeserializer<VersionNumber>)
                        (it, type, ignore) -> VersionNumber.of(it.getAsString()))
                .registerTypeAdapter(ArtifactId.class, (JsonSerializer<ArtifactId>)
                        (it, type, ignore) -> new JsonPrimitive(it.getValue()))
                .registerTypeAdapter(ArtifactId.class, (JsonDeserializer<ArtifactId>)
                        (it, type, ignore) -> ArtifactId.of(it.getAsString()))
                .registerTypeAdapter(Classifier.class, (JsonSerializer<Classifier>)
                        (it, type, ignore) -> new JsonPrimitive(it.getValue()))
                .registerTypeAdapter(Classifier.class, (JsonDeserializer<Classifier>)
                        (it, type, ignore) -> Classifier.of(it.getAsString()))
                .registerTypeAdapter(
                        GroupId.class, (JsonSerializer<GroupId>) (it, type, ignore) -> new JsonPrimitive(it.getValue()))
                .registerTypeAdapter(
                        GroupId.class, (JsonDeserializer<GroupId>) (it, type, ignore) -> GroupId.of(it.getAsString()))
                .registerTypeAdapter(
                        NodeId.class, (JsonSerializer<NodeId>) (it, type, ignore) -> new JsonPrimitive(it.toString()))
                .registerTypeAdapter(NodeId.class, (JsonDeserializer<NodeId>)
                        (it, type, ignore) -> NodeId.fromValue(it.getAsString()))
                .registerTypeAdapter(MavenScope.class, (JsonSerializer<MavenScope>)
                        (it, type, ignore) -> new JsonPrimitive(it.getValue()))
                .registerTypeAdapter(MavenScope.class, (JsonDeserializer<MavenScope>)
                        (it, type, ignore) -> MavenScope.fromString(it.getAsString()))
                .registerTypeAdapter(ResolvedUrl.class, (JsonSerializer<ResolvedUrl>)
                        (it, type, ignore) -> new JsonPrimitive(it.getValue()))
                .registerTypeAdapter(ResolvedUrl.class, (JsonDeserializer<ResolvedUrl>)
                        (it, type, ignore) -> ResolvedUrl.of(it.getAsString()))
                .registerTypeAdapter(RepositoryId.class, (JsonSerializer<RepositoryId>)
                        (it, type, ignore) -> new JsonPrimitive(it.getValue()))
                .registerTypeAdapter(RepositoryId.class, (JsonDeserializer<RepositoryId>)
                        (it, type, ignore) -> RepositoryId.of(it.getAsString()))
                .setLenient()
                // .registerTypeAdapter(LockFileDependency.class, new LockFileDependencyAdapter())
                .create();
    }

    public static String toJson(Object it) {
        return getGson().toJson(it);
    }

    public static <T> T fromJson(String it, Class<T> type) {
        return getGson().fromJson(it, type);
    }
}

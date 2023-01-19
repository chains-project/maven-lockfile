package io.github.chains_project.maven_lockfile.typeadapters;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.LockFileDependency;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import java.io.IOException;

public class LockFileDependencyAdapter extends TypeAdapter<LockFileDependency> {

    @Override
    public void write(JsonWriter out, LockFileDependency value) throws IOException {
        out.beginObject()
                .name(value.getArtifactId().getValue())
                .beginObject()
                .name("version")
                .value(value.getVersion().getValue())
                .name("groupId")
                .value(value.getGroupId().getValue())
                .name("integrity")
                .value(value.getChecksum())
                .name("checksumAlgorithm")
                .value(value.getChecksumAlgorithm())
                .name("repoUrl")
                .value(value.getRepoUrl())
                .endObject()
                .endObject();
    }

    @Override
    public LockFileDependency read(JsonReader in) throws IOException {
        in.beginObject();
        String artifactId = in.nextName();
        in.beginObject();
        in.skipValue();
        String version = in.nextString();
        in.skipValue();
        String groupId = in.nextString();
        in.skipValue();
        String integrity = in.nextString();
        in.skipValue();
        String checksumAlgorithm = in.nextString();
        in.skipValue();
        String repoUrl = in.nextString();
        in.endObject();
        in.endObject();
        return new LockFileDependency(
                ArtifactId.of(artifactId),
                GroupId.of(groupId),
                VersionNumber.of(version),
                checksumAlgorithm,
                integrity,
                repoUrl);
    }
}

package io.github.chains_project.maven_lockfile.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Lockfile format version 2: the content of version 1, with each artifact's coordinates, checksum and repository
 * stored once in a top-level {@code artifacts} table keyed by package URL. Every occurrence in the dependency,
 * plugin, extension and POM trees refers to its entry through an {@code artifact} key and keeps only what belongs
 * to that occurrence (scope, selected version, children, parent POM, ...).
 *
 * <p>The conversion works on the version 1 JSON tree, so both formats share one in-memory model and one set of
 * validation rules. It is lossless: {@code decode(encode(v1))} equals {@code v1} apart from {@code lockFileVersion}.
 * <ul>
 *   <li>An occurrence whose checksum or repository differs from its table entry keeps the differing fields inline,
 *       with an explicit {@code null} for a field it lacks.</li>
 *   <li>A dependency node's {@code id} and {@code parent} are omitted, because they follow from its coordinates and
 *       its position in the tree. A node whose value differs from the derived one keeps it inline.</li>
 * </ul>
 */
final class LockFileV2 {

    static final int VERSION = 2;
    static final String ARTIFACTS = "artifacts";
    static final String ARTIFACT = "artifact";

    private static final List<String> GAV = List.of("groupId", "artifactId", "version");
    private static final List<String> COORDINATES = List.of("groupId", "artifactId", "version", "classifier", "type");
    private static final List<String> METADATA = List.of("checksumAlgorithm", "checksum", "resolved", "repositoryId");

    /** The version 1 object types that describe an artifact. */
    private enum Kind {
        /** A {@code DependencyNode}: may have a classifier and type. */
        DEPENDENCY_NODE,
        /** A {@code Pom}: has no type field, its table entry records {@code "type": "pom"}. */
        POM,
        /** A {@code MavenPlugin} or {@code MavenExtension}: always the main JAR. */
        MAVEN_COMPONENT
    }

    private LockFileV2() {}

    /** Converts a version 1 lockfile tree to version 2. The input is not modified. */
    static JsonObject encode(JsonObject v1) {
        Map<String, JsonObject> artifacts = new TreeMap<>();
        JsonObject v2 = new JsonObject();
        for (Map.Entry<String, JsonElement> member : v1.entrySet()) {
            String key = member.getKey();
            Kind kind = rootKind(key);
            if (key.equals("lockFileVersion")) {
                v2.addProperty(key, VERSION);
            } else if (kind == null) {
                v2.add(key, member.getValue().deepCopy());
            } else {
                v2.add(key, encodeChild(member.getValue(), kind, null, artifacts));
            }
        }
        JsonObject table = new JsonObject();
        artifacts.forEach(table::add);
        v2.add(ARTIFACTS, table);
        return v2;
    }

    /**
     * Converts a version 2 lockfile tree to the version 1 shape. {@code lockFileVersion} is left as is, so the model
     * read from the result still records the format of the file. The input is not modified.
     *
     * @throws LockFileFormatException if an {@code artifact} reference has no entry in the {@code artifacts} table
     */
    static JsonObject decode(JsonObject v2) throws LockFileFormatException {
        JsonElement table = v2.get(ARTIFACTS);
        JsonObject artifacts = table != null && table.isJsonObject() ? table.getAsJsonObject() : new JsonObject();
        JsonObject v1 = new JsonObject();
        for (Map.Entry<String, JsonElement> member : v2.entrySet()) {
            String key = member.getKey();
            Kind kind = rootKind(key);
            if (key.equals(ARTIFACTS)) {
                continue;
            } else if (kind == null) {
                v1.add(key, member.getValue().deepCopy());
            } else {
                v1.add(key, decodeChild(member.getValue(), kind, null, artifacts));
            }
        }
        return v1;
    }

    private static Kind rootKind(String key) {
        switch (key) {
            case "pom":
            case "boms":
                return Kind.POM;
            case "dependencies":
                return Kind.DEPENDENCY_NODE;
            case "mavenPlugins":
            case "mavenExtensions":
                return Kind.MAVEN_COMPONENT;
            default:
                return null;
        }
    }

    /** The kind of the object(s) nested under {@code key} of a {@code kind} object, or {@code null} for other data. */
    private static Kind childKind(Kind kind, String key) {
        switch (kind) {
            case DEPENDENCY_NODE:
                if (key.equals("children")) return Kind.DEPENDENCY_NODE;
                return key.equals("parentPom") || key.equals("boms") ? Kind.POM : null;
            case POM:
                return key.equals("parent") || key.equals("boms") ? Kind.POM : null;
            case MAVEN_COMPONENT:
                if (key.equals("dependencies")) return Kind.DEPENDENCY_NODE;
                return key.equals("parentPom") ? Kind.POM : null;
            default:
                throw new IllegalArgumentException(kind.name());
        }
    }

    // ---------------------------------------------------------------- encoding

    /** Encodes an object or an array of objects of the given kind; anything else is copied. */
    private static JsonElement encodeChild(
            JsonElement value, Kind kind, JsonElement enclosingId, Map<String, JsonObject> artifacts) {
        if (value.isJsonObject()) {
            return encodeObject(value.getAsJsonObject(), kind, enclosingId, artifacts);
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement element : value.getAsJsonArray()) {
                result.add(encodeChild(element, kind, enclosingId, artifacts));
            }
            return result;
        }
        return value.deepCopy();
    }

    private static JsonObject encodeObject(
            JsonObject v1, Kind kind, JsonElement enclosingId, Map<String, JsonObject> artifacts) {
        JsonObject key = keyCoordinates(v1, kind);
        String purl = purlOf(key);
        JsonObject entry = null;
        if (purl != null) {
            entry = artifacts.computeIfAbsent(purl, ignored -> newEntry(key, v1));
            if (!select(entry, COORDINATES).equals(key)) {
                entry = null; // two coordinates with one package URL: keep this object as it is in version 1
            }
        }

        JsonObject v2 = new JsonObject();
        if (entry != null) {
            v2.addProperty(ARTIFACT, purl);
            for (String field : METADATA) {
                JsonElement own = v1.get(field);
                if (!Objects.equals(own, entry.get(field))) {
                    v2.add(field, own == null ? JsonNull.INSTANCE : own.deepCopy());
                }
            }
        }
        for (Map.Entry<String, JsonElement> member : v1.entrySet()) {
            String field = member.getKey();
            Kind childKind = childKind(kind, field);
            if (entry != null && (COORDINATES.contains(field) || METADATA.contains(field))) {
                continue;
            } else if (kind == Kind.DEPENDENCY_NODE && (field.equals("id") || field.equals("parent"))) {
                continue;
            } else if (childKind == null) {
                v2.add(field, member.getValue().deepCopy());
            } else {
                v2.add(field, encodeChild(member.getValue(), childKind, v1.get("id"), artifacts));
            }
        }
        if (kind == Kind.DEPENDENCY_NODE) {
            keepIfNotDerivable(v1.get("id"), derivedId(v1), "id", v2);
            keepIfNotDerivable(v1.get("parent"), enclosingId, "parent", v2);
        }
        return v2;
    }

    private static JsonObject newEntry(JsonObject keyCoordinates, JsonObject v1) {
        JsonObject entry = keyCoordinates.deepCopy();
        for (String field : METADATA) {
            if (v1.has(field)) {
                entry.add(field, v1.get(field).deepCopy());
            }
        }
        return entry;
    }

    /** Keeps {@code field} in the version 2 node, as an explicit {@code null} if absent, unless it is derivable. */
    private static void keepIfNotDerivable(JsonElement own, JsonElement derived, String field, JsonObject v2) {
        if (!Objects.equals(own, derived)) {
            v2.add(field, own == null ? JsonNull.INSTANCE : own.deepCopy());
        }
    }

    // ---------------------------------------------------------------- decoding

    /** Decodes an object or an array of objects of the given kind; anything else is copied. */
    private static JsonElement decodeChild(JsonElement value, Kind kind, JsonElement enclosingId, JsonObject artifacts)
            throws LockFileFormatException {
        if (value.isJsonObject()) {
            return decodeObject(value.getAsJsonObject(), kind, enclosingId, artifacts);
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement element : value.getAsJsonArray()) {
                result.add(decodeChild(element, kind, enclosingId, artifacts));
            }
            return result;
        }
        return value.deepCopy();
    }

    private static JsonObject decodeObject(JsonObject v2, Kind kind, JsonElement enclosingId, JsonObject artifacts)
            throws LockFileFormatException {
        JsonObject entry = v2.has(ARTIFACT) ? lookUp(v2.get(ARTIFACT), artifacts) : null;
        JsonObject v1 = new JsonObject();
        if (entry != null) {
            select(entry, kind == Kind.DEPENDENCY_NODE ? COORDINATES : GAV)
                    .entrySet()
                    .forEach(e -> v1.add(e.getKey(), e.getValue()));
            for (String field : METADATA) {
                JsonElement value = v2.has(field) ? v2.get(field) : entry.get(field);
                if (value != null && !value.isJsonNull()) {
                    v1.add(field, value.deepCopy());
                }
            }
        }
        JsonElement id = null;
        if (kind == Kind.DEPENDENCY_NODE) {
            id = restore(v2, "id", derivedId(entry != null ? entry : v2));
            JsonElement parent = restore(v2, "parent", enclosingId);
            if (id != null) v1.add("id", id);
            if (parent != null) v1.add("parent", parent);
        }
        for (Map.Entry<String, JsonElement> member : v2.entrySet()) {
            String field = member.getKey();
            Kind childKind = childKind(kind, field);
            if (entry != null && (field.equals(ARTIFACT) || METADATA.contains(field))) {
                continue;
            } else if (kind == Kind.DEPENDENCY_NODE && (field.equals("id") || field.equals("parent"))) {
                continue;
            } else if (childKind == null) {
                v1.add(field, member.getValue().deepCopy());
            } else {
                v1.add(field, decodeChild(member.getValue(), childKind, id, artifacts));
            }
        }
        return v1;
    }

    private static JsonObject lookUp(JsonElement reference, JsonObject artifacts) throws LockFileFormatException {
        JsonElement entry = reference.isJsonPrimitive() ? artifacts.get(reference.getAsString()) : null;
        if (entry == null || !entry.isJsonObject()) {
            throw new LockFileFormatException(
                    "Invalid lockfile: " + ARTIFACT + " " + reference + " has no entry in \"" + ARTIFACTS + "\"");
        }
        return entry.getAsJsonObject();
    }

    /** The value of {@code field} from the version 2 node if present there (possibly {@code null}), else derived. */
    private static JsonElement restore(JsonObject v2, String field, JsonElement derived) {
        JsonElement value = v2.has(field) ? v2.get(field) : derived;
        return value == null || value.isJsonNull() ? null : value.deepCopy();
    }

    // ---------------------------------------------------------------- coordinates

    /** The coordinates that make up the package URL of the artifact a version 1 object describes. */
    private static JsonObject keyCoordinates(JsonObject v1, Kind kind) {
        JsonObject coordinates = select(v1, kind == Kind.DEPENDENCY_NODE ? COORDINATES : GAV);
        if (kind == Kind.POM) {
            coordinates.addProperty("type", "pom");
        }
        return coordinates;
    }

    private static JsonObject select(JsonObject object, List<String> fields) {
        JsonObject selected = new JsonObject();
        for (String field : fields) {
            if (object.has(field)) {
                selected.add(field, object.get(field).deepCopy());
            }
        }
        return selected;
    }

    /**
     * The package URL of the coordinates, e.g. {@code pkg:maven/org.example/lib@1.0?classifier=sources&type=jar}, or
     * {@code null} if the group, artifact or version is missing.
     */
    static String purlOf(JsonObject coordinates) {
        String groupId = stringOrNull(coordinates, "groupId");
        String artifactId = stringOrNull(coordinates, "artifactId");
        String version = stringOrNull(coordinates, "version");
        if (groupId == null || artifactId == null || version == null) {
            return null;
        }
        StringBuilder purl = new StringBuilder("pkg:maven/")
                .append(percentEncode(groupId))
                .append('/')
                .append(percentEncode(artifactId))
                .append('@')
                .append(percentEncode(version));
        char separator = '?';
        for (String qualifier : List.of("classifier", "type")) {
            String value = stringOrNull(coordinates, qualifier);
            if (value != null) {
                purl.append(separator).append(qualifier).append('=').append(percentEncode(value));
                separator = '&';
            }
        }
        return purl.toString();
    }

    private static JsonElement derivedId(JsonObject coordinates) {
        String groupId = stringOrNull(coordinates, "groupId");
        String artifactId = stringOrNull(coordinates, "artifactId");
        String version = stringOrNull(coordinates, "version");
        if (groupId == null || artifactId == null || version == null) {
            return null;
        }
        return new JsonPrimitive(groupId + ":" + artifactId + ":" + version);
    }

    private static String stringOrNull(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static String percentEncode(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || ".-_~".indexOf(c) >= 0) {
                encoded.append((char) c);
            } else {
                encoded.append('%').append(String.format("%02X", c));
            }
        }
        return encoded.toString();
    }
}

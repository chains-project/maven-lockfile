package io.github.chains_project.maven_lockfile.data;

import com.google.common.base.Strings;
import java.util.Objects;

/**
 * A Maven artifact classifier is an optional and arbitrary string that gets appended to the generated artifact's name just after its version
 * see https://www.baeldung.com/maven-artifact-classifiers
 */
public class Classifier implements Comparable<Classifier> {
    public static Classifier of(String classifier) {
        if (Strings.isNullOrEmpty(classifier)) {
            return null;
        }
        return new Classifier(classifier);
    }

    private final String value;

    private Classifier(String classifier) {
        this.value = Objects.requireNonNull(classifier, "classifier is marked non-null but is null");
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "{" + " Classifier='" + getValue() + "'" + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Classifier)) {
            return false;
        }
        Classifier classifier = (Classifier) o;
        return Objects.equals(value, classifier.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public int compareTo(Classifier o) {
        return this.value.compareTo(o.value);
    }
}

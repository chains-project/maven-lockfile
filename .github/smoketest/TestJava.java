///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.buildobjects:jproc:2.8.2 com.fasterxml.jackson.core:jackson-databind:2.14.2 org.eclipse.jgit:org.eclipse.jgit:6.5.0.202303070854-r org.junit.jupiter:junit-jupiter-api:5.7.2 org.junit.jupiter:junit-jupiter-engine:5.7.2
//JAVA 17+
import static java.lang.System.*;
import org.buildobjects.process.ProcBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
public class TestJava {
  
    private static final String COMMAND_GENERATE = "io.github.chains-project:maven-lockfile:1.0.12:generate";
    public static void main(String[] args) {

        try {
          System.out.println(System.getProperty("MVN_PATH_BIN"));
            var result = new ProcBuilder("mvn")
                    .withOutputStream(System.out)
                    .withErrorStream(System.err)
                    .withNoTimeout()
                    .withArg(COMMAND_GENERATE)
                    .run();
            if (result.getExitValue() != 0) {
                throw new RuntimeException("Failed to generate lockfile");
            }
        } catch (Exception e) {
          e.printStackTrace();
        }

    }
}

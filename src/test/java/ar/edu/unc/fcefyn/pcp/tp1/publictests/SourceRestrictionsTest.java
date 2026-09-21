package ar.edu.unc.fcefyn.pcp.tp1.publictests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceRestrictionsTest {

    private static final Set<String> ALLOWED_CONCURRENT_TYPES = Set.of(
            "java.util.concurrent.Semaphore",
            "java.util.concurrent.Executor",
            "java.util.concurrent.ExecutorService",
            "java.util.concurrent.Executors",
            "java.util.concurrent.ThreadPoolExecutor",
            "java.util.concurrent.locks.Lock",
            "java.util.concurrent.locks.ReentrantLock"
    );

    private static final Pattern CONCURRENT_TYPE = Pattern.compile(
            "\\bjava\\.util\\.concurrent(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+"
    );

    private static final List<String> FORBIDDEN_TEXT = List.of(
            "java.util.concurrent.*",
            "java.util.concurrent.locks.*",
            "Collections.synchronized",
            "parallelStream(",
            ".parallel()",
            "new Vector<",
            "new Hashtable<"
    );

    @Test
    void productionCodeDoesNotUseForbiddenConcurrencyUtilities() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<String> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> violations(path).stream())
                    .toList();

            assertTrue(
                    violations.isEmpty(),
                    () -> "Se encontraron APIs no permitidas:\n" + String.join("\n", violations)
            );
        }
    }

    private List<String> violations(Path path) {
        try {
            String source = withoutCommentsAndStrings(Files.readString(path));
            List<String> violations = new java.util.ArrayList<>(FORBIDDEN_TEXT.stream()
                    .filter(source::contains)
                    .map(text -> path + ": " + text)
                    .toList());

            Matcher matcher = CONCURRENT_TYPE.matcher(source);
            while (matcher.find()) {
                String type = matcher.group();
                if (!ALLOWED_CONCURRENT_TYPES.contains(type)) {
                    violations.add(path + ": " + type);
                }
            }

            return violations.stream().distinct().toList();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer " + path, exception);
        }
    }

    private String withoutCommentsAndStrings(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "")
                .replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"")
                .replaceAll("'(?:\\\\.|[^'\\\\])'", "''");
    }
}

package pe.logyx.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reglas de ADR-0002 verificadas sobre el código fuente (sin ArchUnit):
 * <ol>
 *   <li>El dominio es Java puro: sin Quarkus, JPA, Jakarta, Jackson ni MicroProfile.</li>
 *   <li>Un módulo nunca importa la {@code infrastructure} de otro módulo.</li>
 *   <li>{@code domain} y {@code application} no importan {@code infrastructure} (ni la propia).</li>
 * </ol>
 */
class ModuleBoundariesTest {

    private static final Path SRC = Path.of("src/main/java/pe/logyx");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(static\\s+)?([\\w.]+)", Pattern.MULTILINE);
    private static final List<String> FRAMEWORKS = List.of(
            "io.quarkus", "jakarta.", "javax.persistence", "com.fasterxml", "org.eclipse.microprofile",
            "org.hibernate", "io.smallrye");

    @Test
    void dominioEsJavaPuro() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : sources()) {
            if (!file.toString().contains("/domain/")) continue;
            for (String imp : imports(file)) {
                if (FRAMEWORKS.stream().anyMatch(imp::startsWith)) violations.add(SRC.relativize(file) + " → " + imp);
            }
        }
        assertTrue(violations.isEmpty(), "Dominio con dependencias de framework:\n" + String.join("\n", violations));
    }

    @Test
    void modulosSoloSeConocenPorApplicationODominio() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : sources()) {
            String module = module(file);
            String layer = SRC.relativize(file).getNameCount() > 2 ? SRC.relativize(file).getName(1).toString() : "";
            for (String imp : imports(file)) {
                if (!imp.startsWith("pe.logyx.")) continue;
                String[] parts = imp.split("\\.");
                String target = parts[2];
                boolean targetInfra = parts.length > 3 && parts[3].equals("infrastructure");
                if (targetInfra && !target.equals(module)) {
                    violations.add(SRC.relativize(file) + " → " + imp + " (infraestructura de otro módulo)");
                }
                if (targetInfra && (layer.equals("domain") || layer.equals("application"))) {
                    violations.add(SRC.relativize(file) + " → " + imp + " (capa interna depende de infraestructura)");
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> s = Files.walk(SRC)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String module(Path file) {
        return SRC.relativize(file).getName(0).toString();
    }

    private static List<String> imports(Path file) throws IOException {
        Matcher m = IMPORT.matcher(Files.readString(file));
        List<String> out = new ArrayList<>();
        while (m.find()) out.add(m.group(2));
        return out;
    }
}

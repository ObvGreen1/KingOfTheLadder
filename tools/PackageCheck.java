import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Architecture check for the KotL source tree. Not a JUnit test, for the same reason
 * {@code GlickoCheck} is not: it needs no test framework, prints each rule as it applies it, and
 * exits non-zero on the first failure by throwing.
 *
 * <p>It exists because the mistakes it catches are invisible to the compiler. A class parked in
 * the wrong package compiles. A cross-package call written as a fully-qualified name compiles. A
 * manager accumulating a helper that has nothing to do with it compiles. Only a rule stops them
 * coming back.</p>
 *
 * <h2>Rules</h2>
 * <ol>
 *   <li><b>Package matches path.</b> Every file under {@code src/main/java} must sit in the
 *       directory its {@code package} declaration names.</li>
 *   <li><b>No fully-qualified plugin references.</b> Code must reach another class through an
 *       import, never through a dotted name written at the call site. Comments are ignored, so
 *       javadoc may still spell a class out in full.</li>
 *   <li><b>Declared dependencies only.</b> Every import from one {@code me.obvgreen} package to
 *       another must be listed in {@link #ALLOWED}. A new package must be registered before it
 *       can be used, so adding a dependency is always a deliberate act.</li>
 *   <li><b>No cycles.</b> Apart from the composition root — which every wired package is
 *       allowed to name — the package graph must be acyclic.</li>
 * </ol>
 *
 * <p>Run it with {@code ./gradlew checkPackages}, which {@code build} depends on.</p>
 */
public final class PackageCheck {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /** The composition root: the plugin class itself. Every other package may name it. */
    private static final String ROOT = "me.obvgreen";

    /**
     * The permitted import graph, one {@code package=dep,dep} line per package.
     *
     * <p>An empty right-hand side means the package must import nothing from the plugin at all.
     * {@link #ROOT} is deliberately exempt from the cycle rule: a Bukkit plugin's managers take
     * the plugin instance in their constructor, so they name the root, and the root names them
     * back to wire them up. Every other edge below runs in one direction only.</p>
     */
    private static final String ALLOWED = """
            me.obvgreen=arena,command,config,database,dialog,dialog.setup,glicko,item,listener,placeholder,text
            me.obvgreen.arena=config,database,glicko,text,me.obvgreen
            me.obvgreen.command=arena,database,text,me.obvgreen
            me.obvgreen.config=
            me.obvgreen.database=glicko
            me.obvgreen.dialog=database,text,me.obvgreen
            me.obvgreen.dialog.setup=arena,command,dialog,text,me.obvgreen
            me.obvgreen.glicko=
            me.obvgreen.item=text
            me.obvgreen.listener=arena,item,text,me.obvgreen
            me.obvgreen.placeholder=arena,database,dialog,me.obvgreen
            me.obvgreen.text=
            """;

    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([\\w.*]+)");
    private static final Pattern QUALIFIED = Pattern.compile("me\\.obvgreen\\.");

    private final Map<String, Set<String>> allowed = new LinkedHashMap<>();
    private final Map<String, Set<String>> actual = new TreeMap<>();
    private final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        PackageCheck check = new PackageCheck();
        check.parseAllowList();
        check.scan();
        check.checkDeclaredPackagesAreRegistered();
        check.checkQualifiedReferences();
        check.checkAllowedDependencies();
        check.checkNoCycles();
        check.report();
    }

    private void parseAllowList() {
        for (String line : ALLOWED.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            int split = line.indexOf('=');
            String pkg = line.substring(0, split);
            String deps = line.substring(split + 1);
            Set<String> allowedDeps = new TreeSet<>();
            for (String dep : deps.split(",")) {
                if (!dep.isBlank()) {
                    String trimmed = dep.trim();
                    // A dependency may be written short (config) or fully qualified
                    // (me.obvgreen); both name the same target package.
                    allowedDeps.add(trimmed.equals(ROOT) || trimmed.startsWith(ROOT + ".")
                            ? trimmed
                            : ROOT + "." + trimmed);
                }
            }
            allowed.put(pkg, allowedDeps);
        }
    }

    private void scan() throws IOException {
        if (!Files.isDirectory(SOURCE_ROOT)) {
            throw new IllegalStateException("No source tree at " + SOURCE_ROOT.toAbsolutePath());
        }
        List<Path> files;
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            files = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        if (files.isEmpty()) {
            throw new IllegalStateException("No .java files found under " + SOURCE_ROOT);
        }

        // The set of real package names, needed to work out which package an import belongs to.
        Set<String> declared = new HashSet<>();
        Map<Path, String> packageOf = new LinkedHashMap<>();
        for (Path file : files) {
            String source = read(file);
            Matcher matcher = PACKAGE.matcher(source);
            if (!matcher.find()) {
                failures.add(rel(file) + ": no package declaration");
                continue;
            }
            String pkg = matcher.group(1);
            declared.add(pkg);
            packageOf.put(file, pkg);
            if (!directoryFor(pkg).equals(parentOf(file))) {
                failures.add(rel(file) + ": declares package " + pkg
                        + " but sits in " + parentOf(file));
            }
        }
        pass("path matches package (" + files.size() + " files)");

        for (Map.Entry<Path, String> entry : packageOf.entrySet()) {
            String from = entry.getValue();
            String source = read(entry.getKey());
            for (Matcher matcher = IMPORT.matcher(source); matcher.find(); ) {
                String imported = matcher.group(1);
                if (imported.endsWith(".*") || !imported.startsWith(ROOT)) {
                    continue;
                }
                String owner = owningPackage(imported, declared);
                if (owner == null || owner.equals(from)) {
                    continue;
                }
                actual.computeIfAbsent(from, key -> new TreeSet<>()).add(owner);
            }
        }
    }

    /** The declared package that owns {@code typeName}, or null if it is not a plugin type. */
    private static String owningPackage(String typeName, Set<String> declared) {
        String best = null;
        for (String pkg : declared) {
            if (!typeName.startsWith(pkg + ".")) {
                continue;
            }
            String remainder = typeName.substring(pkg.length() + 1);
            if (remainder.indexOf('.') >= 0) {
                continue;
            }
            if (best == null || pkg.length() > best.length()) {
                best = pkg;
            }
        }
        return best;
    }

    private void checkDeclaredPackagesAreRegistered() {
        List<String> unregistered = actual.keySet().stream()
                .filter(pkg -> !allowed.containsKey(pkg))
                .toList();
        for (String pkg : unregistered) {
            failures.add(pkg + ": not in the allow list — add it to ALLOWED in tools/PackageCheck.java "
                    + "with the packages it is allowed to depend on");
        }
        if (unregistered.isEmpty()) {
            pass("every package is registered in the allow list (" + actual.size() + " packages)");
        }
    }

    private void checkQualifiedReferences() throws IOException {
        int offenders = 0;
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                int line = 0;
                boolean inBlockComment = false;
                for (String text : read(file).split("\\R", -1)) {
                    line++;
                    String trimmed = text.strip();
                    if (inBlockComment) {
                        inBlockComment = !trimmed.contains("*/");
                        continue;
                    }
                    if (trimmed.startsWith("/*")) {
                        inBlockComment = !trimmed.contains("*/");
                        continue;
                    }
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")
                            || trimmed.startsWith("package ") || trimmed.startsWith("import ")) {
                        continue;
                    }
                    if (QUALIFIED.matcher(text).find()) {
                        failures.add(rel(file) + ":" + line + ": fully-qualified plugin reference — "
                                + "add an import and use the simple name");
                        offenders++;
                    }
                }
            }
        }
        if (offenders == 0) {
            pass("no fully-qualified plugin references in code");
        }
    }

    private void checkAllowedDependencies() {
        int checked = 0;
        for (Map.Entry<String, Set<String>> entry : actual.entrySet()) {
            Set<String> permitted = allowed.get(entry.getKey());
            if (permitted == null) {
                continue;
            }
            for (String dependency : entry.getValue()) {
                checked++;
                if (!permitted.contains(dependency)) {
                    failures.add(entry.getKey() + " may not depend on " + dependency
                            + " — move the code, or extend ALLOWED if the dependency is deliberate");
                }
            }
        }
        if (failures.stream().noneMatch(f -> f.contains("may not depend on"))) {
            pass("every cross-package import is declared (" + checked + " edges)");
        }
    }

    /**
     * Kahn's algorithm over the package graph with the composition root removed. The root is
     * exempt because every wired package names the plugin class and the plugin class names them
     * back; that one edge is the composition root doing its job, not a cycle between two
     * collaborators.
     */
    private void checkNoCycles() {
        Map<String, Set<String>> graph = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : actual.entrySet()) {
            if (entry.getKey().equals(ROOT)) {
                continue;
            }
            Set<String> edges = new TreeSet<>();
            for (String dependency : entry.getValue()) {
                if (!dependency.equals(ROOT)) {
                    edges.add(dependency);
                }
            }
            graph.put(entry.getKey(), edges);
        }

        Map<String, Integer> inDegree = new TreeMap<>();
        for (String node : graph.keySet()) {
            inDegree.putIfAbsent(node, 0);
        }
        for (Set<String> edges : graph.values()) {
            for (String target : edges) {
                inDegree.merge(target, 1, Integer::sum);
            }
        }

        Deque<String> ready = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }

        int settled = 0;
        while (!ready.isEmpty()) {
            String node = ready.remove();
            settled++;
            for (String target : graph.getOrDefault(node, Set.of())) {
                if (inDegree.merge(target, -1, Integer::sum) == 0) {
                    ready.add(target);
                }
            }
        }

        if (settled == inDegree.size()) {
            pass("package graph is acyclic apart from the composition root");
        } else {
            List<String> stuck = inDegree.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .toList();
            failures.add("package dependency cycle among " + stuck
                    + " — break it by moving shared code down into a package both can depend on");
        }
    }

    private void report() {
        for (String line : ALLOWED.split("\\R")) {
            if (!line.isBlank()) {
                System.out.println("  allowed  " + line);
            }
        }
        if (failures.isEmpty()) {
            System.out.println("ALL CHECKS PASSED");
            return;
        }
        System.err.println("PACKAGE CHECK FAILED");
        for (String failure : failures) {
            System.err.println("  " + failure);
        }
        throw new IllegalStateException(failures.size() + " package layout violation(s)");
    }

    private static void pass(String message) {
        System.out.println("PASS  " + message);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + file, exception);
        }
    }

    private static String rel(Path file) {
        return SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
    }

    private static Path parentOf(Path file) {
        return file.getParent();
    }

    /** The directory a package name maps to, relative to {@link #SOURCE_ROOT}. */
    private static Path directoryFor(String pkg) {
        return SOURCE_ROOT.resolve(pkg.replace('.', '/'));
    }
}

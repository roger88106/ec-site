package com.ecsite.modulith;

import com.ecsite.ProductionClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleDependency;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyType;
import org.springframework.modulith.core.EventType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全体構成の文書（docs/design/overview.md）の図とコードの一致の検証。<br />
 * モジュールの依存とイベントの図は、Spring Modulith のモジュールの検出結果から作り、文書の生成の範囲と比べる。
 * 外部システムの図は手で書くので、図に出るモジュールの名前の一覧だけを、コードのモジュールと比べる。
 * システムプロパティ {@code ecsite.update-docs} が {@code true} なら、比べずに文書の生成の範囲を書き直す。
 */
class ModuleDiagramTest {

    /** 全体構成の文書（Gradle がシステムプロパティで渡す） */
    private static final Path OVERVIEW = Path.of(System.getProperty("design.overview"));

    private static final String GENERATED_START = "<!-- generated:modules -->";
    private static final String GENERATED_END = "<!-- /generated:modules -->";
    private static final String EXTERNAL_START = "<!-- external-systems -->";
    private static final String EXTERNAL_END = "<!-- /external-systems -->";

    /** 手書きの図で、モジュールを表す節点（{@code module_<名前>}） */
    private static final Pattern MODULE_NODE = Pattern.compile("\\bmodule_([a-z]+)\\b");

    private final ApplicationModules modules = ProductionClasses.applicationModules();

    @Test
    @DisplayName("全体構成の文書のモジュールの依存とイベントの図が、コードから作った図と一致する")
    void generatedDiagramMatchesCode() throws IOException {
        var expected = render(modules);
        var document = Files.readString(OVERVIEW, StandardCharsets.UTF_8);
        if (Boolean.getBoolean("ecsite.update-docs")) {
            Files.writeString(OVERVIEW, replaceBetween(document, GENERATED_START, GENERATED_END, expected),
                    StandardCharsets.UTF_8);
            return;
        }
        assertThat(between(document, GENERATED_START, GENERATED_END))
                .as("モジュールの依存かイベントが変わった。backend/ecsite で "
                        + "./gradlew test --tests ModuleDiagramTest -PupdateDocs を流して文書の図を書き直し、"
                        + "差分を確認して commit する")
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("全体構成の文書の外部システムの図に、コードのモジュールがすべてあり、ほかのモジュールがない")
    void externalSystemsDiagramListsAllModules() throws IOException {
        var document = Files.readString(OVERVIEW, StandardCharsets.UTF_8);
        var names = new TreeSet<String>();
        Matcher matcher = MODULE_NODE.matcher(between(document, EXTERNAL_START, EXTERNAL_END));
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        assertThat(names)
                .as("外部システムの図のモジュール（module_<名前> の節点）を、コードのモジュールに合わせる")
                .containsExactlyInAnyOrderElementsOf(moduleNames(modules));
    }

    @Test
    @DisplayName("図は、依存を実線、イベントを発行するモジュールから受けるモジュールへの点線で書き、同じ組の線を 1 本にまとめる")
    void rendersDependenciesAndEvents() {
        var rendered = render(modules);

        assertThat(rendered).startsWith("```mermaid\nflowchart LR\n");
        assertThat(rendered).contains("    order --> payment\n");
        assertThat(rendered).contains("    payment -. PaymentAuthorized<br>PaymentExpired .-> order\n");
        assertThat(rendered.lines().filter(line -> line.contains("-->") || line.contains(".->")))
                .as("同じモジュールの組の線は 1 本にまとめる")
                .doesNotHaveDuplicates();
    }

    /** モジュールの依存とイベントの図（Mermaid のコードブロック）。並びは名前の順で、毎回同じ結果になる */
    static String render(ApplicationModules modules) {
        var lines = new ArrayList<String>();
        lines.add("```mermaid");
        lines.add("flowchart LR");
        moduleNames(modules).forEach(name -> lines.add("    " + name));
        dependencies(modules).forEach((source, targets) ->
                targets.forEach(target -> lines.add("    " + source + " --> " + target)));
        events(modules).forEach((edge, names) ->
                lines.add("    " + edge.publisher() + " -. " + String.join("<br>", names) + " .-> " + edge.listener()));
        lines.add("```");
        return String.join("\n", lines) + "\n";
    }

    private static Set<String> moduleNames(ApplicationModules modules) {
        return modules.stream().map(ApplicationModule::getIdentifier).map(Object::toString)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** イベントのほかの依存（モジュールから、使うモジュールへ） */
    private static Map<String, Set<String>> dependencies(ApplicationModules modules) {
        var eventTypes = modules.stream().flatMap(module -> module.getPublishedEvents().stream())
                .map(EventType::getType).map(JavaClass::getName).collect(Collectors.toSet());
        var result = new TreeMap<String, Set<String>>();
        modules.forEach(module -> {
            var targets = module.getDirectDependencies(modules).stream()
                    .filter(dependency -> dependency.getDependencyType() != DependencyType.EVENT_LISTENER)
                    .filter(dependency -> !eventTypes.contains(dependency.getTargetType().getName()))
                    .map(ApplicationModuleDependency::getTargetModule)
                    .map(target -> target.getIdentifier().toString())
                    .collect(Collectors.toCollection(TreeSet::new));
            if (!targets.isEmpty()) {
                result.put(module.getIdentifier().toString(), targets);
            }
        });
        return result;
    }

    /** イベントの流れ（発行するモジュールから受けるモジュールへ）と、イベントの名前 */
    private static Map<EventEdge, Set<String>> events(ApplicationModules modules) {
        var result = new TreeMap<EventEdge, Set<String>>();
        modules.forEach(listener -> listener.getEventsListenedTo(modules).forEach(event -> {
            var publisher = modules.getModuleByType(event).orElseThrow(
                    () -> new IllegalStateException("イベントを発行するモジュールが見つからない：" + event.getName()));
            if (publisher.equals(listener)) {
                return;
            }
            result.computeIfAbsent(new EventEdge(publisher.getIdentifier().toString(),
                    listener.getIdentifier().toString()), edge -> new TreeSet<>()).add(event.getSimpleName());
        }));
        return result;
    }

    private static String between(String document, String start, String end) {
        int from = document.indexOf(start);
        int to = document.indexOf(end);
        assertThat(from).as("文書に " + start + " がある").isNotNegative();
        assertThat(to).as("文書に " + end + " がある").isGreaterThan(from);
        return document.substring(from + start.length(), to).strip() + "\n";
    }

    private static String replaceBetween(String document, String start, String end, String content) {
        int from = document.indexOf(start);
        int to = document.indexOf(end);
        if (from < 0 || to < from) {
            throw new IllegalStateException("文書に " + start + " と " + end + " がない");
        }
        return document.substring(0, from + start.length()) + "\n" + content + document.substring(to);
    }

    /** イベントの流れの 1 本 */
    private record EventEdge(String publisher, String listener) implements Comparable<EventEdge> {

        @Override
        public int compareTo(EventEdge other) {
            int byPublisher = publisher.compareTo(other.publisher);
            return byPublisher != 0 ? byPublisher : listener.compareTo(other.listener);
        }
    }
}

package com.ecsite.modulith;

import com.ecsite.ProductionClasses;
import com.ecsite.contract.Contracts;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 全体構成の文書の図とコードの一致の検証
 *
 * <p>モジュールの依存の図とモジュール間のイベントの図は、Spring Modulith のモジュールの検出結果から作り、文書の生成の範囲と比べる。
 * 図に出すモジュールとイベントの日本語の名前は、用語集から読む。
 * 外部システムの図は手で書くので、図に出るモジュールの名前が、コードのモジュールであることだけを確認する。
 * システムプロパティ {@code ecsite.update-docs} が {@code true} なら、比べずに文書の生成の範囲を書き直す。
 */
class ModuleDiagramTest {

    /**
     * 全体構成の文書（Gradle が system property で渡すリポジトリのルートから）
     */
    private static final Path OVERVIEW = Contracts.path("docs/02-設計/overview.md");

    /**
     * 用語集（モジュールとイベントの日本語の名前の正本）
     */
    private static final Path GLOSSARY = Contracts.path("docs/03-機能仕様/glossary.md");

    /**
     * モジュールの依存の図の、文書の生成の範囲の始まりの印
     */
    private static final String DEPENDENCIES_START = "<!-- generated:module-dependencies -->";
    /**
     * モジュールの依存の図の、文書の生成の範囲の終わりの印
     */
    private static final String DEPENDENCIES_END = "<!-- /generated:module-dependencies -->";
    /**
     * モジュール間のイベントの図の、文書の生成の範囲の始まりの印
     */
    private static final String EVENTS_START = "<!-- generated:module-events -->";
    /**
     * モジュール間のイベントの図の、文書の生成の範囲の終わりの印
     */
    private static final String EVENTS_END = "<!-- /generated:module-events -->";
    /**
     * 文書の手書きの外部システムの図（2 つ）をまとめて囲む始まりの印
     */
    private static final String EXTERNAL_START = "<!-- external-systems -->";
    /**
     * 文書の手書きの外部システムの図（2 つ）をまとめて囲む終わりの印
     */
    private static final String EXTERNAL_END = "<!-- /external-systems -->";

    /**
     * 手書きの図で、モジュールを表すノード（{@code module_<名前>}）
     */
    private static final Pattern MODULE_NODE = Pattern.compile("\\bmodule_([a-z]+)\\b");

    /**
     * 用語集の表の行（{@code | 論理名 | `識別子` | …}）
     */
    private static final Pattern GLOSSARY_ROW = Pattern.compile("^\\|\\s*([^|]+?)\\s*\\|\\s*`([A-Za-z0-9_]+)`\\s*\\|");

    /**
     * 用語集で、モジュールの論理名の末尾にある語
     */
    private static final String MODULE_SUFFIX = "モジュール";

    /**
     * Spring Modulith のモジュールの一覧（テストのクラスを除く）
     */
    private final ApplicationModules modules = ProductionClasses.applicationModules();

    @Test
    @DisplayName("全体構成の文書のモジュールの依存の図が、コードから作った図と一致する")
    void dependenciesDiagramMatchesCode() throws IOException {
        var expected = renderDependencies(diagramOf(modules), glossary().modules());
        verifyOrUpdate(expected, DEPENDENCIES_START, DEPENDENCIES_END, "モジュールの依存");
    }

    @Test
    @DisplayName("全体構成の文書のモジュール間のイベントの図が、コードから作った図と一致する")
    void eventsDiagramMatchesCode() throws IOException {
        var glossary = glossary();
        var expected = renderEvents(diagramOf(modules), glossary.modules(), glossary.events());
        verifyOrUpdate(expected, EVENTS_START, EVENTS_END, "モジュール間のイベント");
    }

    @Test
    @DisplayName("全体構成の文書の外部システムの図の module_<名前> のノードが、全てコードのモジュールである")
    void externalSystemsDiagramDrawsOnlyExistingModules() throws IOException {
        var names = externalModuleNames(Files.readString(OVERVIEW, StandardCharsets.UTF_8));
        assertThat(names)
                .as("外部システムの図のモジュール（module_<名前> のノード）は、コードにあるモジュールだけにする")
                .isSubsetOf(moduleNames(modules));
    }

    @Test
    @DisplayName("外部システムの図に module_<名前> のノードが 1 つもないと、図の範囲を示して失敗する")
    void externalSystemsDiagramWithoutModuleNodesFails() {
        var document = EXTERNAL_START + "\n```mermaid\nflowchart LR\n    carrier[\"物流サービス\"]\n```\n" + EXTERNAL_END;

        assertThatThrownBy(() -> externalModuleNames(document))
                .hasMessageContaining("module_<名前>")
                .hasMessageContaining(EXTERNAL_START);
    }

    @Test
    @DisplayName("用語集の節が順番を入れ替えて並んでいても、モジュールとイベントの両方の名前を読める")
    void glossarySectionsCanBeInAnyOrder() {
        var glossary = parseGlossary(List.of(
                "## イベント", "| 用語 | 識別子 | 発行元 |", "|---|---|---|", "| 支払の期限切れ | `PaymentExpired` | 決済 |",
                "## モジュール", "| 用語 | 識別子 | 意味 |", "|---|---|---|", "| 決済モジュール | `payment` | 支払 |"));

        assertThat(glossary.modules()).containsExactly(Map.entry("payment", "決済"));
        assertThat(glossary.events()).containsExactly(Map.entry("PaymentExpired", "支払の期限切れ"));
    }

    @Test
    @DisplayName("用語集に「## モジュール」か「## イベント」の節がないと、節の名前を示して失敗する")
    void glossaryWithoutSectionFails() {
        var eventsOnly = List.of("## イベント", "| 支払の期限切れ | `PaymentExpired` | 決済 |");
        var modulesOnly = List.of("## モジュール", "| 決済モジュール | `payment` | 支払 |");

        assertThatThrownBy(() -> parseGlossary(eventsOnly)).hasMessageContaining("## モジュール");
        assertThatThrownBy(() -> parseGlossary(modulesOnly)).hasMessageContaining("## イベント");
    }

    @Test
    @DisplayName("用語集の節の中で 1 行も読めないと、節の名前を示して失敗する")
    void glossarySectionWithoutRowsFails() {
        var lines = List.of(
                "## モジュール", "| 用語 | 意味 | 識別子 |", "| 決済モジュール | 支払 | payment |",
                "## イベント", "| 支払の期限切れ | `PaymentExpired` | 決済 |");

        assertThatThrownBy(() -> parseGlossary(lines)).hasMessageContaining("## モジュール")
                .hasMessageContaining("1 行も");
    }

    @Test
    @DisplayName("用語集のモジュールの論理名の末尾が「モジュール」でないと、その行を示して失敗する")
    void glossaryModuleRowWithoutSuffixFails() {
        var lines = List.of(
                "## モジュール", "| 決済 | `payment` | 支払 |",
                "## イベント", "| 支払の期限切れ | `PaymentExpired` | 決済 |");

        assertThatThrownBy(() -> parseGlossary(lines)).hasMessageContaining("| 決済 | `payment` | 支払 |");
    }

    @Test
    @DisplayName("用語集の同じ節に同じ識別子の行が 2 つあると、その行を示して失敗する")
    void glossaryDuplicateIdentifierFails() {
        var lines = List.of(
                "## モジュール", "| 決済モジュール | `payment` | 支払 |", "| 支払モジュール | `payment` | 支払 |",
                "## イベント", "| 支払の期限切れ | `PaymentExpired` | 決済 |");

        assertThatThrownBy(() -> parseGlossary(lines)).hasMessageContaining("| 支払モジュール | `payment` | 支払 |");
    }

    @Test
    @DisplayName("用語集にモジュールかイベントの名前がないと、追加する名前を示して失敗する")
    void missingGlossaryNamesFail() {
        var events = new TreeMap<EventEdge, Set<String>>();
        events.put(new EventEdge("payment", "order"), new TreeSet<>(Set.of("PaymentExpired")));
        var diagram = new Diagram(new TreeSet<>(Set.of("order", "payment")), Map.of(), events);

        assertThatThrownBy(() -> renderDependencies(diagram, Map.of("order", "注文")))
                .hasMessageContaining("payment");
        assertThatThrownBy(() -> renderEvents(diagram, Map.of("order", "注文", "payment", "決済"), Map.of()))
                .hasMessageContaining("PaymentExpired");
    }

    @Test
    @DisplayName("固定した内容から、依存だけを上から下に並べた図に描く")
    void rendersDependencies() {
        var dependencies = new TreeMap<String, Set<String>>();
        dependencies.put("order", new TreeSet<>(Set.of("shipping", "payment")));
        var diagram = new Diagram(new TreeSet<>(Set.of("order", "payment", "shipping")), dependencies, Map.of());

        var rendered = renderDependencies(diagram, Map.of("order", "注文", "payment", "決済", "shipping", "出荷"));

        // 依存の抽出は、固定した内容では確認できない。dependenciesDiagramMatchesCode が、コードから作った図を文書と比べて確認する
        assertThat(rendered).isEqualTo("""
                ```mermaid
                flowchart TB
                    order["注文"]
                    payment["決済"]
                    shipping["出荷"]
                    order --> payment
                    order --> shipping
                ```
                """);
    }

    @Test
    @DisplayName("固定した内容から、イベントだけを、発行するモジュールから受けるモジュールへの点線で左から右に並べた図に描く")
    void rendersEvents() {
        var events = new TreeMap<EventEdge, Set<String>>();
        events.put(new EventEdge("payment", "order"), new TreeSet<>(Set.of("PaymentExpired", "PaymentAuthorized")));
        var diagram = new Diagram(new TreeSet<>(Set.of("order", "payment", "shipping")), Map.of(), events);

        var rendered = renderEvents(diagram, Map.of("order", "注文", "payment", "決済", "shipping", "出荷"),
                Map.of("PaymentAuthorized", "与信の成功", "PaymentExpired", "支払の期限切れ"));

        // イベントの組ごとの集約は、固定した内容では確認できない。eventsDiagramMatchesCode が、コードから作った図を文書と比べて確認する
        assertThat(rendered).isEqualTo("""
                ```mermaid
                flowchart LR
                    order["注文"]
                    payment["決済"]
                    payment -. 与信の成功<br>支払の期限切れ .-> order
                ```
                """);
    }

    /**
     * 作った図を、文書の生成の範囲と比べる。
     *
     * <p>更新のモードなら、比べずに範囲を書き直す。
     *
     * @param expected 作った図
     * @param start    始まりの印
     * @param end      終わりの印
     * @param title    図の名前（失敗のメッセージに出す）
     * @throws IOException 文書を読み書きできないとき
     */
    private static void verifyOrUpdate(String expected, String start, String end, String title) throws IOException {
        var document = Files.readString(OVERVIEW, StandardCharsets.UTF_8);
        if (Boolean.getBoolean("ecsite.update-docs")) {
            Files.writeString(OVERVIEW, replaceBetween(document, start, end, expected), StandardCharsets.UTF_8);
            return;
        }
        assertThat(between(document, start, end))
                .as(title + "の図が、文書と違う。コードか用語集の名前が変わった。backend/ecsite で "
                        + "./gradlew test --tests ModuleDiagramTest -PupdateDocs を実行して再生成し、"
                        + "差分を確認して commit する")
                .isEqualTo(expected);
    }

    /**
     * 用語集から、モジュールとイベントの日本語の名前を読む。
     *
     * @return 用語集の名前
     * @throws IOException 用語集を読めないとき
     */
    private static Glossary glossary() throws IOException {
        return parseGlossary(Files.readAllLines(GLOSSARY, StandardCharsets.UTF_8));
    }

    /**
     * 用語集の行から、モジュールとイベントの名前を読む。
     *
     * <p>節は見出しで見つけるので、節の順に依存しない。次のときは、原因の節か行を示して失敗する。節がない。節の中で行を 1 つも読めない。
     * モジュールの論理名の末尾が「モジュール」でない。同じ識別子の行が 2 つある。
     *
     * @param lines 用語集の行
     * @return 用語集の名前
     */
    private static Glossary parseGlossary(List<String> lines) {
        var names = new EnumMap<GlossarySection, Map<String, String>>(GlossarySection.class);
        GlossarySection section = null;
        for (var line : lines) {
            if (line.startsWith("## ")) {
                // 見出しで、読む表（モジュール、イベント、ほかは読まない）を切り替える
                section = GlossarySection.ofHeading(line.substring(3).strip());
                if (section != null) {
                    names.putIfAbsent(section, new TreeMap<>());
                }
                continue;
            }
            var matcher = GLOSSARY_ROW.matcher(line);
            if (section == null || !matcher.find()) {
                continue;
            }
            var label = matcher.group(1);
            if (section == GlossarySection.MODULE) {
                if (!label.endsWith(MODULE_SUFFIX)) {
                    throw new IllegalStateException("用語集のモジュールの論理名の末尾が「" + MODULE_SUFFIX + "」でない行：" + line);
                }
                label = label.substring(0, label.length() - MODULE_SUFFIX.length());
            }
            if (names.get(section).put(matcher.group(2), label) != null) {
                throw new IllegalStateException("用語集の『" + section.heading() + "』の節に、同じ識別子の行が 2 つある：" + line);
            }
        }
        for (var required : GlossarySection.values()) {
            if (!names.containsKey(required)) {
                throw new IllegalStateException("用語集（docs/03-機能仕様/glossary.md）に『" + required.heading() + "』の節がない");
            }
            if (names.get(required).isEmpty()) {
                throw new IllegalStateException("用語集の『" + required.heading() + "』"
                        + "の節に、| 論理名 | `識別子` | … | の形の行が 1 行もない");
            }
        }
        return new Glossary(names.get(GlossarySection.MODULE), names.get(GlossarySection.EVENT));
    }

    /**
     * 文書の手書きの外部システムの図から、{@code module_<名前>} のノードのモジュールの名前を集める。
     *
     * <p>ノードが 1 つもないと、図の範囲を示して失敗する（検査が何も確認しないまま通らないようにする）。
     *
     * @param document 文書
     * @return モジュールの名前（名前の順）
     */
    private static Set<String> externalModuleNames(String document) {
        var names = new TreeSet<String>();
        Matcher matcher = MODULE_NODE.matcher(between(document, EXTERNAL_START, EXTERNAL_END));
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        if (names.isEmpty()) {
            throw new IllegalStateException(EXTERNAL_START + " と " + EXTERNAL_END
                    + " の間に、module_<名前> のノードが 1 つもない");
        }
        return names;
    }

    /**
     * 用語集に、必要な名前が全てあることを確認する。
     *
     * @param needed 必要な識別子
     * @param known  用語集の、識別子から日本語の名前
     * @param kind   名前の種類（メッセージに出す）
     */
    private static void requireNames(Set<String> needed, Map<String, String> known, String kind) {
        var missing = needed.stream().filter(name -> !known.containsKey(name))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!missing.isEmpty()) {
            throw new IllegalStateException("用語集（docs/03-機能仕様/glossary.md）に" + kind
                    + "の名前がない。追加する識別子：" + missing);
        }
    }

    /**
     * モジュールの検出結果から、図に描く内容を作る。
     *
     * @param modules モジュールの一覧
     * @return 図に描く内容
     */
    private static Diagram diagramOf(ApplicationModules modules) {
        return new Diagram(moduleNames(modules), dependencies(modules), events(modules));
    }

    /**
     * モジュールの依存の図（Mermaid のコードブロック）を描く。
     *
     * <p>実線だけで、上から下に並べる。並びは名前の順で、毎回同じ結果になる。
     *
     * @param diagram     図に描く内容
     * @param moduleNames モジュールの識別子から日本語の名前
     * @return Mermaid のコードブロック
     */
    private static String renderDependencies(Diagram diagram, Map<String, String> moduleNames) {
        requireNames(diagram.modules(), moduleNames, "モジュール");
        var lines = new ArrayList<String>();
        lines.add("```mermaid");
        lines.add("flowchart TB");
        diagram.modules().forEach(name -> lines.add("    " + node(name, moduleNames)));
        diagram.dependencies().forEach((source, targets) ->
                targets.forEach(target -> lines.add("    " + source + " --> " + target)));
        lines.add("```");
        return String.join("\n", lines) + "\n";
    }

    /**
     * モジュール間のイベントの図（Mermaid のコードブロック）を描く。
     *
     * <p>点線だけで、左から右に並べる。
     * イベントを発行するモジュールと、受けるモジュールだけをノードにする。並びは名前の順で、毎回同じ結果になる。
     *
     * @param diagram     図に描く内容
     * @param moduleNames モジュールの識別子から日本語の名前
     * @param eventNames  イベントのクラス名から日本語の名前
     * @return Mermaid のコードブロック
     */
    private static String renderEvents(Diagram diagram, Map<String, String> moduleNames,
                                       Map<String, String> eventNames) {
        var involved = new TreeSet<String>();
        diagram.events().keySet().forEach(edge -> {
            involved.add(edge.publisher());
            involved.add(edge.listener());
        });
        requireNames(involved, moduleNames, "モジュール");
        // 全てのイベントの流れから、イベントのクラス名を集める
        requireNames(diagram.events().values().stream().flatMap(Set::stream).collect(Collectors.toSet()),
                eventNames, "イベント");
        var lines = new ArrayList<String>();
        lines.add("```mermaid");
        lines.add("flowchart LR");
        involved.forEach(name -> lines.add("    " + node(name, moduleNames)));
        diagram.events().forEach((edge, names) -> lines.add("    " + edge.publisher() + " -. "
                + names.stream().map(eventNames::get).collect(Collectors.joining("<br>"))
                + " .-> " + edge.listener()));
        lines.add("```");
        return String.join("\n", lines) + "\n";
    }

    /**
     * ノードの行（{@code cart["カート"]}）を作る。
     *
     * @param id          モジュールの識別子
     * @param moduleNames モジュールの識別子から日本語の名前
     * @return ノード
     */
    private static String node(String id, Map<String, String> moduleNames) {
        return id + "[\"" + moduleNames.get(id) + "\"]";
    }

    /**
     * モジュールの名前を返す。
     *
     * @param modules モジュールの一覧
     * @return モジュールの名前（名前の順）
     */
    private static Set<String> moduleNames(ApplicationModules modules) {
        return modules.stream().map(ApplicationModule::getIdentifier).map(Object::toString)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * イベント以外の依存（モジュールから、使うモジュールへ）を集める。
     *
     * @param modules モジュールの一覧
     * @return モジュールの名前から、使うモジュールの名前の一覧
     */
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

    /**
     * イベントの流れ（発行するモジュールから受けるモジュールへ）と、イベントのクラス名を集める。
     *
     * @param modules モジュールの一覧
     * @return イベントの流れから、イベントのクラス名の一覧
     */
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

    /**
     * 文書の、始まりの印と終わりの印の間を返す。
     *
     * @param document 文書
     * @param start    始まりの印
     * @param end      終わりの印
     * @return 印の間の文字列（前後の空白を除き、末尾に改行を付ける）
     */
    private static String between(String document, String start, String end) {
        int from = document.indexOf(start);
        int to = document.indexOf(end);
        assertThat(from).as("文書に " + start + " がある").isNotNegative();
        assertThat(to).as("文書に " + end + " がある").isGreaterThan(from);
        return document.substring(from + start.length(), to).strip() + "\n";
    }

    /**
     * 文書の、始まりの印と終わりの印の間を、指定した内容に置き換える。
     *
     * @param document 文書
     * @param start    始まりの印
     * @param end      終わりの印
     * @param content  新しい内容
     * @return 置き換えた後の文書
     */
    private static String replaceBetween(String document, String start, String end, String content) {
        int from = document.indexOf(start);
        int to = document.indexOf(end);
        if (from < 0 || to < from) {
            throw new IllegalStateException("文書に " + start + " と " + end + " がない");
        }
        return document.substring(0, from + start.length()) + "\n" + content + document.substring(to);
    }

    /**
     * 図に描く内容
     *
     * @param modules      モジュールの名前
     * @param dependencies モジュールから、使うモジュールへの依存（イベント以外）
     * @param events       イベントの流れと、その流れのイベントのクラス名
     */
    private record Diagram(Set<String> modules, Map<String, Set<String>> dependencies,
                           Map<EventEdge, Set<String>> events) {
    }

    /**
     * 用語集で、図に使う名前を読む節
     */
    private enum GlossarySection {
        MODULE("モジュール"),
        EVENT("イベント");

        private final String heading;

        GlossarySection(String heading) {
            this.heading = heading;
        }

        /**
         * 節の見出し（{@code ## } の後の文字列）を、節の種類にする。
         *
         * @param heading 見出し
         * @return 節の種類。読まない節なら null
         */
        static GlossarySection ofHeading(String heading) {
            for (var section : values()) {
                if (section.heading.equals(heading)) {
                    return section;
                }
            }
            return null;
        }

        /**
         * 節の見出し（{@code ## } を付けた形）
         *
         * @return 見出し
         */
        String heading() {
            return "## " + heading;
        }
    }

    /**
     * 用語集のモジュールとイベントの名前
     *
     * @param modules モジュールの識別子から、論理名（末尾の「モジュール」を除く）
     * @param events  イベントのクラス名から、論理名
     */
    private record Glossary(Map<String, String> modules, Map<String, String> events) {
    }

    /**
     * イベントの流れの 1 本
     */
    private record EventEdge(String publisher, String listener) implements Comparable<EventEdge> {

        @Override
        public int compareTo(EventEdge other) {
            int byPublisher = publisher.compareTo(other.publisher);
            return byPublisher != 0 ? byPublisher : listener.compareTo(other.listener);
        }
    }
}

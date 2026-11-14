package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.ecsite.contract.Contracts;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * パッケージの大きさの検査
 *
 * <p>根拠はバックエンドの書き方の「パッケージの大きさ」。
 * パッケージの直下のクラスの数を本番のクラスファイルから数え、上限と、目安を超えるパッケージの表を確認する。
 * モジュールの構成の、ほかの観点は次のクラスが確認する。
 * <ul>
 *   <li>同じモジュールの中の層の依存：{@code LayerDependencyTest}</li>
 *   <li>共通コード、設定、モジュールの間の依存：{@code CommonDependencyTest}</li>
 *   <li>注釈を付ける層：{@code AnnotationPlacementTest}</li>
 *   <li>他のモジュールの api の使い方：{@code ModuleApiUseTest}</li>
 *   <li>token の取り出しを使う場所：{@code TokenAccessTest}</li>
 * </ul>
 */
class ModuleStructureTest {

    // ============================== 定数 ==============================

    /**
     * 1 つのパッケージの直下に置けるクラスの数の上限
     */
    private static final int MAX_CLASSES_PER_PACKAGE = 20;

    /**
     * 1 つのパッケージの直下のクラスの数の目安
     *
     * <p>超えて分けないパッケージは、バックエンドの書き方の「パッケージの大きさ」の表に理由を書く。
     */
    private static final int GUIDELINE_CLASSES_PER_PACKAGE = 10;

    /**
     * バックエンドの書き方
     *
     * <p>Gradle が system property で渡すリポジトリのルートから読む。
     */
    private static final Path RULES_BACKEND_CODING = Contracts.path("docs/05-開発規則/backend-coding.md");

    /**
     * バックエンドの書き方で、パッケージの大きさの表がある節の見出しの名前
     *
     * <p>見出しの番号は問わない。
     */
    private static final String PACKAGE_SIZE_TITLE = "パッケージの大きさ";

    /**
     * パッケージの大きさの表がある節の見出しの行
     *
     * <p>{@code ### } の後に、あれば番号、続けて見出しの名前が付く。
     */
    private static final Pattern PACKAGE_SIZE_HEADING = Pattern.compile("^### (?:[\\d.]+\\s+)?" + PACKAGE_SIZE_TITLE + "\\s*$");

    /**
     * パッケージの大きさの表の行
     *
     * <p>1 列目がバッククォートで囲んだパッケージの名前。
     */
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|\\s*`([^`]+)`\\s*\\|.*$");

    // ============================== テスト ==============================

    @Test
    @DisplayName("1 つのパッケージの直下のクラスは 20 を超えない")
    void packagesAreSmallEnough() {
        // 準備：本番の手書きのパッケージごとのクラスの数
        Map<String, Long> countByPackage = classCountByPackage(ProductionClasses.all());
        ProductionClasses.requireNotEmpty(countByPackage.entrySet(), "手で書いたパッケージ");

        // 確認：全てのパッケージが上限以下
        assertThat(countByPackage).allSatisfy((name, count) ->
                assertThat(count).as(name).isLessThanOrEqualTo(MAX_CLASSES_PER_PACKAGE));
    }

    @Nested
    @DisplayName("目安を超えるパッケージの表")
    class OversizedTable {

        @Test
        @DisplayName("目安を超えるパッケージは、バックエンドの書き方の「パッケージの大きさ」の表の行と、パッケージが一致する")
        void oversizedPackagesMatchTable() throws IOException {
            // 準備：文書の表のパッケージ
            Set<String> table = oversizedTable(Files.readAllLines(RULES_BACKEND_CODING, StandardCharsets.UTF_8));
            ProductionClasses.requireNotEmpty(table, "バックエンドの書き方の「パッケージの大きさ」の表の行");

            // 操作と確認：本番のクラスの数と比べて、食い違いがない
            assertThat(oversizedMismatches(classCountByPackage(ProductionClasses.all()), table)).isEmpty();
        }

        @Test
        @DisplayName("表の外のパッケージが目安を超えると、不一致になる")
        void mismatchWhenPackageOutsideTableExceedsGuideline() {
            // 準備：表にないパッケージに、目安を 1 つ超えるクラス
            Map<String, Long> counted = Map.of("module.order.application", 11L);

            // 操作と確認：表にない超過として示す
            assertThat(oversizedMismatches(counted, Set.of())).containsExactly("表にない超過: order.application（11）");
        }

        @Test
        @DisplayName("表のパッケージが目安以下になると、不一致になる")
        void mismatchWhenPackageInTableIsWithinGuideline() {
            // 準備：表にあるパッケージに、目安と同じ数のクラス
            Map<String, Long> counted = Map.of("module.order.application", 10L);

            // 操作と確認：目安以下として示す
            assertThat(oversizedMismatches(counted, Set.of("order.application")))
                    .containsExactly("目安以下: order.application（実際 10）");
        }
    }

    @Nested
    @DisplayName("表の読み取り")
    class TableParsing {

        @Test
        @DisplayName("見出しの後の最初の表の、パッケージの行だけを読む")
        void tableIsReadFromFirstTableAfterHeading() {
            // 準備：見出しの前の表、見出しの後の表、その後の本文と 2 つ目の表、次の見出しの表
            List<String> lines = List.of(
                    "| 別の表 | 分けない理由 |", "|---|---|", "| `x.y` | 理由 |",
                    "### 1.4 パッケージの大きさ", "本文",
                    "| パッケージ | 分けない理由 |", "|---|---|",
                    "| `catalog.infrastructure.persistence` | 理由 |", "| `config.security` | 理由 |",
                    "", "表の後の本文",
                    "| 2 つ目の表 | 理由 |", "|---|---|", "| `w.w` | 理由 |",
                    "### 1.5 共通の型", "| `z.z` | 理由 |");

            // 操作と確認：見出しの後の最初の表のパッケージだけ
            assertThat(oversizedTable(lines)).containsExactlyInAnyOrder("catalog.infrastructure.persistence", "config.security");
        }

        @Test
        @DisplayName("見出しの番号が違っても、見出しの名前で表を探す")
        void tableIsFoundByHeadingNameRegardlessOfNumber() {
            // 準備：番号の違う見出しの後の表
            List<String> lines = List.of(
                    "### 2.2 共通の型", "| `z.z` | 理由 |",
                    "### 2.3 パッケージの大きさ", "| パッケージ | 分けない理由 |", "|---|---|", "| `config.security` | 理由 |");

            // 操作と確認：その表のパッケージを読む
            assertThat(oversizedTable(lines)).containsExactly("config.security");
        }

        @Test
        @DisplayName("パッケージと分けない理由の 2 列の表を読める")
        void twoColumnTableIsRead() {
            // 準備：見出しの後に空行と 2 列の表
            List<String> lines = List.of(
                    "### 1.4 パッケージの大きさ", "",
                    "| パッケージ | 分けない理由 |", "|------------|--------------|",
                    "| `catalog.infrastructure.persistence` | jOOQ の Repository |");

            // 操作と確認：表のパッケージを読む
            assertThat(oversizedTable(lines)).containsExactly("catalog.infrastructure.persistence");
        }
    }

    // ============================== 補助 ==============================

    /**
     * 手で書いたパッケージごとに、入れ子でないクラスの数を返す（package-info は数えない）。
     *
     * @param classes 調べるクラス
     * @return パッケージの名前から、クラスの数への対応（名前の順）
     */
    private static Map<String, Long> classCountByPackage(JavaClasses classes) {
        return classes.stream()
                .filter(javaClass -> !ProductionClasses.isGenerated(javaClass.getPackageName()))
                .filter(JavaClass::isTopLevelClass)
                .filter(javaClass -> !javaClass.getSimpleName().equals("package-info"))
                .collect(Collectors.groupingBy(JavaClass::getPackageName, TreeMap::new, Collectors.counting()));
    }

    /**
     * 目安を超えるパッケージの、数えた結果と表の食い違いを返す。
     *
     * <p>数えた結果のパッケージの名前は、{@code com.ecsite.} と、モジュールの {@code module.} を除いて表と比べる
     * （表はモジュールのパッケージを {@code <モジュール>.<層>} と書く）。
     *
     * @param countByPackage パッケージの名前（{@code com.ecsite.} を除いた形でも、含む形でもよい）から、クラスの数への対応
     * @param table          表のパッケージの名前
     * @return 食い違いの説明（説明の順）。表にない超過、表のパッケージの目安以下
     */
    private static List<String> oversizedMismatches(Map<String, Long> countByPackage, Set<String> table) {
        Map<String, Long> counted = countByPackage.entrySet().stream()
                .collect(Collectors.toMap(entry -> tableName(entry.getKey()), Map.Entry::getValue));
        Set<String> names = new TreeSet<>(table);
        counted.forEach((name, count) -> {
            if (count > GUIDELINE_CLASSES_PER_PACKAGE) {
                names.add(name);
            }
        });

        List<String> mismatches = new ArrayList<>();
        for (String name : names) {
            long actual = counted.getOrDefault(name, 0L);
            if (!table.contains(name)) {
                mismatches.add("表にない超過: %s（%d）".formatted(name, actual));
            } else if (actual <= GUIDELINE_CLASSES_PER_PACKAGE) {
                mismatches.add("目安以下: %s（実際 %d）".formatted(name, actual));
            }
        }
        return mismatches;
    }

    /**
     * パッケージの名前を、表に書く形にする。
     *
     * @param packageName パッケージの名前
     * @return {@code com.ecsite.} と、モジュールの {@code module.} を除いた名前
     */
    private static String tableName(String packageName) {
        String name = packageName.startsWith("com.ecsite.") ? packageName.substring("com.ecsite.".length()) : packageName;
        return name.startsWith("module.") ? name.substring("module.".length()) : name;
    }

    /**
     * バックエンドの書き方の「パッケージの大きさ」の、目安を超えるパッケージの表を読む。
     *
     * <p>見出しの名前で節を探し、見出しの後の最初の表だけを読む。表の行のうち、1 列目がバッククォートで囲んだパッケージの名前の行を読み、
     * 表の後の行（本文や 2 つ目の表）は読まない。
     *
     * @param lines 文書の行
     * @return 表のパッケージの名前
     */
    private static Set<String> oversizedTable(List<String> lines) {
        Set<String> table = new TreeSet<>();
        boolean afterHeading = false;
        boolean inTable = false;
        for (String line : lines) {
            if (line.startsWith("#")) {
                if (inTable) {
                    break;
                }
                afterHeading = PACKAGE_SIZE_HEADING.matcher(line).matches();
                continue;
            }
            if (!afterHeading) {
                continue;
            }
            if (line.startsWith("|")) {
                inTable = true;
                Matcher row = TABLE_ROW.matcher(line);
                if (row.matches()) {
                    table.add(row.group(1));
                }
            } else if (inTable) {
                break;
            }
        }
        return table;
    }
}

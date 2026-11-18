package com.ecsite.architecture;

import com.ecsite.architecture.sample.testcode.PassesEventPublications;
import com.ecsite.architecture.sample.testcode.ReadsEventPublications;
import com.ecsite.config.event.ReadsEventPublicationRecords;
import com.ecsite.support.ProductionClasses;
import com.ecsite.support.TestCodeClasses;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * イベントの発行の記録を読む場所の検査
 *
 * <p>根拠はバックエンドのテストの書き方の「テストデータは補助のクラスで作る」。
 * 発行の記録（{@link #RECORD_TYPES} の型）を読む操作（名前が find か count で始まる操作）は、非同期のイベントの補助だけが呼ぶ。
 * テストは、記録を読んで待たずに、補助の待つ操作を呼ぶ。補助を作るために記録を注入して渡すだけのテストは当たらない。
 */
class EventPublicationReadTest {

    // ============================== 定数 ==============================

    /**
     * 記録を読んでよい、非同期のイベントの補助（統合テストのクラス）
     */
    private static final Set<String> HELPERS = Set.of(
            "com.ecsite.support.async.AsyncEvents",
            "com.ecsite.config.event.EventPublications");

    /**
     * 記録を読む操作を持つ型の名前。EventPublicationRecords は package-private なので、型でなく名前で指す。
     * 実装の型は、isAssignableTo でこの一覧の型として扱う。
     */
    private static final List<String> RECORD_TYPES = List.of(
            "org.springframework.modulith.events.core.EventPublicationRegistry",
            "org.springframework.modulith.events.core.EventPublicationRepository",
            "org.springframework.modulith.events.CompletedEventPublications",
            "com.ecsite.config.event.EventPublicationRecords");

    // ============================== テスト ==============================

    @Test
    @DisplayName("補助の外のテストのクラスは、イベントの発行の記録を読まない")
    void onlyHelpersReadEventPublications() {
        // 準備：補助のクラスがある（名前を変えたときに、規則が誰も許さない状態にしない）
        assertThat(TestCodeClasses.all().stream().map(JavaClass::getName)).containsAll(HELPERS);

        // 操作と確認：補助の外に、記録を読むクラスがない
        assertThat(violations(TestCodeClasses.all())).isEmpty();
    }

    @Test
    @DisplayName("テストのクラスの一覧は、本番のパッケージに置いた違反の例を含まない")
    void testCodeClassesExcludeExampleInProductionPackage() {
        // 操作と確認：本番のパッケージに置いた違反の例が、一覧にない（他のテストの検査が読まない）
        assertThat(TestCodeClasses.all().stream().map(JavaClass::getName))
                .doesNotContain(ReadsEventPublicationRecords.class.getName());
    }

    @Test
    @DisplayName("規則は、RECORD_TYPES の型の find と count の呼び出しとメソッド参照に当たり、他の操作と型には当たらない")
    void ruleMatchesOnlyReads() {
        // 準備：記録を読む例（入れ子のクラス、ラムダ、private の補助のメソッド、実装の型を含む）と、読まない例
        var examples = new ClassFileImporter().importClasses(
                ReadsEventPublications.class, ReadsEventPublications.Inner.class, ReadsEventPublicationRecords.class,
                PassesEventPublications.class);
        var reads = ReadsEventPublications.class.getName();

        // 操作と確認：読む例だけが違反になり、渡すだけ、find と count 以外の名前、RECORD_TYPES にない型の例は違反にならない
        assertThat(violations(examples)).containsExactlyInAnyOrder(
                reads + " -> findIncompletePublicationsOlderThan",
                reads + " -> findIncompletePublications",
                reads + " -> findFailedPublications",
                reads + " -> findAll",
                reads + " -> countByStatus",
                reads + " -> findByStatus",
                reads + " -> findCompletedPublications",
                reads + " -> findIncompletePublicationsPublishedBefore",
                ReadsEventPublicationRecords.class.getName() + " -> countIncomplete");
    }

    // ============================== 補助 ==============================

    /**
     * 補助の外で、記録を読む呼び出しとメソッド参照を返す。
     *
     * @param classes 調べるクラス
     * @return 呼び出す側の最上位のクラスの名前と、読む操作の名前（名前の順）
     */
    private static List<String> violations(JavaClasses classes) {
        return classes.stream()
                .filter(javaClass -> !HELPERS.contains(ProductionClasses.outermostName(javaClass)))
                .flatMap(javaClass -> Stream.concat(
                        javaClass.getMethodCallsFromSelf().stream(), javaClass.getMethodReferencesFromSelf().stream()))
                .filter(EventPublicationReadTest::readsRegistry)
                .map(access -> ProductionClasses.outermostName(access.getOriginOwner()) + " -> " + access.getName())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 記録を読む操作（名前が find か count で始まる操作）へのアクセスかを返す。
     *
     * @param access 呼び出しかメソッド参照
     * @return RECORD_TYPES の型（その実装を含む）の find か count で始まる操作なら true
     */
    private static boolean readsRegistry(JavaAccess<?> access) {
        return (access.getName().startsWith("find") || access.getName().startsWith("count"))
                && RECORD_TYPES.stream().anyMatch(access.getTargetOwner()::isAssignableTo);
    }
}

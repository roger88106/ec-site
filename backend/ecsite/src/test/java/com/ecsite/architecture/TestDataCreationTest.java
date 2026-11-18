package com.ecsite.architecture;

import com.ecsite.architecture.sample.testcode.CreatesTestData;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.support.ProductionClasses;
import com.ecsite.support.TestCodeClasses;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * テストデータを作る操作を呼ぶ場所の検査
 *
 * <p>根拠はバックエンドのテストの書き方の「テストデータは補助のクラスで作る」。
 * 対象は、在庫の作成（在庫のモジュールの {@code createIfAbsent}）と支払の作成（{@link PaymentApi} の {@code create}）。
 * 呼ぶのは、補助のクラスと、その操作そのものを確認するテストだけである。呼び出しとメソッド参照を、クラスファイルから型で探す。
 */
class TestDataCreationTest {

    // ============================== 定数 ==============================

    /**
     * 操作ごとの、呼んでよいクラス（補助のクラスと、その操作そのものを確認するテスト。統合テストのクラス）
     */
    private static final Map<Rule, Set<String>> ALLOWED = Map.of(
            Rule.STOCK, Set.of(
                    "com.ecsite.support.data.InventoryTestData",
                    "com.ecsite.module.inventory.application.handler.CatalogEventHandlerTest",
                    "com.ecsite.module.inventory.infrastructure.persistence.JooqStockItemRepositoryTest"),
            Rule.PAYMENT, Set.of(
                    "com.ecsite.support.data.PaymentTestData",
                    "com.ecsite.module.payment.api.PaymentApiTest"));

    // ============================== テスト ==============================

    @Test
    @DisplayName("補助の外のテストのクラスは、在庫と支払を作る操作を呼ばない")
    void onlyHelpersCreateTestData() {
        // 準備：呼んでよいクラスがある（名前を変えたときに、規則が誰も許さない状態にしない）
        assertThat(TestCodeClasses.all().stream().map(JavaClass::getName))
                .containsAll(ALLOWED.values().stream().flatMap(Set::stream).toList());

        // 操作と確認：呼んでよいクラスの外に、作る操作を呼ぶクラスがない
        assertThat(violations(TestCodeClasses.all())).isEmpty();
    }

    @Test
    @DisplayName("規則は、var の変数からの呼び出しとメソッド参照に当たる")
    void ruleMatchesCallsAndReferences() {
        // 準備：補助の外で作る例
        var examples = new ClassFileImporter().importClasses(CreatesTestData.class);

        // 操作と確認：在庫の作成の呼び出しとメソッド参照、支払の作成の呼び出しが違反になる
        assertThat(violations(examples)).containsExactly(
                CreatesTestData.class.getName() + " -> " + PaymentApi.class.getSimpleName() + ".create",
                CreatesTestData.class.getName() + " -> " + StockItemRepository.class.getSimpleName() + ".createIfAbsent");
    }

    // ============================== 補助 ==============================

    /**
     * 呼んでよいクラスの外で、作る操作を呼ぶ呼び出しとメソッド参照を返す。
     *
     * @param classes 調べるクラス
     * @return 呼び出す側の最上位のクラスの名前と、呼ぶ操作（名前の順）
     */
    private static List<String> violations(JavaClasses classes) {
        return classes.stream()
                .flatMap(javaClass -> Stream.concat(
                        javaClass.getMethodCallsFromSelf().stream(), javaClass.getMethodReferencesFromSelf().stream()))
                .flatMap(access -> Stream.of(Rule.values())
                        .filter(rule -> rule.matches(access))
                        .filter(rule -> !ALLOWED.get(rule).contains(ProductionClasses.outermostName(access.getOriginOwner())))
                        .map(rule -> ProductionClasses.outermostName(access.getOriginOwner()) + " -> "
                                + access.getTargetOwner().getSimpleName() + "." + access.getName()))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 補助だけが呼ぶ操作
     */
    private enum Rule {

        /**
         * 在庫の作成（在庫のモジュールの createIfAbsent。Repository、その実装、アプリケーションのサービス）
         */
        STOCK("createIfAbsent", owner -> owner.getPackageName().startsWith("com.ecsite.module.inventory.")),

        /**
         * 支払の作成（支払の API の create）
         */
        PAYMENT("create", owner -> owner.isAssignableTo(PaymentApi.class));

        /**
         * 操作の名前
         */
        private final String method;

        /**
         * 操作を持つ型の条件
         */
        private final Predicate<JavaClass> owner;

        /**
         * 規則を作る。
         *
         * @param method 操作の名前
         * @param owner  操作を持つ型の条件
         */
        Rule(String method, Predicate<JavaClass> owner) {
            this.method = method;
            this.owner = owner;
        }

        /**
         * アクセスが、この操作の呼び出しかメソッド参照かを返す。
         *
         * @param access 呼び出しかメソッド参照
         * @return 名前が一致し、操作を持つ型が条件に合えば true
         */
        boolean matches(JavaAccess<?> access) {
            return access.getName().equals(method) && owner.test(access.getTargetOwner());
        }
    }
}

package com.ecsite.architecture;

import com.ecsite.architecture.sample.idgeneration.CallsRandomUuid;
import com.ecsite.architecture.sample.idgeneration.CreatesRandom;
import com.ecsite.architecture.sample.idgeneration.CreatesSecureRandom;
import com.ecsite.architecture.sample.idgeneration.ExampleIdGenerator;
import com.ecsite.architecture.sample.idgeneration.ExampleRandomConfig;
import com.ecsite.architecture.sample.idgeneration.InjectsIdGeneration;
import com.ecsite.support.ProductionClasses;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 識別子と乱数の作り方の検査
 *
 * <p>根拠はバックエンドの書き方の「識別子と乱数は注入する」。次の 2 つを確認する。
 * <ul>
 *   <li>{@code UUID.randomUUID()} を呼ぶのは、識別子の生成の実装 {@code RandomIdGenerator} と、イベント ID を作る {@code DomainEvent} だけ</li>
 *   <li>{@code new SecureRandom()}・{@code new Random()} を書くのは、乱数の Bean を作る {@code RandomConfig} だけ</li>
 * </ul>
 */
class IdGenerationTest {

    // ============================== 定数 ==============================

    /**
     * 識別子の生成の実装
     */
    private static final String ID_GENERATOR = "com.ecsite.common.id.RandomIdGenerator";

    /**
     * イベント ID を作る静的メソッドを持つクラス
     */
    private static final String DOMAIN_EVENT = "com.ecsite.common.model.base.DomainEvent";

    /**
     * 乱数の Bean の設定のクラス
     */
    private static final String RANDOM_CONFIG = "com.ecsite.config.RandomConfig";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("本番のクラス")
    class ProductionCode {

        @Test
        @DisplayName("本番のクラスは、識別子の生成の実装とイベント ID の作成の外で、UUID.randomUUID() を呼ばない")
        void productionClassesDoNotCallRandomUuid() {
            // 操作と確認：本番の手書きのクラスに規則を当てる
            uuidRule(ID_GENERATOR, DOMAIN_EVENT).check(ProductionClasses.handwritten());
        }

        @Test
        @DisplayName("本番のクラスは、乱数の Bean の設定の外で、乱数を直接作らない")
        void productionClassesDoNotCreateRandom() {
            // 操作と確認：本番の手書きのクラスに規則を当てる
            randomRule(RANDOM_CONFIG).check(ProductionClasses.handwritten());
        }
    }

    @Nested
    @DisplayName("識別子の規則")
    class UuidRule {

        @Test
        @DisplayName("規則は、UUID.randomUUID() を呼ぶ例に当たる")
        void ruleMatchesRandomUuid() {
            // 準備：UUID.randomUUID() を呼ぶ例
            JavaClasses examples = new ClassFileImporter().importClasses(CallsRandomUuid.class);

            // 操作と確認：例外の一覧にないので違反になる
            assertThat(uuidRule(ID_GENERATOR, DOMAIN_EVENT).evaluate(examples).hasViolation()).isTrue();
        }

        @Test
        @DisplayName("規則は、例外の一覧のクラスには当たらない")
        void ruleDoesNotMatchListedClass() {
            // 準備：識別子の生成の実装に見立てた例（規則の対象を 0 件にしないため、規則に合う例も加える）
            JavaClasses examples = new ClassFileImporter().importClasses(ExampleIdGenerator.class, InjectsIdGeneration.class);

            // 操作と確認：例を例外の一覧に入れた規則では、違反にならない
            assertThat(uuidRule(ExampleIdGenerator.class.getName(), DOMAIN_EVENT).evaluate(examples).hasViolation()).isFalse();
        }

        @Test
        @DisplayName("規則は、注入した識別子の生成を使うクラスには当たらない")
        void ruleDoesNotMatchInjectedIds() {
            // 準備：注入して使う例
            JavaClasses examples = new ClassFileImporter().importClasses(InjectsIdGeneration.class);

            // 操作と確認：違反にならない
            assertThat(uuidRule(ID_GENERATOR, DOMAIN_EVENT).evaluate(examples).hasViolation()).isFalse();
        }
    }

    @Nested
    @DisplayName("乱数の規則")
    class RandomRule {

        @Test
        @DisplayName("規則は、new SecureRandom() を書く例に当たる")
        void ruleMatchesSecureRandom() {
            // 準備：new SecureRandom() を書く例
            JavaClasses examples = new ClassFileImporter().importClasses(CreatesSecureRandom.class);

            // 操作と確認：違反になる
            assertThat(randomRule(RANDOM_CONFIG).evaluate(examples).hasViolation()).isTrue();
        }

        @Test
        @DisplayName("規則は、new Random() を書く例に当たる")
        void ruleMatchesRandom() {
            // 準備：new Random() を書く例
            JavaClasses examples = new ClassFileImporter().importClasses(CreatesRandom.class);

            // 操作と確認：違反になる
            assertThat(randomRule(RANDOM_CONFIG).evaluate(examples).hasViolation()).isTrue();
        }

        @Test
        @DisplayName("規則は、例外の一覧のクラスには当たらない")
        void ruleDoesNotMatchListedClass() {
            // 準備：乱数の Bean の設定に見立てた例（規則の対象を 0 件にしないため、規則に合う例も加える）
            JavaClasses examples = new ClassFileImporter().importClasses(ExampleRandomConfig.class, InjectsIdGeneration.class);

            // 操作と確認：例を例外の一覧に入れた規則では、違反にならない
            assertThat(randomRule(ExampleRandomConfig.class.getName()).evaluate(examples).hasViolation()).isFalse();
        }

        @Test
        @DisplayName("規則は、乱数を受け取るクラスには当たらない")
        void ruleDoesNotMatchInjectedRandom() {
            // 準備：注入して使う例
            JavaClasses examples = new ClassFileImporter().importClasses(InjectsIdGeneration.class);

            // 操作と確認：違反にならない
            assertThat(randomRule(RANDOM_CONFIG).evaluate(examples).hasViolation()).isFalse();
        }
    }

    // ============================== 規則の補助 ==============================

    /**
     * 例外の一覧のクラスの外で、UUID.randomUUID() を呼ばない規則を作る。
     *
     * @param generator 呼んでよい、識別子の生成の実装のクラスの名前
     * @param event     呼んでよい、イベント ID を作るクラスの名前
     * @return 規則
     */
    private static ArchRule uuidRule(String generator, String event) {
        return noClasses().that().doNotHaveFullyQualifiedName(generator).and().doNotHaveFullyQualifiedName(event)
                .should().callMethod(UUID.class, "randomUUID")
                .because("識別子は注入した IdGenerator で作る（バックエンドの書き方の「識別子と乱数は注入する」）");
    }

    /**
     * 例外の一覧のクラスの外で、乱数を直接作らない規則を作る。
     *
     * @param randomConfig 乱数を作ってよいクラスの名前
     * @return 規則
     */
    private static ArchRule randomRule(String randomConfig) {
        return noClasses().that().doNotHaveFullyQualifiedName(randomConfig)
                .should().callConstructor(SecureRandom.class)
                .orShould().callConstructor(SecureRandom.class, byte[].class)
                .orShould().callConstructor(Random.class)
                .orShould().callConstructor(Random.class, long.class)
                .because("乱数は、注入した RandomGenerator から使う（バックエンドの書き方の「識別子と乱数は注入する」）");
    }
}

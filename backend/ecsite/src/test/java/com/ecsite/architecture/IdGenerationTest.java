package com.ecsite.architecture;

import com.ecsite.architecture.sample.idgeneration.CallsRandomUuid;
import com.ecsite.architecture.sample.idgeneration.CallsRandomUuidReference;
import com.ecsite.architecture.sample.idgeneration.CreatesRandom;
import com.ecsite.architecture.sample.idgeneration.CreatesRandomReference;
import com.ecsite.architecture.sample.idgeneration.CreatesRandomWithSeed;
import com.ecsite.architecture.sample.idgeneration.CreatesSecureRandom;
import com.ecsite.architecture.sample.idgeneration.CreatesSecureRandomReference;
import com.ecsite.architecture.sample.idgeneration.CreatesSecureRandomWithSeed;
import com.ecsite.architecture.sample.idgeneration.ExampleEventIdMethod;
import com.ecsite.architecture.sample.idgeneration.ExampleEventIdWithOtherMethod;
import com.ecsite.architecture.sample.idgeneration.ExampleIdGenerator;
import com.ecsite.architecture.sample.idgeneration.ExampleRandomConfig;
import com.ecsite.architecture.sample.idgeneration.InjectsIdGeneration;
import com.ecsite.support.ProductionClasses;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 識別子と乱数の作り方の検査
 *
 * <p>根拠はバックエンドの書き方の「識別子と乱数は注入する」。次の 2 つを確認する。
 * <ul>
 *   <li>{@code UUID.randomUUID()} を呼ぶ（メソッド参照を含む）のは、識別子の生成の実装 {@code RandomIdGenerator} と、イベント ID を作る {@code DomainEvent.newEventId} だけ</li>
 *   <li>{@code SecureRandom}・{@code Random} のコンストラクターを呼ぶ（コンストラクター参照を含む）のは、乱数の Bean を作る {@code RandomConfig} だけ</li>
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
     * イベント ID を作る静的メソッドの名前
     */
    private static final String NEW_EVENT_ID = "newEventId";

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
            uuidRule(ID_GENERATOR, DOMAIN_EVENT, NEW_EVENT_ID).check(ProductionClasses.handwritten());
        }

        @Test
        @DisplayName("本番のクラスは、乱数の Bean の設定の外で、乱数を直接作らない")
        void productionClassesDoNotCreateRandom() {
            // 操作と確認：本番の手書きのクラスに規則を当てる
            randomRule(RANDOM_CONFIG).check(ProductionClasses.handwritten());
        }

        @Test
        @DisplayName("例外の一覧のクラスは、本番のクラスで UUID.randomUUID() を呼んでいる")
        void listedClassesCallRandomUuid() {
            // 準備：例外の一覧を空にした規則
            ArchRule withoutException = uuidRule("", "", "");

            // 操作：本番の手書きのクラスに規則を当てる
            List<String> details = withoutException.evaluate(ProductionClasses.handwritten()).getFailureReport().getDetails();

            // 確認：識別子の生成の実装とイベント ID を作るメソッドが違反になる（例外の一覧が使われている）
            assertThat(details).anyMatch(detail -> detail.contains(ID_GENERATOR));
            assertThat(details).anyMatch(detail -> detail.contains(DOMAIN_EVENT + "." + NEW_EVENT_ID));
        }

        @Test
        @DisplayName("例外の一覧のクラスは、本番のクラスで乱数を作っている")
        void listedClassCreatesRandom() {
            // 準備：例外の一覧を空にした規則
            ArchRule withoutException = randomRule("");

            // 操作：本番の手書きのクラスに規則を当てる
            List<String> details = withoutException.evaluate(ProductionClasses.handwritten()).getFailureReport().getDetails();

            // 確認：乱数の Bean の設定が違反になる（例外の一覧が使われている）
            assertThat(details).anyMatch(detail -> detail.contains(RANDOM_CONFIG));
        }
    }

    @Nested
    @DisplayName("識別子の規則")
    class UuidRule {

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.architecture.IdGenerationTest#uuidExamples")
        @DisplayName("規則は、UUID.randomUUID() を使う例に当たる")
        void ruleMatchesRandomUuid(Class<?> example) {
            // 準備：UUID.randomUUID() を使う例
            JavaClasses examples = new ClassFileImporter().importClasses(example);

            // 操作と確認：例外の一覧にないので違反になる
            assertThat(uuidRule(ID_GENERATOR, DOMAIN_EVENT, NEW_EVENT_ID).evaluate(examples).hasViolation()).isTrue();
        }

        @Test
        @DisplayName("規則は、例外の一覧のクラスには当たらない")
        void ruleDoesNotMatchListedClass() {
            // 準備：識別子の生成の実装に見立てた例（規則の対象を 0 件にしないため、規則に合う例も加える）
            JavaClasses examples = new ClassFileImporter().importClasses(ExampleIdGenerator.class, InjectsIdGeneration.class);

            // 操作と確認：例を例外の一覧に入れた規則では、違反にならない
            assertThat(uuidRule(ExampleIdGenerator.class.getName(), DOMAIN_EVENT, NEW_EVENT_ID).evaluate(examples).hasViolation()).isFalse();
        }

        @Test
        @DisplayName("規則は、例外のメソッドには当たらない")
        void ruleDoesNotMatchListedMethod() {
            // 準備：イベント ID を作る静的メソッドだけを持つ例（規則の対象を 0 件にしないため、規則に合う例も加える）
            JavaClasses examples = new ClassFileImporter().importClasses(ExampleEventIdMethod.class, InjectsIdGeneration.class);

            // 操作と確認：例を例外の一覧に入れた規則では、違反にならない
            assertThat(uuidRule(ID_GENERATOR, ExampleEventIdMethod.class.getName(), NEW_EVENT_ID).evaluate(examples).hasViolation()).isFalse();
        }

        @Test
        @DisplayName("規則は、例外のクラスの、例外のメソッドの外の呼び出しには当たる")
        void ruleMatchesOtherMethodOfListedClass() {
            // 準備：イベント ID を作る静的メソッドのほかに、別のメソッドも呼ぶ例
            JavaClasses examples = new ClassFileImporter().importClasses(ExampleEventIdWithOtherMethod.class);

            // 操作：クラスを例外の一覧に入れた規則を当てる
            ArchRule rule = uuidRule(ID_GENERATOR, ExampleEventIdWithOtherMethod.class.getName(), NEW_EVENT_ID);
            List<String> details = rule.evaluate(examples).getFailureReport().getDetails();

            // 確認：別のメソッドだけが違反になる
            assertThat(details).hasSize(1).allMatch(detail -> detail.contains("other()"));
        }

        @Test
        @DisplayName("規則は、注入した識別子の生成を使うクラスには当たらない")
        void ruleDoesNotMatchInjectedIds() {
            // 準備：注入して使う例
            JavaClasses examples = new ClassFileImporter().importClasses(InjectsIdGeneration.class);

            // 操作と確認：違反にならない
            assertThat(uuidRule(ID_GENERATOR, DOMAIN_EVENT, NEW_EVENT_ID).evaluate(examples).hasViolation()).isFalse();
        }
    }

    @Nested
    @DisplayName("乱数の規則")
    class RandomRule {

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.architecture.IdGenerationTest#randomExamples")
        @DisplayName("規則は、乱数を直接作る例に当たる")
        void ruleMatchesRandom(Class<?> example) {
            // 準備：乱数を直接作る例
            JavaClasses examples = new ClassFileImporter().importClasses(example);

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

    // ============================== 準備の補助 ==============================

    /**
     * UUID.randomUUID() を使う例を返す。
     *
     * @return 例のクラス（使い方の名前を付ける）
     */
    static Stream<Named<Class<?>>> uuidExamples() {
        return Stream.of(
                Named.of("UUID.randomUUID()", CallsRandomUuid.class),
                Named.of("UUID::randomUUID", CallsRandomUuidReference.class));
    }

    /**
     * 乱数を直接作る例を返す。
     *
     * @return 例のクラス（作り方の名前を付ける）
     */
    static Stream<Named<Class<?>>> randomExamples() {
        return Stream.of(
                Named.of("new SecureRandom()", CreatesSecureRandom.class),
                Named.of("new SecureRandom(byte[])", CreatesSecureRandomWithSeed.class),
                Named.of("SecureRandom::new", CreatesSecureRandomReference.class),
                Named.of("new Random()", CreatesRandom.class),
                Named.of("new Random(long)", CreatesRandomWithSeed.class),
                Named.of("Random::new", CreatesRandomReference.class));
    }

    // ============================== 規則の補助 ==============================

    /**
     * 例外の一覧の外で、UUID.randomUUID() を呼ぶ（メソッド参照を含む）ことを禁じる規則を作る。
     *
     * @param generator   呼んでよい、識別子の生成の実装のクラスの名前
     * @param eventClass  呼んでよい静的メソッドを持つ、イベントのクラスの名前
     * @param eventMethod 呼んでよい、イベント ID を作る静的メソッドの名前
     * @return 規則
     */
    private static ArchRule uuidRule(String generator, String eventClass, String eventMethod) {
        Predicate<JavaAccess<?>> listed = access -> access.getOriginOwner().getName().equals(generator)
                || (access.getOriginOwner().getName().equals(eventClass) && access.getOrigin().getName().equals(eventMethod));
        return noClasses()
                .should(usesAccess("UUID.randomUUID() を呼ぶ", IdGenerationTest::isRandomUuid, listed))
                .because("識別子は注入した IdGenerator で作る（バックエンドの書き方の「識別子と乱数は注入する」）");
    }

    /**
     * 例外の一覧の外で、乱数を直接作る（コンストラクター参照を含む）ことを禁じる規則を作る。
     *
     * @param randomConfig 乱数を作ってよいクラスの名前
     * @return 規則
     */
    private static ArchRule randomRule(String randomConfig) {
        Predicate<JavaAccess<?>> listed = access -> access.getOriginOwner().getName().equals(randomConfig);
        return noClasses()
                .should(usesAccess("SecureRandom・Random のコンストラクターを呼ぶ", IdGenerationTest::isRandomConstructor, listed))
                .because("乱数は、注入した RandomGenerator から使う（バックエンドの書き方の「識別子と乱数は注入する」）");
    }

    /**
     * 呼び出しとメソッド参照・コンストラクター参照の両方から、対象へのアクセスを見つける条件を作る。
     *
     * <p>メソッド参照は、ArchUnit では呼び出しと別に扱われるので、両方を見る。
     *
     * @param description 条件の説明
     * @param target      対象のアクセスか
     * @param listed      例外の一覧のアクセスか（対象から除く）
     * @return 例外の一覧の外に、対象のアクセスがあれば満たす条件
     */
    private static ArchCondition<JavaClass> usesAccess(String description, Predicate<JavaAccess<?>> target, Predicate<JavaAccess<?>> listed) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Stream.of(javaClass.getMethodCallsFromSelf(), javaClass.getMethodReferencesFromSelf(),
                                javaClass.getConstructorCallsFromSelf(), javaClass.getConstructorReferencesFromSelf())
                        .flatMap(accesses -> accesses.stream().map(access -> (JavaAccess<?>) access))
                        .filter(target)
                        .filter(listed.negate())
                        .forEach(access -> events.add(SimpleConditionEvent.satisfied(access, access.getDescription())));
            }
        };
    }

    /**
     * UUID.randomUUID() へのアクセスかを返す。
     *
     * @param access 呼び出しかメソッド参照
     * @return 呼ぶ先が {@link UUID#randomUUID()} なら true
     */
    private static boolean isRandomUuid(JavaAccess<?> access) {
        return access.getTargetOwner().isEquivalentTo(UUID.class) && access.getName().equals("randomUUID");
    }

    /**
     * SecureRandom か Random のコンストラクターへのアクセスかを返す。
     *
     * @param access 呼び出しかコンストラクター参照
     * @return 呼ぶ先が {@link SecureRandom} か {@link Random} のコンストラクターなら true
     */
    private static boolean isRandomConstructor(JavaAccess<?> access) {
        return access.getName().equals("<init>")
                && (access.getTargetOwner().isEquivalentTo(SecureRandom.class) || access.getTargetOwner().isEquivalentTo(Random.class));
    }
}

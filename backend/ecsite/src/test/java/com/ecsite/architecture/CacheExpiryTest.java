package com.ecsite.architecture;

import com.ecsite.architecture.sample.caffeine.BuildsAsyncWithoutExpiry;
import com.ecsite.architecture.sample.caffeine.BuildsFromSpec;
import com.ecsite.architecture.sample.caffeine.BuildsInLambdaWithoutExpiry;
import com.ecsite.architecture.sample.caffeine.BuildsTwoCachesWithOneExpiry;
import com.ecsite.architecture.sample.caffeine.BuildsTwoFieldCachesWithOneExpiry;
import com.ecsite.architecture.sample.caffeine.BuildsWithCustomExpiry;
import com.ecsite.architecture.sample.caffeine.BuildsWithExpireAfterAccess;
import com.ecsite.architecture.sample.caffeine.BuildsWithExpireAfterWrite;
import com.ecsite.architecture.sample.caffeine.BuildsWithExpiryInOtherMethod;
import com.ecsite.architecture.sample.caffeine.BuildsWithRefreshOnly;
import com.ecsite.architecture.sample.caffeine.BuildsWithoutExpiry;
import com.ecsite.support.ProductionClasses;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ローカルキャッシュの期限の検査
 *
 * <p>根拠はバックエンドの書き方の「ローカルキャッシュには期限を付ける」。
 * Caffeine のキャッシュを作るときに期限を指定していることを、次の 2 つで確認する。
 * <ul>
 *   <li>{@code Caffeine.newBuilder()} を呼ぶメソッド（コンストラクターと static の初期化子を含む）は、同じメソッドで期限を指定する。
 *       指定の回数は、{@code newBuilder()} を呼ぶ回数以上にする。
 *       期限の指定は {@code expireAfterWrite}、{@code expireAfterAccess}、{@code expireAfter}（引数の型を問わない）。
 *       {@code refreshAfterWrite} は要素を削除しないので、期限の指定にしない</li>
 *   <li>設定の文字列から作る {@code Caffeine.from} を呼ばない（期限の有無を検査できないため）</li>
 * </ul>
 * メソッドの単位で回数を比べるのは、同じクラスや同じメソッドの別のキャッシュに期限を指定してあっても、
 * 期限のないキャッシュを見逃さないため。項目の初期化子は、コンストラクターの中に入るので、同じメソッドとして数える。
 */
class CacheExpiryTest {

    // ============================== 定数 ==============================

    /**
     * キャッシュのビルダーを作る、Caffeine のメソッドの名前
     */
    private static final Set<String> NEW_BUILDER_METHODS = Set.of("newBuilder");

    /**
     * 期限の指定として認める、Caffeine のビルダーのメソッドの名前
     */
    private static final Set<String> EXPIRY_METHODS = Set.of("expireAfterWrite", "expireAfterAccess", "expireAfter");

    /**
     * 理由（規則の違反の報告に表示する）
     */
    private static final String REASON = "ローカルキャッシュには期限を付ける（バックエンドの書き方の「ローカルキャッシュには期限を付ける」）";

    /**
     * {@code Caffeine.newBuilder()} を呼ぶメソッド
     */
    private static final DescribedPredicate<JavaCodeUnit> CALLS_NEW_BUILDER = DescribedPredicate.describe(
            "call Caffeine.newBuilder()",
            codeUnit -> countCaffeineCalls(codeUnit, NEW_BUILDER_METHODS) > 0);

    /**
     * 同じメソッドで、作るキャッシュの数以上の回数、期限を指定する条件
     */
    private static final ArchCondition<JavaCodeUnit> SPECIFY_EXPIRY = new ArchCondition<>("期限を指定する") {
        @Override
        public void check(JavaCodeUnit codeUnit, ConditionEvents events) {
            long builders = countCaffeineCalls(codeUnit, NEW_BUILDER_METHODS);
            long expiries = countCaffeineCalls(codeUnit, EXPIRY_METHODS);
            events.add(new SimpleConditionEvent(codeUnit, expiries >= builders,
                    "%s は Caffeine のキャッシュを %d 個作るが、期限（%s）の指定は %d 回"
                            .formatted(codeUnit.getFullName(), builders,
                                    String.join("・", EXPIRY_METHODS.stream().sorted().toList()), expiries)));
        }
    };

    /**
     * 設定の文字列から作る {@code Caffeine.from}（引数の型を問わない）の呼び出し
     */
    private static final DescribedPredicate<JavaMethodCall> CAFFEINE_FROM = DescribedPredicate.describe(
            "Caffeine.from",
            call -> call.getName().equals("from") && call.getTargetOwner().isEquivalentTo(Caffeine.class));

    /**
     * 期限を指定してキャッシュを作る規則
     *
     * <p>{@code Caffeine.newBuilder()} を呼ぶメソッドがないクラスの集まりも検査するので、
     * 検査の対象が 1 つもなくても失敗にしない。本番のクラスで対象があることは、テストの準備で確認する。
     */
    private static final ArchRule CACHE_EXPIRY = CompositeArchRule
            .of(codeUnits().that(CALLS_NEW_BUILDER).should(SPECIFY_EXPIRY).allowEmptyShould(true).because(REASON))
            .and(noClasses().should().callMethodWhere(CAFFEINE_FROM).because(REASON));

    // ============================== テスト ==============================

    @Nested
    @DisplayName("本番のクラス")
    class ProductionCode {

        @Test
        @DisplayName("本番のクラスは、期限を指定して Caffeine のキャッシュを作る")
        void productionClassesBuildCachesWithExpiry() {
            // 準備：Caffeine のキャッシュを作るメソッドが 1 件以上ある
            ProductionClasses.requireNotEmpty(cacheBuildingCodeUnits(ProductionClasses.handwritten()),
                    "Caffeine のキャッシュを作るメソッド");

            // 操作と確認：本番の手書きのクラスを規則で検査する
            CACHE_EXPIRY.check(ProductionClasses.handwritten());
        }
    }

    @Nested
    @DisplayName("期限の規則")
    class ExpiryRule {

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.architecture.CacheExpiryTest#withoutExpiryExamples")
        @DisplayName("規則は、期限を指定せずにキャッシュを作る例を違反とする")
        void ruleRejectsCacheWithoutExpiry(Class<?> example) {
            // 準備：期限を指定せずにキャッシュを作る例
            JavaClasses examples = new ClassFileImporter().importClasses(example);

            // 操作と確認：違反になる
            assertThat(CACHE_EXPIRY.evaluate(examples).hasViolation()).isTrue();
        }

        @Test
        @DisplayName("規則は、同じクラスの別のメソッドで期限を指定していても、期限を指定しないキャッシュを見逃さない")
        void ruleChecksEachMethod() {
            // 準備：期限を指定するメソッドと指定しないメソッドを持つ例
            JavaClasses examples = new ClassFileImporter().importClasses(BuildsWithExpiryInOtherMethod.class);

            // 操作：規則で検査する
            List<String> details = CACHE_EXPIRY.evaluate(examples).getFailureReport().getDetails();

            // 確認：期限を指定しないメソッドだけが違反になる
            assertThat(details)
                    .anyMatch(detail -> detail.contains("buildWithoutExpiry"))
                    .noneMatch(detail -> detail.contains("buildWithExpiry()"));
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.architecture.CacheExpiryTest#withExpiryExamples")
        @DisplayName("規則は、期限を指定してキャッシュを作る例を違反としない")
        void ruleAcceptsCacheWithExpiry(Class<?> example) {
            // 準備：期限を指定してキャッシュを作る例
            JavaClasses examples = new ClassFileImporter().importClasses(example);

            // 操作と確認：違反にならない
            assertThat(CACHE_EXPIRY.evaluate(examples).hasViolation()).isFalse();
        }
    }

    // ============================== 規則の補助 ==============================

    /**
     * メソッドが、Caffeine のメソッドのうち指定した名前のものを呼ぶ回数を返す。
     *
     * @param codeUnit メソッド（コンストラクターと static の初期化子を含む）
     * @param names    メソッドの名前
     * @return 呼ぶ回数
     */
    private static long countCaffeineCalls(JavaCodeUnit codeUnit, Set<String> names) {
        return codeUnit.getMethodCallsFromSelf().stream()
                .filter(call -> names.contains(call.getName()) && call.getTargetOwner().isEquivalentTo(Caffeine.class))
                .count();
    }

    /**
     * Caffeine のキャッシュを作るメソッドを返す。
     *
     * @param classes 対象のクラス
     * @return {@code Caffeine.newBuilder()} を呼ぶメソッド
     */
    private static List<JavaCodeUnit> cacheBuildingCodeUnits(JavaClasses classes) {
        return classes.stream()
                .flatMap(javaClass -> javaClass.getCodeUnits().stream())
                .filter(CALLS_NEW_BUILDER)
                .toList();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 期限を指定せずにキャッシュを作る例を返す。
     *
     * @return 例のクラス（作り方ごとの名前を付けて返す）
     */
    static Stream<Named<Class<?>>> withoutExpiryExamples() {
        return Stream.of(
                Named.of("期限なし", BuildsWithoutExpiry.class),
                Named.of("refreshAfterWrite だけ", BuildsWithRefreshOnly.class),
                Named.of("buildAsync で期限なし", BuildsAsyncWithoutExpiry.class),
                Named.of("ラムダの中で期限なし", BuildsInLambdaWithoutExpiry.class),
                Named.of("1 つのメソッドで 2 つ作り、片方だけに期限", BuildsTwoCachesWithOneExpiry.class),
                Named.of("項目の初期化子で 2 つ作り、片方だけに期限", BuildsTwoFieldCachesWithOneExpiry.class),
                Named.of("Caffeine.from", BuildsFromSpec.class));
    }

    /**
     * 期限を指定してキャッシュを作る例を返す。
     *
     * @return 例のクラス（期限の指定ごとの名前を付けて返す）
     */
    static Stream<Named<Class<?>>> withExpiryExamples() {
        return Stream.of(
                Named.of("expireAfterWrite", BuildsWithExpireAfterWrite.class),
                Named.of("expireAfterAccess", BuildsWithExpireAfterAccess.class),
                Named.of("expireAfter", BuildsWithCustomExpiry.class));
    }
}

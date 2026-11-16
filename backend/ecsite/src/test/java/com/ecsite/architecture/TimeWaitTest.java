package com.ecsite.architecture;

import com.ecsite.architecture.sample.timewait.TimeWaitExamples;
import com.ecsite.support.TestCodeClasses;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 時間の待ちの検査
 *
 * <p>根拠はテストの書き方の「待ち方」。時間で待つテストは、遅い環境で失敗し、速い環境で時間を無駄にするので、条件で待つ。
 * 単体テストと統合テストのクラスが、次を呼ばないことを確認する。
 * <ul>
 *   <li>{@code Thread.sleep}、{@code TimeUnit} の {@code sleep}</li>
 *   <li>決まった時間だけ見る・待ってから見る Awaitility の {@code during}、{@code pollDelay}</li>
 * </ul>
 * フロントと E2E の待ちは、Biome の plugin（no-time-wait）が見る。
 */
class TimeWaitTest {

    // ============================== 定数 ==============================

    /**
     * Awaitility の待ちの条件を組み立てるクラス（テストの依存なので、名前で指定する）
     */
    private static final String CONDITION_FACTORY = "org.awaitility.core.ConditionFactory";

    /**
     * 決まった時間だけ見る・待ってから見る Awaitility のメソッド
     */
    private static final Set<String> FIXED_TIME_CONDITIONS = Set.of("during", "pollDelay");

    /**
     * 時間で待つ呼び出し
     */
    private static final DescribedPredicate<JavaMethodCall> TIME_WAIT = DescribedPredicate.describe(
            "Thread.sleep, TimeUnit.sleep, ConditionFactory.during or pollDelay",
            call -> isSleep(call) || isFixedTimeCondition(call));

    /**
     * 時間で待たない規則
     */
    private static final ArchRule NO_TIME_WAIT = noClasses()
            .should().callMethodWhere(TIME_WAIT)
            .because("テストは時間でなく条件で待つ（テストの書き方の「待ち方」）");

    // ============================== テスト ==============================

    @Test
    @DisplayName("単体テストと統合テストのクラスは、時間で待たない")
    void testClassesDoNotWaitForFixedTime() {
        // 操作と確認：単体テストと統合テストのクラスに規則を当てる
        NO_TIME_WAIT.check(TestCodeClasses.all());
    }

    @ParameterizedTest(name = "「{0}」")
    @MethodSource("com.ecsite.architecture.TimeWaitTest#timeWaitExamples")
    @DisplayName("規則は、時間で待つ例に当たる")
    void ruleMatchesTimeWait(Class<?> example) {
        // 準備：時間で待つ例
        JavaClasses examples = new ClassFileImporter().importClasses(example);

        // 操作と確認：違反になる
        assertThat(NO_TIME_WAIT.evaluate(examples).hasViolation()).isTrue();
    }

    @Test
    @DisplayName("規則は、条件を満たすまで待つ例に当たらない")
    void ruleDoesNotMatchConditionWait() {
        // 準備：Awaitility で条件を満たすまで待つ例
        JavaClasses examples = new ClassFileImporter().importClasses(TimeWaitExamples.WaitsForCondition.class);

        // 操作と確認：違反にならない
        assertThat(NO_TIME_WAIT.evaluate(examples).hasViolation()).isFalse();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 時間で待つ例を返す。
     *
     * @return 例のクラス（待ち方の名前を付ける）
     */
    static Stream<Named<Class<?>>> timeWaitExamples() {
        return Stream.of(
                Named.of("Thread.sleep", TimeWaitExamples.SleepsThread.class),
                Named.of("TimeUnit.sleep", TimeWaitExamples.SleepsTimeUnit.class),
                Named.of("Awaitility の pollDelay", TimeWaitExamples.PollsWithDelay.class),
                Named.of("Awaitility の during", TimeWaitExamples.WaitsDuring.class));
    }

    // ============================== 規則の補助 ==============================

    /**
     * {@code Thread.sleep} か {@code TimeUnit} の {@code sleep} の呼び出しかを返す。
     *
     * @param call メソッドの呼び出し
     * @return sleep の呼び出しなら true
     */
    private static boolean isSleep(JavaMethodCall call) {
        return call.getName().equals("sleep")
                && (call.getTargetOwner().isEquivalentTo(Thread.class) || call.getTargetOwner().isEquivalentTo(TimeUnit.class));
    }

    /**
     * Awaitility の {@code during} か {@code pollDelay} の呼び出しかを返す。
     *
     * @param call メソッドの呼び出し
     * @return 決まった時間の条件の呼び出しなら true
     */
    private static boolean isFixedTimeCondition(JavaMethodCall call) {
        return FIXED_TIME_CONDITIONS.contains(call.getName()) && call.getTargetOwner().getName().equals(CONDITION_FACTORY);
    }
}

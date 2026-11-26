package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.module.payment.infrastructure.mock.TestPaymentActions.Timing;
import com.ecsite.support.async.Concurrently;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * テスト用の処理の差し込み（{@link TestPaymentActions}）の検証
 */
@DisplayName("テスト用の処理の差し込み")
class TestPaymentActionsTest {

    // ============================== 定数 ==============================

    /**
     * 同時に呼ぶスレッドの数
     */
    private static final int THREADS = 8;

    /**
     * 1 つのスレッドが呼ぶ回数
     */
    private static final int CALLS_PER_THREAD = 10;

    /**
     * 確認する処理の差し込み
     */
    private final TestPaymentActions actions = new TestPaymentActions();

    /**
     * 処理が動いた回数
     */
    private final AtomicInteger count = new AtomicInteger();

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Timing.class)
    @DisplayName("次の 1 回だけの処理を設定すると、最初に呼んだときだけ動く")
    void onceRunsOnlyFirstTime(Timing timing) {
        // 準備：次の 1 回だけの処理を設定する
        actions.once(timing, count::incrementAndGet);

        // 操作：3 回呼ぶ
        runTimes(timing, 3);

        // 確認：1 回だけ動く
        assertThat(count).hasValue(1);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Timing.class)
    @DisplayName("呼ばれるたびの処理を設定すると、呼ぶたびに動く")
    void alwaysRunsEveryTime(Timing timing) {
        // 準備：呼ばれるたびの処理を設定する
        actions.always(timing, count::incrementAndGet);

        // 操作：3 回呼ぶ
        runTimes(timing, 3);

        // 確認：3 回動く
        assertThat(count).hasValue(3);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Timing.class)
    @DisplayName("何も設定しないで呼ぶと、何も起きない")
    void doesNothingWhenNotSet(Timing timing) {
        // 操作と確認：設定せずに呼んでも、例外にならず何も動かない
        runTimes(timing, 2);
        assertThat(count).hasValue(0);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Timing.class)
    @DisplayName("同じ時点に両方の処理を設定すると、次の 1 回だけの処理、呼ばれるたびの処理の順に動く")
    void onceRunsBeforeAlways(Timing timing) {
        // 準備：同じ時点に、動いた順を残す 2 つの処理を設定する
        List<String> order = new ArrayList<>();
        actions.once(timing, () -> order.add("once"));
        actions.always(timing, () -> order.add("always"));

        // 操作と確認：最初の呼び出しは once、always の順に動き、2 回目は always だけが動く
        actions.run(timing);
        assertThat(order).containsExactly("once", "always");
        actions.run(timing);
        assertThat(order).containsExactly("once", "always", "always");
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Timing.class)
    @DisplayName("ある時点に処理を設定しても、他の時点では動かない")
    void doesNotRunAtOtherTimings(Timing timing) {
        // 準備：1 つの時点に、次の 1 回だけの処理と呼ばれるたびの処理を設定する
        actions.once(timing, count::incrementAndGet);
        actions.always(timing, count::incrementAndGet);

        // 操作：他の時点を呼ぶ
        for (var other : Timing.values()) {
            if (other != timing) {
                actions.run(other);
            }
        }

        // 確認：動かない
        assertThat(count).hasValue(0);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Timing.class)
    @DisplayName("次の 1 回だけの処理を設定し直すと、前の処理は動かず新しい処理だけが動く")
    void onceIsOverwritten(Timing timing) {
        // 準備：次の 1 回だけの処理を 2 回設定する
        var old = new AtomicInteger();
        actions.once(timing, old::incrementAndGet);
        actions.once(timing, count::incrementAndGet);

        // 操作：2 回呼ぶ
        runTimes(timing, 2);

        // 確認：新しい処理だけが 1 回動く
        assertThat(old).hasValue(0);
        assertThat(count).hasValue(1);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Timing.class)
    @DisplayName("呼ばれるたびの処理を設定し直すと、前の処理は動かず新しい処理だけが動く")
    void alwaysIsOverwritten(Timing timing) {
        // 準備：呼ばれるたびの処理を 2 回設定する
        var old = new AtomicInteger();
        actions.always(timing, old::incrementAndGet);
        actions.always(timing, count::incrementAndGet);

        // 操作：2 回呼ぶ
        runTimes(timing, 2);

        // 確認：新しい処理だけが動く
        assertThat(old).hasValue(0);
        assertThat(count).hasValue(2);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Timing.class)
    @DisplayName("次の 1 回だけの処理の中から同じ時点を呼んでも、2 回目は動かない")
    void onceDoesNotRunAgainWhenCalledFromItself(Timing timing) {
        // 準備：処理の中から同じ時点を呼ぶ
        actions.once(timing, () -> {
            count.incrementAndGet();
            actions.run(timing);
        });

        // 操作：呼ぶ
        actions.run(timing);

        // 確認：1 回だけ動く
        assertThat(count).hasValue(1);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Timing.class)
    @DisplayName("次の 1 回だけの処理が例外を投げると、そのまま伝わり、次からは動かない")
    void onceExceptionPropagatesAndIsRemoved(Timing timing) {
        // 準備：例外を投げる処理を設定する
        var failure = new IllegalStateException("once");
        actions.once(timing, () -> {
            count.incrementAndGet();
            throw failure;
        });

        // 操作と確認：最初は同じ例外が伝わり、次は何も起きない
        assertThatThrownBy(() -> actions.run(timing)).isSameAs(failure);
        actions.run(timing);
        assertThat(count).hasValue(1);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Timing.class)
    @DisplayName("呼ばれるたびの処理が例外を投げると、そのまま伝わり、次も動く")
    void alwaysExceptionPropagatesAndKeepsRunning(Timing timing) {
        // 準備：例外を投げる処理を設定する
        var failure = new IllegalStateException("always");
        actions.always(timing, () -> {
            count.incrementAndGet();
            throw failure;
        });

        // 操作と確認：呼ぶたびに同じ例外が伝わる
        assertThatThrownBy(() -> actions.run(timing)).isSameAs(failure);
        assertThatThrownBy(() -> actions.run(timing)).isSameAs(failure);
        assertThat(count).hasValue(2);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Timing.class)
    @DisplayName("同時に呼んでも、次の 1 回だけの処理は 1 回だけ動く")
    void onceRunsExactlyOnceWhenCalledConcurrently(Timing timing) throws Exception {
        // 準備：次の 1 回だけの処理を設定する
        actions.once(timing, count::incrementAndGet);

        // 操作：全てのスレッドで同時に呼ぶ
        runConcurrently(timing, CALLS_PER_THREAD);

        // 確認：1 回だけ動く
        assertThat(count).hasValue(1);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Timing.class)
    @DisplayName("同時に呼ぶと、呼ばれるたびの処理は呼ばれた回数だけ動く")
    void alwaysRunsAsManyTimesAsCalledConcurrently(Timing timing) throws Exception {
        // 準備：呼ばれるたびの処理を設定する
        actions.always(timing, count::incrementAndGet);

        // 操作：全てのスレッドで同時に呼ぶ
        runConcurrently(timing, CALLS_PER_THREAD);

        // 確認：呼んだ回数だけ動く
        assertThat(count).hasValue(THREADS * CALLS_PER_THREAD);
    }

    // ============================== 確認の補助 ==============================

    /**
     * 指定した回数だけ続けて呼ぶ。
     *
     * @param timing 処理を行う時点
     * @param times  呼ぶ回数
     */
    private void runTimes(Timing timing, int times) {
        for (var i = 0; i < times; i++) {
            actions.run(timing);
        }
    }

    /**
     * 全てのスレッドを同時に始めて、それぞれが指定した回数ずつ呼び、全ての終わりを待つ。
     *
     * @param timing         処理を行う時点
     * @param timesPerThread 1 スレッドが呼ぶ回数
     * @throws Exception スレッドの処理が失敗したとき
     */
    private void runConcurrently(Timing timing, int timesPerThread) throws Exception {
        Concurrently.run(THREADS, thread -> {
            runTimes(timing, timesPerThread);
            return null;
        });
    }
}

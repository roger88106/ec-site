package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.module.payment.infrastructure.mock.TestPaymentFailures.Kind;
import com.ecsite.support.async.Concurrently;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * テスト用の失敗の注入（{@link TestPaymentFailures}）の検証
 */
@DisplayName("テスト用の失敗の注入")
class TestPaymentFailuresTest {

    // ============================== 定数 ==============================

    /**
     * 同時に呼ぶスレッドの数
     */
    private static final int THREADS = 8;

    /**
     * 1 つのスレッドが確認する回数
     */
    private static final int CALLS_PER_THREAD = 20;

    /**
     * 確認する失敗の注入
     */
    private final TestPaymentFailures failures = new TestPaymentFailures();

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Kind.class)
    @DisplayName("回数を指定すると、その回数だけ失敗して、次からは失敗しなくなる")
    void failsOnlyGivenTimes(Kind kind) {
        // 準備：2 回失敗させる
        failures.failNext(kind, 2);

        // 操作：4 回続けて確認する
        var results = consumeTimes(kind, 4);

        // 確認：2 回だけ失敗する
        assertThat(results).containsExactly(true, true, false, false);
    }

    @ParameterizedTest(name = "「{0}」")
    @MethodSource("timesThatDoNotFail")
    @DisplayName("回数を指定しない、0 回、負の回数のいずれでも、失敗しない")
    void doesNotFailWhenTimesIsNotPositive(String description, OptionalInt times) {
        // 準備：全ての種類に、回数を指定する（指定しない場合は何もしない）
        for (var kind : Kind.values()) {
            times.ifPresent(value -> failures.failNext(kind, value));
        }

        // 操作：全ての種類を確認する
        var results = new ArrayList<Boolean>();
        for (var kind : Kind.values()) {
            results.addAll(consumeTimes(kind, 2));
        }

        // 確認：失敗しない
        assertThat(results).containsOnly(false);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Kind.class)
    @DisplayName("1 回を指定すると、1 回だけ失敗する")
    void failsOnceWhenOne(Kind kind) {
        // 準備：1 回を指定する
        failures.failNext(kind, 1);

        // 操作：確認する
        var results = consumeTimes(kind, 2);

        // 確認：最初だけ失敗する
        assertThat(results).containsExactly(true, false);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Kind.class)
    @DisplayName("ある種類に回数を指定しても、他の種類は失敗しない")
    void doesNotAffectOtherKinds(Kind kind) {
        // 準備：1 つの種類に回数を指定する
        failures.failNext(kind, 3);

        // 操作：他の種類を確認する
        var otherResults = new ArrayList<Boolean>();
        for (var other : Kind.values()) {
            if (other != kind) {
                otherResults.add(failures.consume(other));
            }
        }

        // 確認：他の種類は失敗せず、指定した種類の回数は減っていない
        assertThat(otherResults).containsOnly(false);
        assertThat(consumeTimes(kind, 4)).containsExactly(true, true, true, false);
    }

    @ParameterizedTest(name = "「{0}」")
    @ValueSource(ints = {1, 5})
    @DisplayName("多い回数を指定した後に少ない回数を指定すると、少ない回数に置き換わる")
    void overwritesWithSmallerTimes(int smaller) {
        // 準備：多い回数、少ない回数の順に指定する
        failures.failNext(Kind.REFUND, 10);
        failures.failNext(Kind.REFUND, smaller);

        // 操作：確認する
        var failed = consumeTimes(Kind.REFUND, 12).stream().filter(Boolean::booleanValue).count();

        // 確認：後から指定した回数だけ失敗する
        assertThat(failed).isEqualTo(smaller);
    }

    @Test
    @DisplayName("途中まで減らした後に回数を指定すると、前の残りに加えずに置き換わる")
    void overwritesWithoutAddingToRemaining() {
        // 準備：3 回指定して 1 回減らし、残り 2 のところで 2 回を指定し直す
        failures.failNext(Kind.CLOSE, 3);
        failures.consume(Kind.CLOSE);
        failures.failNext(Kind.CLOSE, 2);

        // 操作：確認する
        var results = consumeTimes(Kind.CLOSE, 4);

        // 確認：指定し直した 2 回だけ失敗する
        assertThat(results).containsExactly(true, true, false, false);
    }

    @Test
    @DisplayName("使い切った後に回数を指定し直すと、指定し直した回数だけ失敗する")
    void overwritesWithLargerTimesAfterConsuming() {
        // 準備：1 回指定して使い切り、3 回を指定し直す
        failures.failNext(Kind.OPEN, 1);
        failures.consume(Kind.OPEN);
        failures.failNext(Kind.OPEN, 3);

        // 操作：確認する
        var results = consumeTimes(Kind.OPEN, 4);

        // 確認：3 回失敗する
        assertThat(results).containsExactly(true, true, true, false);
    }

    @Test
    @DisplayName("拒否する画面に指定した画面は、何度聞いても拒否される")
    void rejectedAuthorizationFetchStaysRejected() {
        // 準備：1 つの画面を拒否する
        failures.rejectAuthorizationFetch("page-1");

        // 操作と確認：何度聞いても、指定した画面だけ拒否される
        assertThat(failures.isAuthorizationFetchRejected("page-1")).isTrue();
        assertThat(failures.isAuthorizationFetchRejected("page-1")).isTrue();
        assertThat(failures.isAuthorizationFetchRejected("page-2")).isFalse();
    }

    @Test
    @DisplayName("期限切れに指定した与信は、何度聞いても期限切れになる")
    void expiredAuthorizationStaysExpired() {
        // 準備：1 つの与信を期限切れにする
        failures.expireAuthorization("auth-1");

        // 操作と確認：何度聞いても、指定した与信だけ期限切れになる
        assertThat(failures.isExpired("auth-1")).isTrue();
        assertThat(failures.isExpired("auth-1")).isTrue();
        assertThat(failures.isExpired("auth-2")).isFalse();
    }

    @Test
    @DisplayName("拒否する画面と期限切れの与信は、互いに影響しない")
    void rejectionAndExpirationAreIndependent() {
        // 準備：1 つの文字列は画面だけ拒否し、別の文字列は与信だけ期限切れにする
        failures.rejectAuthorizationFetch("rejected");
        failures.expireAuthorization("expired");

        // 操作と確認：指定していない側には影響しない
        assertThat(failures.isExpired("rejected")).isFalse();
        assertThat(failures.isAuthorizationFetchRejected("expired")).isFalse();
    }

    @Test
    @DisplayName("同時に確認しても、失敗する数は指定した回数とちょうど同じになる")
    void failsExactlyGivenTimesWhenConsumedConcurrently() throws Exception {
        // 準備：全てのスレッドが確認する合計より少ない回数を指定する
        var times = THREADS * CALLS_PER_THREAD / 2;
        failures.failNext(Kind.CAPTURE_CONNECTION, times);

        // 操作：全てのスレッドを同時に始めて、終わりを待つ
        var failedCounts = Concurrently.run(THREADS, thread ->
                consumeTimes(Kind.CAPTURE_CONNECTION, CALLS_PER_THREAD).stream().filter(Boolean::booleanValue).count());

        // 確認：失敗した数が指定した回数とちょうど同じ
        assertThat(failedCounts.stream().mapToLong(Long::longValue).sum()).isEqualTo(times);
    }

    // ============================== 確認の補助 ==============================

    /**
     * 失敗しない回数の指定
     *
     * @return 名前と、指定する回数（指定しないときは空）
     */
    private static Stream<Arguments> timesThatDoNotFail() {
        return Stream.of(
                Arguments.of("指定しない", OptionalInt.empty()),
                Arguments.of("0 回", OptionalInt.of(0)),
                Arguments.of("負の回数", OptionalInt.of(-1)));
    }

    /**
     * 指定した回数だけ続けて確認し、結果を順に返す。
     *
     * @param kind  失敗の種類
     * @param count 確認する回数
     * @return 確認した結果（失敗なら true）
     */
    private List<Boolean> consumeTimes(Kind kind, int count) {
        var results = new ArrayList<Boolean>();
        for (var i = 0; i < count; i++) {
            results.add(failures.consume(kind));
        }
        return results;
    }
}

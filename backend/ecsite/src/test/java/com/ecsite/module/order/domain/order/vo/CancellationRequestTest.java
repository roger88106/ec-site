package com.ecsite.module.order.domain.order.vo;

import com.ecsite.common.model.id.OperatorId;
import com.ecsite.common.model.value.Operator;
import com.ecsite.module.order.domain.order.vo.CancellationRequest.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * キャンセルの申請の単体テスト
 *
 * <p>コンストラクターが、申請の結果と、決定の日時・決定者の有無の整合を検査することだけを確認する。
 */
class CancellationRequestTest {

    // ============================== 定数 ==============================

    /**
     * 申請日時
     */
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-08T01:00:00Z");

    /**
     * 決定の日時
     */
    private static final Instant DECIDED_AT = Instant.parse("2026-10-08T02:00:00Z");

    /**
     * 決定した運営者
     */
    private static final Operator OPERATOR = new Operator(new OperatorId(UUID.fromString("00000000-0000-0000-0000-000000000001")), "管理 花子");

    // ============================== テスト ==============================

    @Nested
    @DisplayName("申請中")
    class Pending {

        @Test
        @DisplayName("決定の日時も決定者もない申請中は受け付ける")
        void acceptsUndecided() {
            // 操作：決定の日時と決定者のない申請中の申請を作る
            var request = new CancellationRequest(REQUESTED_AT, null, Result.PENDING, null, null);

            // 確認：決定の日時と決定者がない
            assertThat(request.decidedAt()).isNull();
            assertThat(request.decidedBy()).isNull();
        }

        @Test
        @DisplayName("決定の日時がある申請中は拒否される")
        void rejectsDecidedAt() {
            // 操作と確認：IllegalArgumentException で拒否される
            assertThatThrownBy(() -> new CancellationRequest(REQUESTED_AT, null, Result.PENDING, DECIDED_AT, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("決定者がある申請中は拒否される")
        void rejectsDecidedBy() {
            // 操作と確認：IllegalArgumentException で拒否される
            assertThatThrownBy(() -> new CancellationRequest(REQUESTED_AT, null, Result.PENDING, null, OPERATOR))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("承認・却下")
    class Decided {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = Result.class, names = {"APPROVED", "REJECTED"})
        @DisplayName("決定の日時と決定者がある承認・却下は受け付ける")
        void acceptsDecided(Result result) {
            // 操作：決定の日時と決定者のある申請を作る
            var request = new CancellationRequest(REQUESTED_AT, null, result, DECIDED_AT, OPERATOR);

            // 確認：決定の日時と決定者を持つ
            assertThat(request.decidedAt()).isEqualTo(DECIDED_AT);
            assertThat(request.decidedBy()).isEqualTo(OPERATOR);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = Result.class, names = {"APPROVED", "REJECTED"})
        @DisplayName("決定の日時がない承認・却下は拒否される")
        void rejectsMissingDecidedAt(Result result) {
            // 操作と確認：IllegalArgumentException で拒否される
            assertThatThrownBy(() -> new CancellationRequest(REQUESTED_AT, null, result, null, OPERATOR))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = Result.class, names = {"APPROVED", "REJECTED"})
        @DisplayName("決定者がない承認・却下は受け付ける（記録を始める前の判断）")
        void acceptsMissingDecidedBy(Result result) {
            // 操作：決定の日時はあるが決定者のない申請を作る
            var request = new CancellationRequest(REQUESTED_AT, null, result, DECIDED_AT, null);

            // 確認：決定の日時を持ち、決定者はない
            assertThat(request.decidedAt()).isEqualTo(DECIDED_AT);
            assertThat(request.decidedBy()).isNull();
        }
    }
}

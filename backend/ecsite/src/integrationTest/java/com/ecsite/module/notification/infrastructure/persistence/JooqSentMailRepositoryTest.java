package com.ecsite.module.notification.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.module.notification.application.port.SentMailRepository;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 送ったメールの記録のリポジトリの統合テスト
 *
 * <p>同じ注文と種類のメールを 1 回だけ記録すること（出荷通知メールを 1 通だけ送るための記録）を確認する。
 * 記録を使って 1 通だけ送ることは {@code ShipmentMailTest} が確認する。
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqSentMailRepositoryTest {

    // ============================== 定数 ==============================

    /**
     * 送った日時
     */
    private static final Instant SENT_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 先に記録する宛先
     */
    private static final String RECIPIENT = "taro@example.com";

    /**
     * 後から記録しようとする、別の宛先
     */
    private static final String OTHER_RECIPIENT = "other@example.com";

    // ============================== Bean ==============================

    @Autowired
    SentMailRepository repository;

    @Autowired
    DSLContext dsl;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("初めての記録")
    class First {

        @Test
        @DisplayName("送ったメールを初めて記録すると、true を返す")
        void returnsTrue() {
            // 操作と確認：true
            assertThat(repository.recordIfAbsent(UUID.randomUUID(), SentMailRepository.Kind.SHIPMENT, RECIPIENT, SENT_AT))
                    .isTrue();
        }

        @Test
        @DisplayName("送ったメールを記録すると、種類・宛先・送った日時を保存する")
        void savesRecord() {
            // 操作：記録する
            var orderId = UUID.randomUUID();
            repository.recordIfAbsent(orderId, SentMailRepository.Kind.SHIPMENT, RECIPIENT, SENT_AT);

            // 確認：この注文の行の項目
            assertSavedRow(dsl.fetchOne("select kind, recipient, sent_at from notification.sent_mail where order_id = ?", orderId));
        }

        @Test
        @DisplayName("注文が違えば、同じ種類のメールも別に記録できる")
        void recordsMailOfOtherOrder() {
            // 準備：別の注文のメールを記録する
            repository.recordIfAbsent(UUID.randomUUID(), SentMailRepository.Kind.SHIPMENT, RECIPIENT, SENT_AT);

            // 操作と確認：true
            assertThat(repository.recordIfAbsent(UUID.randomUUID(), SentMailRepository.Kind.SHIPMENT, RECIPIENT, SENT_AT))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("2 回目の記録")
    class Second {

        @Test
        @DisplayName("同じ注文と種類の記録がすでにあれば、false を返す")
        void returnsFalse() {
            // 準備：記録する
            var orderId = UUID.randomUUID();
            repository.recordIfAbsent(orderId, SentMailRepository.Kind.SHIPMENT, RECIPIENT, SENT_AT);

            // 操作と確認：別の宛先で記録しようとすると false
            assertThat(repository.recordIfAbsent(orderId, SentMailRepository.Kind.SHIPMENT, OTHER_RECIPIENT,
                    SENT_AT.plusSeconds(60))).isFalse();
        }

        @Test
        @DisplayName("同じ注文と種類の記録がすでにあれば、記録は 1 つのままで、先の記録の宛先が残る")
        void keepsFirstRecord() {
            // 準備：記録する
            var orderId = UUID.randomUUID();
            repository.recordIfAbsent(orderId, SentMailRepository.Kind.SHIPMENT, RECIPIENT, SENT_AT);

            // 操作：別の宛先で記録しようとする
            repository.recordIfAbsent(orderId, SentMailRepository.Kind.SHIPMENT, OTHER_RECIPIENT, SENT_AT.plusSeconds(60));

            // 確認：この注文の行は 1 つで、先の宛先
            assertThat(dsl.fetchValue("select count(*) from notification.sent_mail where order_id = ?", orderId))
                    .isEqualTo(1L);
            assertThat(dsl.fetchValue("select recipient from notification.sent_mail where order_id = ?", orderId))
                    .isEqualTo(RECIPIENT);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 保存した行が、記録した値を持つことを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>行がある</li>
     *   <li>種類が出荷通知</li>
     *   <li>宛先</li>
     *   <li>送った日時</li>
     * </ul>
     *
     * @param row 注文 ID で読んだ行
     */
    private static void assertSavedRow(Record row) {
        assertThat(row).isNotNull();
        assertThat(row.get("kind", String.class)).isEqualTo("SHIPMENT");
        assertThat(row.get("recipient", String.class)).isEqualTo(RECIPIENT);
        assertThat(row.get("sent_at", OffsetDateTime.class).toInstant()).isEqualTo(SENT_AT);
    }
}

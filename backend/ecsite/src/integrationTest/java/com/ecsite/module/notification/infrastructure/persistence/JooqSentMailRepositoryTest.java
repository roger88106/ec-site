package com.ecsite.module.notification.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.module.notification.application.port.SentMailRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 送ったメールの記録のリポジトリの統合テスト。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqSentMailRepositoryTest {

    /**
     * 送った日時
     */
    private static final Instant SENT_AT = Instant.parse("2026-10-07T15:30:00Z");

    @Autowired
    SentMailRepository repository;

    @Autowired
    DSLContext dsl;

    @Test
    @DisplayName("送ったメールを記録すると true を返し、宛先と送った日時を保存する")
    void recordsSentMail() {
        var orderId = UUID.randomUUID();

        assertThat(repository.recordIfAbsent(orderId, SentMailRepository.Kind.SHIPMENT, "taro@example.com", SENT_AT))
                .isTrue();

        var row = dsl.fetchOne("select kind, recipient, sent_at from notification.sent_mail where order_id = ?", orderId);
        assertThat(row).isNotNull();
        assertThat(row.get("kind", String.class)).isEqualTo("SHIPMENT");
        assertThat(row.get("recipient", String.class)).isEqualTo("taro@example.com");
        assertThat(row.get("sent_at", OffsetDateTime.class).toInstant()).isEqualTo(SENT_AT);
    }

    @Test
    @DisplayName("同じ注文と種類の記録がすでにあれば false を返し、先の記録の宛先が残る")
    void doesNotRecordSameMailTwice() {
        var orderId = UUID.randomUUID();
        repository.recordIfAbsent(orderId, SentMailRepository.Kind.SHIPMENT, "taro@example.com", SENT_AT);

        assertThat(repository.recordIfAbsent(orderId, SentMailRepository.Kind.SHIPMENT, "other@example.com",
                SENT_AT.plusSeconds(60))).isFalse();

        assertThat(dsl.fetchValue("select count(*) from notification.sent_mail where order_id = ?", orderId))
                .isEqualTo(1L);
        assertThat(dsl.fetchValue("select recipient from notification.sent_mail where order_id = ?", orderId))
                .isEqualTo("taro@example.com");
    }

    @Test
    @DisplayName("注文が違えば、同じ種類のメールも別に記録できる")
    void recordsMailOfOtherOrder() {
        repository.recordIfAbsent(UUID.randomUUID(), SentMailRepository.Kind.SHIPMENT, "taro@example.com", SENT_AT);

        assertThat(repository.recordIfAbsent(UUID.randomUUID(), SentMailRepository.Kind.SHIPMENT, "taro@example.com",
                SENT_AT)).isTrue();
    }
}

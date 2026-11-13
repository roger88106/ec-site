package com.ecsite.module.notification.application;

import com.ecsite.common.message.MessageResolver;
import com.ecsite.module.notification.application.port.Mailer;
import com.ecsite.module.order.event.OrderShipped;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出荷通知メールのサービスの単体テスト
 *
 * <p>送ったメールの記録はいつも「まだ送っていない」と答え、送ったメールを受け取って、組み立てた内容を確認する。
 */
class ShipmentMailServiceTest {

    private final List<Mailer.Mail> sent = new ArrayList<>();

    private ShipmentMailService service;

    @BeforeEach
    void setUp() {
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        service = new ShipmentMailService((orderId, kind, recipient, sentAt) -> true, sent::add,
                Clock.fixed(Instant.parse("2026-10-08T01:00:00Z"), ZoneOffset.UTC),
                new MessageResolver(messageSource), carrier -> switch (carrier) {
                    case YAMATO -> "ecsite.shipping.carrier.yamato";
                    case SAGAWA -> "ecsite.shipping.carrier.sagawa";
                    case JAPAN_POST -> "ecsite.shipping.carrier.japan_post";
                });
    }

    @Test
    @DisplayName("US-38 宛先は注文のメールアドレス。件名に注文番号、本文に注文番号・配送業者の表示名・送り状番号が入る")
    void composes() {
        var event = OrderShipped.of(Instant.parse("2026-10-08T01:00:00Z"), UUID.randomUUID(), "20261008-123456",
                "taro@example.com", OrderShipped.Carrier.YAMATO, "123456789012");

        var mail = sentMailOf(event);

        assertThat(mail.to()).isEqualTo("taro@example.com");
        assertThat(mail.subject()).contains("20261008-123456");
        assertThat(mail.body()).contains("20261008-123456", "ヤマト運輸", "123456789012");
    }

    @Test
    @DisplayName("US-38 配送業者ごとに表示名を使う（佐川急便、日本郵便）")
    void usesCarrierNames() {
        var sagawa = OrderShipped.of(Instant.now(), UUID.randomUUID(), "20261008-000001", "a@example.com",
                OrderShipped.Carrier.SAGAWA, "1");
        var japanPost = OrderShipped.of(Instant.now(), UUID.randomUUID(), "20261008-000002", "b@example.com",
                OrderShipped.Carrier.JAPAN_POST, "2");

        assertThat(sentMailOf(sagawa).body()).contains("佐川急便");
        assertThat(sentMailOf(japanPost).body()).contains("日本郵便");
    }

    /**
     * 注文の出荷をサービスに渡し、送ったメールを返す。
     */
    private Mailer.Mail sentMailOf(OrderShipped event) {
        sent.clear();
        service.send(event);
        assertThat(sent).hasSize(1);
        return sent.getFirst();
    }
}

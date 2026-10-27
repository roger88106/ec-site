package com.ecsite.module.notification.application;

import com.ecsite.common.message.MessageHelper;
import com.ecsite.module.order.event.OrderShipped;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出荷通知メールの組み立ての単体テスト
 */
class ShipmentMailComposerTest {

    private ShipmentMailComposer composer;

    @BeforeEach
    void setUp() {
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        composer = new ShipmentMailComposer(new MessageHelper(messageSource), carrier -> switch (carrier) {
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

        var mail = composer.compose(event);

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

        assertThat(composer.compose(sagawa).body()).contains("佐川急便");
        assertThat(composer.compose(japanPost).body()).contains("日本郵便");
    }
}

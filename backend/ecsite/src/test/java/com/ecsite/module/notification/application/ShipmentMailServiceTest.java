package com.ecsite.module.notification.application;

import com.ecsite.common.message.MessageResolver;
import com.ecsite.module.notification.application.port.Mailer;
import com.ecsite.module.order.event.OrderShipped;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * <p>出荷通知メールの宛先と内容の組み立てだけを確認する。送ったメールの記録はいつも「まだ送っていない」と答え、
 * 送ったメールを受け取って、組み立てた内容を確認する。再送と 1 通だけ届くことは、通知の統合テストが確認する。
 */
class ShipmentMailServiceTest {

    // ============================== 定数 ==============================

    /**
     * 出荷日時
     */
    private static final Instant SHIPPED_AT = Instant.parse("2026-10-08T01:00:00Z");

    /**
     * 注文番号
     */
    private static final String ORDER_NUMBER = "20261008-123456";

    /**
     * 注文のメールアドレス
     */
    private static final String EMAIL = "taro@example.com";

    /**
     * 送り状番号
     */
    private static final String TRACKING_NUMBER = "123456789012";

    /**
     * 送ったメール
     */
    private final List<Mailer.Mail> sent = new ArrayList<>();

    /**
     * 出荷通知メールのサービス
     */
    private ShipmentMailService service;

    // ============================== テスト ==============================

    /**
     * 文言のファイルを読み、配送業者の表示名のキーを返すサービスを作る。
     */
    @BeforeEach
    void setUp() {
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        service = new ShipmentMailService((orderId, kind, recipient, sentAt) -> true, sent::add,
                Clock.fixed(SHIPPED_AT, ZoneOffset.UTC),
                new MessageResolver(messageSource), carrier -> switch (carrier) {
                    case YAMATO -> "ecsite.shipping.carrier.yamato";
                    case SAGAWA -> "ecsite.shipping.carrier.sagawa";
                    case JAPAN_POST -> "ecsite.shipping.carrier.japan_post";
                });
    }

    @Nested
    @DisplayName("宛先と件名")
    class Header {

        @Test
        @DisplayName("US-38 宛先は、注文のメールアドレス")
        void sendsToOrderEmail() {
            // 操作：ヤマト運輸の出荷を渡す
            var mail = sentMailOf(yamatoShipment());

            // 確認：注文のメールアドレス
            assertThat(mail.to()).isEqualTo(EMAIL);
        }

        @Test
        @DisplayName("US-38 件名に、注文番号が入る")
        void subjectHasOrderNumber() {
            // 操作：ヤマト運輸の出荷を渡す
            var mail = sentMailOf(yamatoShipment());

            // 確認：注文番号がある
            assertThat(mail.subject()).contains(ORDER_NUMBER);
        }
    }

    @Nested
    @DisplayName("本文")
    class Body {

        @Test
        @DisplayName("US-38 本文に、注文番号と配送業者の表示名と送り状番号が入る")
        void bodyHasShipment() {
            // 操作：ヤマト運輸の出荷を渡す
            var mail = sentMailOf(yamatoShipment());

            // 確認：出荷の内容がある
            assertThat(mail.body()).contains(ORDER_NUMBER, "ヤマト運輸", TRACKING_NUMBER);
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({
                "SAGAWA, 佐川急便",
                "JAPAN_POST, 日本郵便",
        })
        @DisplayName("US-38 本文の配送業者は、配送業者ごとの表示名")
        void usesCarrierName(OrderShipped.Carrier carrier, String name) {
            // 準備：配送業者の出荷
            var event = OrderShipped.of(SHIPPED_AT, UUID.randomUUID(), ORDER_NUMBER, EMAIL, carrier, TRACKING_NUMBER);

            // 操作と確認：表示名がある
            assertThat(sentMailOf(event).body()).contains(name);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * ヤマト運輸の出荷を、定数から作る。
     *
     * @return 注文の出荷のイベント
     */
    private static OrderShipped yamatoShipment() {
        return OrderShipped.of(SHIPPED_AT, UUID.randomUUID(), ORDER_NUMBER, EMAIL, OrderShipped.Carrier.YAMATO, TRACKING_NUMBER);
    }

    /**
     * 注文の出荷をサービスに渡し、送ったメールを返す。
     *
     * <p>メールが 1 通だけ送られたことも確認する。
     *
     * @param event 注文の出荷のイベント
     * @return 送ったメール
     */
    private Mailer.Mail sentMailOf(OrderShipped event) {
        sent.clear();
        service.send(event);
        assertThat(sent).hasSize(1);
        return sent.getFirst();
    }
}

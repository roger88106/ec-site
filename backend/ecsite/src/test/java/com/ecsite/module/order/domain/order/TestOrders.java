package com.ecsite.module.order.domain.order;

import com.ecsite.common.model.id.MemberId;
import com.ecsite.common.model.id.OrderId;
import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.id.TaxCategoryId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.checkout.CheckoutId;
import com.ecsite.module.order.domain.order.vo.BuyerProfile;
import com.ecsite.module.order.domain.order.vo.OrderNumber;
import com.ecsite.module.order.domain.order.vo.ShippingAddress;
import com.ecsite.module.order.domain.quote.Amounts;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.domain.quote.TaxAmount;
import com.ecsite.module.order.domain.quote.VariantInfo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 注文のテストで使う、状態ごとの注文の作り方
 *
 * <p>注文の状態の遷移の単体テスト（支払・出荷・キャンセル）が、遷移の前の注文を作るのに使う。
 */
public final class TestOrders {

    /**
     * インスタンスを作らない。
     */
    private TestOrders() {
    }

    /**
     * 「支払待ち」の注文を作る。
     *
     * <p>成立のイベントは取り出してあるので、後の操作で登録されたイベントだけが残る。
     * 総額は 2,100 円（税込 1,100 円の商品 1 個と送料 1,000 円）。
     *
     * @param orderedAt 注文の日時
     * @return 支払待ちの注文
     */
    public static Order pending(Instant orderedAt) {
        var variant = new VariantInfo(new VariantId(UUID.randomUUID()), new ProductId(UUID.randomUUID()), "P-1", "P-1-1", "商品", List.of(),
                Money.of(1000), new TaxCategoryId(UUID.randomUUID()), null, true, false);
        var amounts = new Amounts(Money.of(1100), Money.of(1000), Money.of(2100),
                List.of(new TaxAmount(BigDecimal.TEN, Money.of(2100), Money.of(190))));
        var quote = new Quote(List.of(QuoteItem.of(variant, 1, 10, BigDecimal.TEN, Money.of(1100))), amounts, Money.of(90), true);
        var order = Order.place(new OrderId(UUID.randomUUID()), OrderNumber.of("20261008-000001"), new CheckoutId(UUID.randomUUID()), new MemberId(UUID.randomUUID()),
                orderedAt, quote, "taro@example.com",
                ShippingAddress.of("山田 太郎", "1000001", TestAreas.CHIYODA, "千代田 1-1", "0312345678"), BuyerProfile.UNKNOWN);
        order.pullEvents();
        return order;
    }

    /**
     * 「受付」の注文を作る。受付は注文の 30 秒後とする。
     *
     * <p>受付のイベントは取り出してあるので、後の操作で登録されたイベントだけが残る。
     *
     * @param orderedAt 注文の日時
     * @return 受付の注文
     */
    public static Order accepted(Instant orderedAt) {
        return accepted(orderedAt, orderedAt.plusSeconds(30));
    }

    /**
     * 「受付」の注文を作る。受付の時刻を指定する。
     *
     * <p>受付のイベントは取り出してあるので、後の操作で登録されたイベントだけが残る。
     *
     * @param orderedAt 注文の日時
     * @param acceptedAt 受付の日時
     * @return 受付の注文
     */
    public static Order accepted(Instant orderedAt, Instant acceptedAt) {
        var order = pending(orderedAt);
        order.acceptPayment(acceptedAt);
        order.pullEvents();
        return order;
    }

    /**
     * 「キャンセル申請中」の注文を作る。受付は注文の 30 秒後、申請は注文の 60 秒後で、理由は「理由」とする。
     *
     * <p>状態の変化のイベントは取り出してあるので、後の操作で登録されたイベントだけが残る。
     *
     * @param orderedAt 注文の日時
     * @return キャンセル申請中の注文
     */
    public static Order requested(Instant orderedAt) {
        return requested(orderedAt, orderedAt.plusSeconds(30), "理由", orderedAt.plusSeconds(60));
    }

    /**
     * 「キャンセル申請中」の注文を作る。受付の時刻・申請の理由と時刻を指定する。
     *
     * <p>状態の変化のイベントは取り出してあるので、後の操作で登録されたイベントだけが残る。
     *
     * @param orderedAt  注文の日時
     * @param acceptedAt 受付の日時
     * @param reason     申請の理由（null なら理由なし）
     * @param requestedAt 申請の日時
     * @return キャンセル申請中の注文
     */
    public static Order requested(Instant orderedAt, Instant acceptedAt, String reason, Instant requestedAt) {
        var order = accepted(orderedAt, acceptedAt);
        order.requestCancellation(reason, requestedAt);
        order.pullEvents();
        return order;
    }

    /**
     * 「出荷済み」の注文を作る。受付は注文の 30 秒後、出荷は注文の 1 時間後で、送り状番号は「123456789012」とする。
     *
     * <p>状態の変化と出荷のイベントは取り出してあるので、後の操作で登録されたイベントだけが残る。
     *
     * @param orderedAt 注文の日時
     * @return 出荷済みの注文
     */
    public static Order shipped(Instant orderedAt) {
        return shipped(orderedAt, orderedAt.plusSeconds(30), "123456789012", orderedAt.plusSeconds(3600));
    }

    /**
     * 「出荷済み」の注文を作る。受付の時刻・送り状番号と出荷の時刻を指定する。
     *
     * <p>状態の変化と出荷のイベントは取り出してあるので、後の操作で登録されたイベントだけが残る。
     *
     * @param orderedAt      注文の日時
     * @param acceptedAt     受付の日時
     * @param trackingNumber 送り状番号（ハイフンを除いた数字）
     * @param shippedAt      出荷の日時
     * @return 出荷済みの注文
     */
    public static Order shipped(Instant orderedAt, Instant acceptedAt, String trackingNumber, Instant shippedAt) {
        var order = accepted(orderedAt, acceptedAt);
        order.ship(Carrier.YAMATO, trackingNumber, shippedAt);
        order.pullEvents();
        return order;
    }
}

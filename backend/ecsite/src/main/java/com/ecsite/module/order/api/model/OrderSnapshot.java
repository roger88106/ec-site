package com.ecsite.module.order.api.model;

import com.ecsite.common.model.id.MemberId;
import com.ecsite.common.model.id.OrderId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.event.OrderStatusChanged;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 注文の今の値
 *
 * <p>氏名、住所、電話番号、メールアドレスは載せない。お届け先は郵便番号と都道府県だけ。
 *
 * @param orderId        注文 ID
 * @param orderNumber    注文番号
 * @param memberId       会員 ID
 * @param orderedAt      注文日時
 * @param status         注文の状態
 * @param cancellation   キャンセル。キャンセルでなければ null
 * @param postalCode     お届け先の郵便番号
 * @param prefectureCode お届け先の都道府県のコード
 * @param buyerAge       注文時の年齢。分からなければ null
 * @param buyerGender    注文時の性別。分からなければ null
 * @param amounts        金額の内訳
 * @param shippingTax    送料の消費税
 * @param lines          明細（行番号の順）
 */
public record OrderSnapshot(OrderId orderId, String orderNumber, MemberId memberId, Instant orderedAt,
                            OrderStatusChanged.Status status, @Nullable Cancellation cancellation, String postalCode,
                            String prefectureCode, @Nullable Integer buyerAge, @Nullable Gender buyerGender,
                            Amounts amounts, Money shippingTax, List<Line> lines) {

    /**
     * キャンセル
     *
     * @param cancelledAt キャンセル日時
     * @param kind        キャンセルの種類
     * @param restocked   在庫に戻したか
     */
    public record Cancellation(Instant cancelledAt, OrderStatusChanged.CancellationKind kind, boolean restocked) {
    }

    /**
     * 明細
     *
     * @param lineNo            行番号
     * @param variantId         バリアント ID
     * @param productCode       商品品番
     * @param variantCode       バリアント品番
     * @param productName       商品名
     * @param axisValues        軸の名前と値
     * @param quantity          数量
     * @param ratePercent       税率（%）
     * @param unitPriceExclTax  税抜単価
     * @param subtotalInclTax   税込小計
     */
    public record Line(int lineNo, VariantId variantId, String productCode, String variantCode, String productName,
                       List<AxisValue> axisValues, int quantity, BigDecimal ratePercent, Money unitPriceExclTax,
                       Money subtotalInclTax) {
    }

    /**
     * 性別
     */
    public enum Gender {

        /**
         * 男性
         */
        MALE,

        /**
         * 女性
         */
        FEMALE,

        /**
         * その他
         */
        OTHER,

        /**
         * 回答しない
         */
        UNDISCLOSED,
    }
}

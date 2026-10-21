package com.ecsite.module.order.api;

import com.ecsite.common.model.value.Money;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * レポート用の注文の写し（docs/design/modules/order.md「他のモジュールへの公開」）。<br />
 * 氏名、住所、電話番号、メールアドレスは載せない。お届け先は郵便番号と都道府県だけ。
 *
 * @param orderId          注文 ID
 * @param orderNumber      注文番号
 * @param memberId         会員 ID
 * @param orderedAt        注文日時
 * @param status           注文の状態
 * @param cancelledAt      キャンセル日時。キャンセルでなければ null
 * @param cancellationKind キャンセルの種類。キャンセルでなければ null
 * @param restocked        キャンセルで在庫に戻したか。キャンセルでなければ null
 * @param postalCode       お届け先の郵便番号
 * @param prefectureCode   お届け先の都道府県のコード
 * @param buyerAge         注文時の年齢。分からなければ null
 * @param buyerGender      注文時の性別。分からなければ null
 * @param total            総額（税込。商品の合計 ＋ 送料）
 * @param shippingFee      送料（税込）
 * @param taxTotal         消費税の合計（送料の分を含む）
 * @param shippingTax      送料の消費税
 * @param lines            明細（行番号の順）
 */
public record OrderReportSnapshot(UUID orderId, String orderNumber, UUID memberId, Instant orderedAt, Status status,
                                  @Nullable Instant cancelledAt, @Nullable CancellationKind cancellationKind,
                                  @Nullable Boolean restocked, String postalCode, String prefectureCode,
                                  @Nullable Integer buyerAge, @Nullable Gender buyerGender, Money total, Money shippingFee,
                                  Money taxTotal, Money shippingTax, List<Line> lines) {

    /**
     * 明細
     *
     * @param lineNo          行番号
     * @param variantId       バリアント ID
     * @param productCode     商品品番
     * @param variantCode     バリアント品番
     * @param productName     商品名
     * @param axisValues      軸の名前と値
     * @param quantity        数量
     * @param ratePercent     税率（%）
     * @param subtotalInclTax 税込小計
     * @param subtotalExclTax 税抜小計（税抜単価 × 数量）
     */
    public record Line(int lineNo, UUID variantId, String productCode, String variantCode, String productName,
                       List<AxisValue> axisValues, int quantity, BigDecimal ratePercent, Money subtotalInclTax,
                       Money subtotalExclTax) {
    }

    /**
     * 軸の名前と値
     *
     * @param name  軸の名前
     * @param value 値
     */
    public record AxisValue(String name, String value) {
    }

    /**
     * 注文の状態
     */
    public enum Status {

        /**
         * 支払待ち
         */
        PENDING_PAYMENT,

        /**
         * 受付
         */
        ACCEPTED,

        /**
         * キャンセル申請中
         */
        CANCELLATION_REQUESTED,

        /**
         * キャンセル
         */
        CANCELLED,

        /**
         * 出荷済み
         */
        SHIPPED,
    }

    /**
     * キャンセルの種類
     */
    public enum CancellationKind {

        /**
         * 申請の承認
         */
        REQUEST_APPROVED,

        /**
         * 運営者の判断
         */
        OPERATOR,

        /**
         * 支払期限切れ
         */
        PAYMENT_EXPIRED,
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

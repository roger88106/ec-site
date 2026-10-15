package com.ecsite.module.reporting.domain;

import com.ecsite.common.model.value.Money;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 注文の写し（レポート）。集計のために持つ、注文と明細の今の値（docs/backend/modules/reporting.md「集めるデータ」）。
 *
 * @param orderId          注文 ID
 * @param orderNumber      注文番号
 * @param memberId         会員 ID
 * @param orderedAt        注文日時
 * @param status           注文の状態（注文の状態の名前）
 * @param shippedAt        出荷日時。出荷していなければ null
 * @param deliveryStatus   配送状態。出荷していなければ null
 * @param cancelledAt      キャンセル日時。キャンセルでなければ null
 * @param cancellationKind キャンセルの種類。キャンセルでなければ null
 * @param restocked        キャンセルで在庫に戻したか。キャンセルでなければ null
 * @param postalCode       郵便番号
 * @param prefectureCode   全国地方公共団体コードの上 2 桁。読み替えられなければ null
 * @param buyerAge         注文時の年齢。分からなければ null
 * @param buyerGender      注文時の性別。分からなければ null
 * @param total            総額
 * @param shippingFee      送料
 * @param taxTotal         消費税の合計
 * @param shippingTax      送料の消費税
 * @param lines            明細
 */
public record ReportOrder(UUID orderId, String orderNumber, UUID memberId, Instant orderedAt, String status,
                          @Nullable Instant shippedAt, @Nullable DeliveryStatus deliveryStatus,
                          @Nullable Instant cancelledAt, @Nullable String cancellationKind, @Nullable Boolean restocked,
                          String postalCode, @Nullable String prefectureCode, @Nullable Integer buyerAge,
                          @Nullable String buyerGender, Money total, Money shippingFee, Money taxTotal, Money shippingTax,
                          List<Line> lines) {

    /**
     * 出荷日時と配送状態を差し替えた写しを返す。
     *
     * @param shippedAt      出荷日時
     * @param deliveryStatus 配送状態
     * @return 写し
     */
    public ReportOrder withShipment(@Nullable Instant shippedAt, @Nullable DeliveryStatus deliveryStatus) {
        return new ReportOrder(orderId, orderNumber, memberId, orderedAt, status, shippedAt, deliveryStatus, cancelledAt,
                cancellationKind, restocked, postalCode, prefectureCode, buyerAge, buyerGender, total, shippingFee, taxTotal,
                shippingTax, lines);
    }

    /**
     * 明細の商品 ID を差し替えた写しを返す。
     *
     * @param productIds 行番号 → 商品 ID。ない行は null
     * @return 写し
     */
    public ReportOrder withProductIds(Map<Integer, UUID> productIds) {
        var replaced = lines.stream().map(line -> new Line(line.lineNo(), productIds.get(line.lineNo()), line.variantId(),
                line.productCode(), line.variantCode(), line.productName(), line.axisValues(), line.quantity(),
                line.ratePercent(), line.subtotalInclTax(), line.subtotalExclTax())).toList();
        return new ReportOrder(orderId, orderNumber, memberId, orderedAt, status, shippedAt, deliveryStatus, cancelledAt,
                cancellationKind, restocked, postalCode, prefectureCode, buyerAge, buyerGender, total, shippingFee, taxTotal,
                shippingTax, replaced);
    }

    /**
     * 明細の写し
     *
     * @param lineNo          行番号
     * @param productId       商品 ID。求められなければ null
     * @param variantId       バリアント ID
     * @param productCode     商品品番
     * @param variantCode     バリアント品番
     * @param productName     商品名
     * @param axisValues      軸の名前と値
     * @param quantity        数量
     * @param ratePercent     税率（%）
     * @param subtotalInclTax 税込小計
     * @param subtotalExclTax 税抜小計
     */
    public record Line(int lineNo, @Nullable UUID productId, UUID variantId, String productCode, String variantCode,
                       String productName, List<AxisValue> axisValues, int quantity, BigDecimal ratePercent,
                       Money subtotalInclTax, Money subtotalExclTax) {
    }

    /**
     * 軸の名前と値
     *
     * @param name  軸の名前
     * @param value 値
     */
    public record AxisValue(String name, String value) {
    }
}

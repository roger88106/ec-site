package com.ecsite.module.reporting;

import org.jooq.DSLContext;
import org.jooq.JSONB;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.reporting.Tables.SALES_ORDER;
import static com.ecsite.jooq.reporting.Tables.SALES_ORDER_LINE;

/**
 * レポートの集計のテストで、写し（注文と明細）を DB に直接入れる補助。<br />
 * 写しの作り方（イベント）は ReportCollectionTest で確認するので、集計のテストは写しから始める。
 * 明細は税込単価 1,100 円（税抜 1,000 円、税率 10%）。送料は 1,000 円（消費税 90 円）。
 */
public class ReportTestData {

    /**
     * 日本時間
     */
    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * @param dsl jOOQ DSL Context
     */
    public ReportTestData(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * 注文の写しの組み立てを始める。
     *
     * @param status    注文の状態
     * @param orderedOn 注文日（日本時間の正午に注文したものとする）
     * @return 組み立て
     */
    public OrderBuilder order(String status, String orderedOn) {
        return new OrderBuilder(status, LocalDate.parse(orderedOn));
    }

    /**
     * 注文の写しの組み立て
     */
    public class OrderBuilder {

        private final UUID orderId = UUID.randomUUID();
        private final String status;
        private final LocalDate orderedOn;
        private LocalDate shippedOn;
        private String prefectureCode = "13";
        private Integer age;
        private String gender;
        private long shippingFee = 1000;
        private final List<Line> lines = new ArrayList<>();

        private OrderBuilder(String status, LocalDate orderedOn) {
            this.status = status;
            this.orderedOn = orderedOn;
        }

        /**
         * @param date 出荷日（日本時間の正午に出荷したものとする）
         * @return this
         */
        public OrderBuilder shippedOn(String date) {
            this.shippedOn = LocalDate.parse(date);
            return this;
        }

        /**
         * @param code 全国地方公共団体コードの上 2 桁。null なら不明
         * @return this
         */
        public OrderBuilder prefecture(String code) {
            this.prefectureCode = code;
            return this;
        }

        /**
         * @param age    注文時の年齢。null なら不明
         * @param gender 注文時の性別。null なら不明
         * @return this
         */
        public OrderBuilder buyer(Integer age, String gender) {
            this.age = age;
            this.gender = gender;
            return this;
        }

        /**
         * @param fee 送料（税込）。消費税は 10% の内税
         * @return this
         */
        public OrderBuilder shippingFee(long fee) {
            this.shippingFee = fee;
            return this;
        }

        /**
         * 明細を追加する（税込単価 1,100 円、税抜単価 1,000 円）。
         *
         * @param productId 商品 ID
         * @param variantId バリアント ID
         * @param quantity  数量
         * @return this
         */
        public OrderBuilder line(UUID productId, UUID variantId, int quantity) {
            lines.add(new Line(productId, variantId, quantity));
            return this;
        }

        /**
         * 写しを入れる。総額は明細の税込小計 ＋ 送料、消費税は税率 10% の内税（切り捨て）。
         *
         * @return 注文 ID
         */
        public UUID insert() {
            long items = lines.stream().mapToLong(line -> 1100L * line.quantity()).sum();
            long total = items + shippingFee;
            long shippingTax = shippingFee * 10 / 110;
            long taxTotal = total * 10 / 110;
            var orderedAt = noon(orderedOn);
            var shippedAt = shippedOn == null ? null : noon(shippedOn);
            dsl.insertInto(SALES_ORDER)
                    .set(SALES_ORDER.ORDER_ID, orderId)
                    .set(SALES_ORDER.ORDER_NUMBER, "%s-%06d".formatted(orderedOn.toString().replace("-", ""),
                            Math.floorMod(orderId.hashCode(), 1_000_000)))
                    .set(SALES_ORDER.MEMBER_ID, UUID.randomUUID())
                    .set(SALES_ORDER.ORDERED_AT, orderedAt)
                    .set(SALES_ORDER.ORDERED_ON, orderedOn)
                    .set(SALES_ORDER.STATUS, status)
                    .set(SALES_ORDER.SHIPPED_AT, shippedAt)
                    .set(SALES_ORDER.SHIPPED_ON, shippedOn)
                    .set(SALES_ORDER.DELIVERY_STATUS, shippedOn == null ? null : "SHIPPED")
                    .set(SALES_ORDER.POSTAL_CODE, "1000001")
                    .set(SALES_ORDER.PREFECTURE_CODE, prefectureCode)
                    .set(SALES_ORDER.BUYER_AGE, age == null ? null : age.shortValue())
                    .set(SALES_ORDER.BUYER_GENDER, gender)
                    .set(SALES_ORDER.TOTAL_AMOUNT, total)
                    .set(SALES_ORDER.SHIPPING_FEE, shippingFee)
                    .set(SALES_ORDER.TAX_TOTAL, taxTotal)
                    .set(SALES_ORDER.SHIPPING_TAX, shippingTax)
                    .set(SALES_ORDER.REFRESHED_AT, orderedAt)
                    .execute();
            for (int i = 0; i < lines.size(); i++) {
                var line = lines.get(i);
                dsl.insertInto(SALES_ORDER_LINE)
                        .set(SALES_ORDER_LINE.ORDER_ID, orderId)
                        .set(SALES_ORDER_LINE.LINE_NO, (short) (i + 1))
                        .set(SALES_ORDER_LINE.PRODUCT_ID, line.productId())
                        .set(SALES_ORDER_LINE.VARIANT_ID, line.variantId())
                        .set(SALES_ORDER_LINE.PRODUCT_CODE, "P-TEST")
                        .set(SALES_ORDER_LINE.VARIANT_CODE, "P-TEST-" + (i + 1))
                        .set(SALES_ORDER_LINE.PRODUCT_NAME, "テストの商品")
                        .set(SALES_ORDER_LINE.AXIS_VALUES, JSONB.valueOf("[]"))
                        .set(SALES_ORDER_LINE.QUANTITY, line.quantity())
                        .set(SALES_ORDER_LINE.RATE_PERCENT, BigDecimal.TEN)
                        .set(SALES_ORDER_LINE.SUBTOTAL_INCL_TAX, 1100L * line.quantity())
                        .set(SALES_ORDER_LINE.SUBTOTAL_EXCL_TAX, 1000L * line.quantity())
                        .execute();
            }
            return orderId;
        }
    }

    /**
     * 日本時間の正午
     */
    private static Instant noon(LocalDate date) {
        return date.atTime(LocalTime.NOON).atZone(JAPAN).toInstant();
    }

    /**
     * 明細
     */
    private record Line(UUID productId, UUID variantId, int quantity) {
    }
}

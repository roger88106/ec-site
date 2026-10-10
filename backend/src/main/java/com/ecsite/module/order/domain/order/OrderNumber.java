package com.ecsite.module.order.domain.order;

import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * 注文番号（BR-10）。注文日（日本時間）の 8 桁、ハイフン、ランダムな数字 6 桁（例：20261008-482915）。<br />
 * 識別子ではなく属性。日ごとの注文数を推測させないよう、連番にしない（plan 002 第 4 節）。
 *
 * @param value 注文番号
 */
@ValueObject
public record OrderNumber(String value) {

    /**
     * 注文番号の形
     */
    private static final Pattern FORMAT = Pattern.compile("\\d{8}-\\d{6}");

    /**
     * 注文日の書式（日本時間）
     */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("Asia/Tokyo"));

    /**
     * ランダムな部分の値の数（000000〜999999）
     */
    private static final int RANDOM_BOUND = 1_000_000;

    /**
     * コンストラクタ。形の違う値は、プログラムの誤りとして拒否する。
     *
     * @param value 注文番号
     */
    public OrderNumber(@NonNull String value) {
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("注文番号の形が違います: " + value);
        }
        this.value = value;
    }

    /**
     * 注文番号を作る。
     *
     * @param value 注文番号
     * @return 注文番号
     */
    public static OrderNumber of(String value) {
        return new OrderNumber(value);
    }

    /**
     * 注文日時から注文番号を作る。重複したら、呼ぶ側が作り直す。
     *
     * @param orderedAt 注文日時
     * @param random    乱数
     * @return 注文番号
     */
    public static OrderNumber generate(@NonNull Instant orderedAt, @NonNull RandomGenerator random) {
        return new OrderNumber("%s-%06d".formatted(DATE.format(orderedAt), random.nextInt(RANDOM_BOUND)));
    }
}

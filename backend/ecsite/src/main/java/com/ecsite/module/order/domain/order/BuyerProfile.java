package com.ecsite.module.order.domain.order;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * 注文時の年齢と性別（docs/backend/modules/order.md「注文時の年齢と性別」）。<br />
 * 注文の成立のときに、会員の token の生年月日と性別から求める。生年月日は持たない（レポートに個人を特定できる情報を出さないため）。
 *
 * @param age    注文日（日本時間）の時点の満年齢。分からなければ null
 * @param gender 性別。分からなければ null
 */
@ValueObject
public record BuyerProfile(@Nullable Integer age, @Nullable Gender gender) {

    /**
     * 年齢を記録する上限。これより前の生年月日は、ありえないものとして不明にする
     */
    static final int MAX_AGE = 120;

    /**
     * 年齢・性別とも不明
     */
    public static final BuyerProfile UNKNOWN = new BuyerProfile(null, null);

    /**
     * 注文日の区切り
     */
    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

    /**
     * 生年月日の形（{@code YYYY-MM-DD}。2 月 30 日のような、ない日は拒む）
     */
    private static final DateTimeFormatter BIRTHDATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * token の生年月日と性別から求める。
     *
     * @param birthdate 生年月日（token の値）
     * @param gender    性別（token の値）
     * @param orderedAt 注文日時
     * @return 注文時の年齢と性別
     */
    public static BuyerProfile of(@Nullable String birthdate, @Nullable String gender, @NonNull Instant orderedAt) {
        return new BuyerProfile(ageOf(birthdate, LocalDate.ofInstant(orderedAt, JAPAN)), Gender.fromClaim(gender));
    }

    /**
     * 注文日の満年齢を求める。生年月日がない、形が正しくない、注文日より後、上限の年齢より前なら null。<br />
     * 2 月 29 日生まれは、うるう年でない年は 3 月 1 日に年を取る（{@link Period} の数え方）。
     */
    private static Integer ageOf(String birthdate, LocalDate orderDate) {
        if (birthdate == null) {
            return null;
        }
        LocalDate born;
        try {
            born = LocalDate.parse(birthdate, BIRTHDATE);
        } catch (DateTimeParseException e) {
            return null;
        }
        if (born.isAfter(orderDate) || born.isBefore(orderDate.minusYears(MAX_AGE))) {
            return null;
        }
        return Period.between(born, orderDate).getYears();
    }
}

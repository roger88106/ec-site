package com.ecsite.module.reporting.application;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.application.model.SegmentTotal;
import com.ecsite.module.reporting.application.model.query.CustomerBreakdownQuery;
import com.ecsite.module.reporting.application.model.view.AgeGroupSales;
import com.ecsite.module.reporting.application.model.view.CustomerBreakdownReport;
import com.ecsite.module.reporting.application.model.view.GenderSales;
import com.ecsite.module.reporting.application.model.view.PrefectureSales;
import com.ecsite.module.reporting.application.port.RegionDirectory;
import com.ecsite.module.reporting.application.port.ReportQueryRepository;
import com.ecsite.module.reporting.domain.sales.AgeGroup;
import com.ecsite.module.reporting.domain.sales.ReportGender;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 地域・年齢層・性別のクエリサービス
 */
@Service
@RequiredArgsConstructor
public class CustomerBreakdownQueryService {

    /**
     * 都道府県が不明な組をまとめるキー（都道府県のコードは空にならないので、他のコードと重ならない）
     */
    private static final String UNKNOWN_PREFECTURE = "";

    /**
     * 都道府県の並べ替えで、不明を最後に置くキー（実在のコードは 01〜47）
     */
    private static final String UNKNOWN_PREFECTURE_SORT_KEY = "99";

    /**
     * 写しを集計する問い合わせ
     */
    private final ReportQueryRepository queryRepository;

    /**
     * 地域の腐敗防止層
     */
    private final RegionDirectory regionDirectory;

    /**
     * 地域・年齢層・性別を求める。
     *
     * @param query 条件
     * @return 3 つの分け方の売上
     */
    @Transactional(readOnly = true)
    public CustomerBreakdownReport breakdown(@NonNull CustomerBreakdownQuery query) {
        // 都道府県・年齢・性別の組ごとの集計 ※注文は 1 つの組にだけ入るので、組の注文件数を合算しても、同じ注文を 2 回数えない
        var segments = queryRepository.segmentSales(query.period(), query.basis(), query.productId(), query.excludeTax(),
                query.excludeShipping());

        // 年齢層と性別へのまとめ ※売れなかった区分も 0 として、すべての区分を並べる
        var ageGroups = forEveryValue(AgeGroup.values(), group(segments, segment -> AgeGroup.of(segment.age())),
                (group, total) -> new AgeGroupSales(group, total.amount(), total.orders()));
        var genders = forEveryValue(ReportGender.values(), group(segments, segment -> ReportGender.of(segment.gender())),
                (gender, total) -> new GenderSales(gender, total.amount(), total.orders()));

        // 商品を選んだときは、指定によらず送料を除いている
        return new CustomerBreakdownReport(prefectureSales(segments), ageGroups, genders,
                query.excludeShipping() || query.productId() != null);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 都道府県の売上を求める。
     *
     * <p>売れた都道府県だけを、コードの順に並べ、不明を最後に置く。名前は地域から引く。
     */
    private List<PrefectureSales> prefectureSales(List<SegmentTotal> segments) {
        var names = regionDirectory.prefectureNames();
        return group(segments, segment -> Objects.requireNonNullElse(segment.prefectureCode(), UNKNOWN_PREFECTURE)).entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> isUnknown(entry.getKey()) ? UNKNOWN_PREFECTURE_SORT_KEY : entry.getKey()))
                .map(entry -> new PrefectureSales(isUnknown(entry.getKey()) ? null : entry.getKey(),
                        names.get(entry.getKey()), entry.getValue().amount(), entry.getValue().orders()))
                .toList();
    }

    /**
     * 都道府県のコードが、不明を表す値か。
     */
    private static boolean isUnknown(String prefectureKey) {
        return prefectureKey.equals(UNKNOWN_PREFECTURE);
    }

    /**
     * すべての区分について、売上を作る。
     *
     * <p>売れなかった区分は 0 にする。
     */
    private static <K, R> List<R> forEveryValue(K[] values, Map<K, Total> totals, BiFunction<K, Total, R> toSales) {
        return Arrays.stream(values)
                .map(value -> toSales.apply(value, totals.getOrDefault(value, Total.ZERO)))
                .toList();
    }

    /**
     * 組を、分け方の値ごとに合計する。
     */
    private static <K> Map<K, Total> group(List<SegmentTotal> segments, Function<SegmentTotal, K> key) {
        return segments.stream().collect(Collectors.toMap(key, segment -> new Total(segment.amount(), segment.orders()),
                Total::plus));
    }

    /**
     * 売上額と注文件数の合計
     */
    private record Total(Money amount, long orders) {

        /**
         * 売上額と注文件数が 0 の合計
         */
        static final Total ZERO = new Total(Money.ZERO, 0);

        /**
         * 2 つの合計を加える。
         *
         * @param other 加える合計
         * @return 加えた合計
         */
        Total plus(Total other) {
            return new Total(amount.plus(other.amount), orders + other.orders);
        }
    }
}

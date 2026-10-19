package com.ecsite.module.reporting.application;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.application.model.query.CustomerBreakdownQuery;
import com.ecsite.module.reporting.application.model.view.AgeGroupSales;
import com.ecsite.module.reporting.application.model.view.CustomerBreakdownReport;
import com.ecsite.module.reporting.application.model.view.GenderSales;
import com.ecsite.module.reporting.application.model.view.PrefectureSales;
import com.ecsite.module.reporting.application.port.ReportQueryRepository.SegmentTotal;
import com.ecsite.module.reporting.application.port.ReportQueryRepository;
import com.ecsite.module.reporting.domain.AgeGroup;
import com.ecsite.module.reporting.domain.ReportGender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 地域・年齢層・性別（docs/backend/modules/reporting.md「地域・年齢層・性別」）。<br />
 * 都道府県・年齢・性別の組ごとに SQL で集計し、3 つの分け方へのまとめは Java で行う。
 * 注文は 1 つの組にだけ入るので、組の注文件数を足し合わせても、同じ注文を 2 回数えない。
 */
@Service
@RequiredArgsConstructor
public class CustomerBreakdownQueryService {

    /**
     * 写しを集計する問い合わせ
     */
    private final ReportQueryRepository queryRepository;

    /**
     * 地域・年齢層・性別を求める。
     *
     * @param query 条件
     * @return 3 つの分け方の売上
     */
    @Transactional(readOnly = true)
    public CustomerBreakdownReport breakdown(CustomerBreakdownQuery query) {
        var segments = queryRepository.segmentSales(query.period(), query.basis(), query.productId(), query.excludeTax(),
                query.excludeShipping());

        // 都道府県：売れた都道府県だけを、コードの順に。不明は最後
        var prefectures = group(segments, segment -> Objects.requireNonNullElse(segment.prefectureCode(), "")).entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().isEmpty() ? "99" : entry.getKey()))
                .map(entry -> new PrefectureSales(entry.getKey().isEmpty() ? null : entry.getKey(),
                        entry.getValue().amount(), entry.getValue().orders()))
                .toList();

        // 年齢層と性別：すべての区分を、売れなかったものも 0 として
        var byAge = group(segments, segment -> AgeGroup.of(segment.age()));
        var ageGroups = Arrays.stream(AgeGroup.values())
                .map(group -> {
                    var total = byAge.getOrDefault(group, Total.ZERO);
                    return new AgeGroupSales(group, total.amount(), total.orders());
                })
                .toList();
        var byGender = group(segments, segment -> ReportGender.of(segment.gender()));
        var genders = Arrays.stream(ReportGender.values())
                .map(gender -> {
                    var total = byGender.getOrDefault(gender, Total.ZERO);
                    return new GenderSales(gender, total.amount(), total.orders());
                })
                .toList();

        return new CustomerBreakdownReport(prefectures, ageGroups, genders,
                query.excludeShipping() || query.productId() != null);
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
         * 0
         */
        static final Total ZERO = new Total(Money.ZERO, 0);

        /**
         * 2 つの合計を足す。
         *
         * @param other 足す合計
         * @return 足した合計
         */
        Total plus(Total other) {
            return new Total(amount.plus(other.amount), orders + other.orders);
        }
    }
}

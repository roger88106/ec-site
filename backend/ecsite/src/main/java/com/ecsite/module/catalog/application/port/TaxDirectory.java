package com.ecsite.module.catalog.application.port;

import com.ecsite.common.model.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 税の腐敗防止層（constitution 4.1）。<br />
 * 税モジュールの API を、商品カタログが必要とする形で包む。税モジュールの型を商品カタログの内部に持ち込まない。
 */
public interface TaxDirectory {

    /**
     * 税区分が、指定した時点で有効な税率を持つかを返す（AC-6）。存在しない税区分は持たないとみなす。
     *
     * @param taxCategoryId 税区分 ID
     * @param instant       時点
     * @return 有効な税率を持てば true
     */
    boolean hasRateAt(UUID taxCategoryId, Instant instant);

    /**
     * 指定した時点の税込価格を返す（AC-19）。
     *
     * @param taxCategoryId 税区分 ID
     * @param priceExclTax  税抜価格
     * @param instant       時点
     * @return 税込価格。その時点で有効な税率がなければ空
     */
    Optional<Money> priceInclTaxAt(UUID taxCategoryId, Money priceExclTax, Instant instant);

    /**
     * すべての税区分について、指定した時点で有効な率を返す。C 側の一覧の SQL に埋め込むために使う。<br />
     * 税率の一覧はインスタンス内で 30 秒キャッシュする（docs/modules/catalog.md）。
     *
     * @param instant 時点
     * @return 税区分 ID → 率（%）。有効な税率のない税区分は含まない
     */
    Map<UUID, BigDecimal> ratesAt(Instant instant);
}

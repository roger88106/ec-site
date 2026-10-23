package com.ecsite.module.catalog.application;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.api.StoreProductDetail;
import com.ecsite.module.catalog.application.model.query.StoreProductListQuery;
import com.ecsite.module.catalog.application.model.view.StoreProductSummaryView;
import com.ecsite.module.catalog.application.port.ProductSearch;
import com.ecsite.module.catalog.application.port.StoreProductQueryRepository;
import com.ecsite.module.catalog.application.port.TaxDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * C 側向けの商品照会のクエリサービス。<br />
 * 表示時刻は呼ばれた時点の現在時刻。税率の一覧はキャッシュされる（最大 30 秒）が、商品・価格・公開状態はキャッシュしない。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreProductQueryService {

    /**
     * C 側向けの商品照会のクエリリポジトリ
     */
    private final StoreProductQueryRepository storeProductQueryRepository;

    /**
     * 商品検索
     */
    private final ProductSearch productSearch;

    /**
     * 税の腐敗防止層
     */
    private final TaxDirectory taxDirectory;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * C 側の商品一覧を取得する。
     *
     * @param query   絞り込みと並び順
     * @param request ページの指定
     * @return 商品の一覧
     */
    public PageResult<StoreProductSummaryView> listProducts(StoreProductListQuery query, PageRequest request) {
        var now = clock.instant();
        return storeProductQueryRepository.findVisible(query, request, now, taxDirectory.ratesAt(now));
    }

    /**
     * キーワードで商品を探す。
     *
     * @param keyword キーワード
     * @param request ページの指定
     * @return 検索結果（新着順）
     */
    public PageResult<StoreProductSummaryView> searchProducts(String keyword, PageRequest request) {
        var now = clock.instant();
        return productSearch.search(keyword, request, now, taxDirectory.ratesAt(now));
    }

    /**
     * C 側の商品の詳細を取得する。
     *
     * @param id 商品 ID
     * @return 商品の詳細。表示されない商品なら空（存在しない商品と同じ扱い）
     */
    public Optional<StoreProductDetail> findProduct(UUID id) {
        var now = clock.instant();
        return storeProductQueryRepository.findVisibleDetail(id, now, taxDirectory.ratesAt(now));
    }
}

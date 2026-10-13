package com.ecsite.module.catalog.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.value.Money;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.application.model.query.ProductListQuery;
import com.ecsite.module.catalog.application.model.view.ProductDetailView;
import com.ecsite.module.catalog.application.model.view.ProductSummaryView;
import com.ecsite.module.catalog.application.port.ProductQueryRepository;
import com.ecsite.module.catalog.application.port.TaxDirectory;
import com.ecsite.module.catalog.domain.product.ProductActions;
import com.ecsite.module.catalog.domain.product.PublicationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 商品のクエリサービス
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {

    /**
     * 商品クエリリポジトリ
     */
    private final ProductQueryRepository productQueryRepository;

    /**
     * 税の腐敗防止層
     */
    private final TaxDirectory taxDirectory;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 商品の詳細を取得する。税込価格は現時点の税率で計算し、できる操作は現在時刻で決める。
     *
     * @param id 商品 ID
     * @return 商品の詳細
     */
    public ProductDetailView getProduct(UUID id) {
        var view = productQueryRepository.findDetailById(id)
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
        var now = clock.instant();
        var priceInclTax = taxDirectory.priceInclTaxAt(view.taxCategoryId(), Money.of(view.priceExclTax()), now)
                .map(Money::yen)
                .orElse(null);
        var actions = ProductActions.of(PublicationStatus.valueOf(view.status()), view.firstShownAt(), view.axes().size(),
                view.variants().size(), now);
        return view.withCurrentState(priceInclTax, actions);
    }

    /**
     * B 側の商品一覧を取得する。
     *
     * @param query   絞り込みの条件
     * @param request ページの指定
     * @return 商品の一覧
     */
    public PageResult<ProductSummaryView> listProducts(ProductListQuery query, PageRequest request) {
        return productQueryRepository.findSummaries(query, request);
    }
}

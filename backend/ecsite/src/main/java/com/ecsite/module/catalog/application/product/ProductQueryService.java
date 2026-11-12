package com.ecsite.module.catalog.application.product;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.catalog.application.model.query.ProductListQuery;
import com.ecsite.module.catalog.application.model.view.ProductDetailView;
import com.ecsite.module.catalog.application.model.view.ProductSummaryView;
import com.ecsite.module.catalog.application.port.ProductQueryRepository;
import com.ecsite.module.catalog.application.port.TaxDirectory;
import com.ecsite.module.catalog.domain.product.ProductActions;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
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
     * 商品の取り出し（バージョンの確認を含む）
     */
    private final ProductLoader productLoader;

    /**
     * 画像を加えられることを確認する（商品がある、バージョンが一致する、枚数が上限に達していない）。
     *
     * @param productId 商品 ID
     * @param version   取得時の商品のバージョン
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     * @throws BusinessException 画像が上限に達している（{@code TOO_MANY_PRODUCT_IMAGES}）
     */
    public void verifyCanAddImage(@NonNull UUID productId, long version) {
        productLoader.get(productId, version).verifyCanAddImage();
    }

    /**
     * 画像を加えられることを、バージョンなしで確認する（商品がある、枚数が上限に達していない）。
     *
     * @param productId 商品 ID
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     * @throws BusinessException 画像が上限に達している（{@code TOO_MANY_PRODUCT_IMAGES}）
     */
    public void verifyCanAddImage(@NonNull UUID productId) {
        productLoader.get(productId).verifyCanAddImage();
    }

    /**
     * 商品の詳細を取得する。
     *
     * <p>税込価格は現時点の税率で計算し、できる操作は現在時刻で決める。
     *
     * @param id 商品 ID
     * @return 商品の詳細
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     */
    public ProductDetailView getProduct(@NonNull UUID id) {
        // 商品の詳細の取得
        var view = productQueryRepository.findDetailById(id)
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
        // 今の税込価格と、できる操作の設定
        var now = clock.instant();
        var priceInclTax = taxDirectory.priceInclTaxAt(view.taxCategoryId(), view.priceExclTax(), now).orElse(null);
        var actions = ProductActions.of(view.status(), view.firstShownAt(), view.axes().size(),
                view.variants().size(), now);
        return view.withCurrentState(priceInclTax, actions);
    }

    /**
     * 商品の詳細を、今の状態（税込価格、できる操作）なしで探す。
     *
     * @param id 商品 ID
     * @return 商品の詳細。なければ空
     */
    public Optional<ProductDetailView> findProduct(@NonNull UUID id) {
        return productQueryRepository.findDetailById(id);
    }

    /**
     * B 側の商品一覧を取得する。
     *
     * @param query   絞り込みの条件
     * @param request ページの指定
     * @return 商品の一覧
     */
    public PageResult<ProductSummaryView> listProducts(@NonNull ProductListQuery query, @NonNull PageRequest request) {
        return productQueryRepository.findSummaries(query, request);
    }
}

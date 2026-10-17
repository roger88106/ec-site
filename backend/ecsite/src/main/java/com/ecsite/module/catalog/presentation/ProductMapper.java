package com.ecsite.module.catalog.presentation;

import com.ecsite.api.admin.model.AddVariantRequest;
import com.ecsite.api.admin.model.CreateProductRequest;
import com.ecsite.api.admin.model.ImageUpload;
import com.ecsite.api.admin.model.ProductActions;
import com.ecsite.api.admin.model.ProductDetail;
import com.ecsite.api.admin.model.ProductImage;
import com.ecsite.api.admin.model.ProductSummary;
import com.ecsite.api.admin.model.ProductSummaryPage;
import com.ecsite.api.admin.model.PublicationStatus;
import com.ecsite.api.admin.model.ReorderVariantsRequest;
import com.ecsite.api.admin.model.SetPublicationPeriodRequest;
import com.ecsite.api.admin.model.UpdateProductRequest;
import com.ecsite.api.admin.model.Variant;
import com.ecsite.common.model.value.Money;
import com.ecsite.common.paging.PageResult;
import com.ecsite.common.presentation.ApiDateTimes;
import com.ecsite.module.catalog.application.model.command.AddVariantCommand;
import com.ecsite.module.catalog.application.model.command.ChangePublicationPeriodCommand;
import com.ecsite.module.catalog.application.model.command.CreateProductCommand;
import com.ecsite.module.catalog.application.model.command.ReorderVariantsCommand;
import com.ecsite.module.catalog.application.model.command.UpdateProductCommand;
import com.ecsite.module.catalog.application.model.query.ProductListQuery;
import com.ecsite.module.catalog.application.model.view.ImageUploadView;
import com.ecsite.module.catalog.application.model.view.ProductDetailView;
import com.ecsite.module.catalog.application.model.view.ProductSummaryView;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 商品 API のリクエスト・レスポンスと、アプリケーション層のモデルの変換
 */
@Component
class ProductMapper {

    /**
     * 商品の登録コマンドに変換する。<br />
     * 所属カテゴリと軸は LinkedHashSet で受け取るので、送られた順を保つ。
     *
     * @param request 商品の登録リクエスト
     * @return 商品の登録コマンド
     */
    CreateProductCommand toCreateProductCommand(CreateProductRequest request) {
        return new CreateProductCommand(
                request.getProductCode(),
                request.getName(),
                request.getDescription(),
                request.getTaxCategoryId(),
                Money.of(request.getPriceExclTax()),
                List.copyOf(request.getCategoryIds()),
                List.copyOf(request.getAxes()));
    }

    /**
     * 商品の編集コマンドに変換する。
     *
     * @param productId 商品 ID
     * @param request   商品の編集リクエスト
     * @return 商品の編集コマンド
     */
    UpdateProductCommand toUpdateProductCommand(UUID productId, UpdateProductRequest request) {
        return new UpdateProductCommand(
                productId,
                request.getName(),
                request.getDescription(),
                request.getTaxCategoryId(),
                Money.of(request.getPriceExclTax()),
                List.copyOf(request.getCategoryIds()),
                request.getVersion());
    }

    /**
     * バリアントの登録コマンドに変換する。
     *
     * @param productId 商品 ID
     * @param request   バリアントの登録リクエスト
     * @return バリアントの登録コマンド
     */
    AddVariantCommand toAddVariantCommand(UUID productId, AddVariantRequest request) {
        return new AddVariantCommand(productId, request.getVariantCode(), List.copyOf(request.getAxisValues()),
                request.getVersion());
    }

    /**
     * バリアントの並び順の変更コマンドに変換する。
     *
     * @param productId 商品 ID
     * @param request   バリアントの並び順の変更リクエスト
     * @return バリアントの並び順の変更コマンド
     */
    ReorderVariantsCommand toReorderVariantsCommand(UUID productId, ReorderVariantsRequest request) {
        // variantIds は LinkedHashSet で受け取るので、送られた順を保つ
        return new ReorderVariantsCommand(productId, List.copyOf(request.getVariantIds()), request.getVersion());
    }

    /**
     * 公開期間の変更コマンドに変換する。日時を省略・null にしたら、その側は限りがない。
     *
     * @param productId 商品 ID
     * @param request   公開期間の設定リクエスト
     * @return 公開期間の変更コマンド
     */
    ChangePublicationPeriodCommand toChangePublicationPeriodCommand(UUID productId, SetPublicationPeriodRequest request) {
        return new ChangePublicationPeriodCommand(productId,
                ApiDateTimes.toInstant(request.getPublishStart().orElse(null)),
                ApiDateTimes.toInstant(request.getPublishEnd().orElse(null)),
                request.getVersion());
    }

    /**
     * 商品の詳細のレスポンスに変換する。
     *
     * @param view 商品の詳細のビュー
     * @return 商品の詳細のレスポンス
     */
    ProductDetail toProductDetail(ProductDetailView view) {
        return new ProductDetail()
                .id(view.id())
                .productCode(view.productCode())
                .name(view.name())
                .description(view.description())
                .taxCategoryId(view.taxCategoryId())
                .priceExclTax(view.priceExclTax())
                .priceInclTax(view.priceInclTax())
                .categoryIds(view.categoryIds())
                .axes(view.axes())
                .status(PublicationStatus.fromValue(view.status()))
                .images(view.images().stream().map(ProductMapper::toImage).toList())
                .variants(view.variants().stream().map(ProductMapper::toVariant).toList())
                .actions(toActions(view.actions()))
                .version(view.version())
                .createdAt(ApiDateTimes.toOffsetDateTime(view.createdAt()))
                .updatedAt(ApiDateTimes.toOffsetDateTime(view.updatedAt()))
                .firstShownAt(ApiDateTimes.toOffsetDateTime(view.firstShownAt()))
                .publishStart(ApiDateTimes.toOffsetDateTime(view.publishStart()))
                .publishEnd(ApiDateTimes.toOffsetDateTime(view.publishEnd()));
    }

    /**
     * B 側の商品一覧のレスポンスに変換する。
     *
     * @param page 商品の一覧
     * @return 商品の一覧のレスポンス
     */
    ProductSummaryPage toProductSummaryPage(PageResult<ProductSummaryView> page) {
        return new ProductSummaryPage()
                .page(page.page())
                .size(page.size())
                .totalItems(page.totalItems())
                .totalPages(page.totalPages())
                .items(page.items().stream().map(ProductMapper::toSummary).toList());
    }

    /**
     * 画像のアップロードの受付のレスポンスに変換する。
     *
     * @param view 画像のアップロードの受付
     * @return 画像のアップロードの受付のレスポンス
     */
    ImageUpload toImageUpload(ImageUploadView view) {
        return new ImageUpload()
                .uploadId(view.uploadId())
                .uploadUrl(view.uploadUrl())
                .headers(view.headers())
                .expiresAt(ApiDateTimes.toOffsetDateTime(view.expiresAt()));
    }

    /**
     * B 側の商品一覧の条件を作る。
     *
     * @param code       商品品番またはバリアント品番の前方一致
     * @param name       商品名の部分一致
     * @param status     公開状態
     * @param categoryId カテゴリ ID（子孫のカテゴリを含む）
     * @return 商品一覧の条件
     */
    ProductListQuery toProductListQuery(String code, String name, PublicationStatus status, UUID categoryId) {
        return new ProductListQuery(code, name, status == null ? null : status.getValue(), categoryId);
    }

// ============================== プライベートメソッド ==============================

    /**
     * バリアントをレスポンスに変換する。画像 ID は指定がなければ null のまま返す。
     */
    private static Variant toVariant(ProductDetailView.VariantView view) {
        return new Variant()
                .id(view.id())
                .variantCode(view.variantCode())
                .axisValues(view.axisValues())
                .salesSuspended(view.salesSuspended())
                .imageId(view.imageId());
    }

    /**
     * 商品画像をレスポンスに変換する。
     */
    private static ProductImage toImage(ProductDetailView.ImageView view) {
        return new ProductImage()
                .id(view.id())
                .url(view.url());
    }

    /**
     * 商品一覧の 1 件をレスポンスに変換する。
     */
    private static ProductSummary toSummary(ProductSummaryView view) {
        return new ProductSummary()
                .id(view.id())
                .productCode(view.productCode())
                .name(view.name())
                .status(PublicationStatus.fromValue(view.status()))
                .variantCount(view.variantCount());
    }

    /**
     * できる操作をレスポンスに変換する。
     */
    private static ProductActions toActions(com.ecsite.module.catalog.domain.product.ProductActions actions) {
        return new ProductActions()
                .publish(actions.publish())
                .unpublish(actions.unpublish())
                .discontinue(actions.discontinue())
                .delete(actions.delete())
                .deleteVariants(actions.deleteVariants())
                .addVariant(actions.addVariant());
    }
}

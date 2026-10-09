package com.ecsite.module.catalog.presentation;

import com.ecsite.api.admin.model.AddVariantRequest;
import com.ecsite.api.admin.model.CreateProductRequest;
import com.ecsite.api.admin.model.ProductDetail;
import com.ecsite.api.admin.model.PublicationStatus;
import com.ecsite.api.admin.model.ReorderVariantsRequest;
import com.ecsite.api.admin.model.UpdateProductRequest;
import com.ecsite.api.admin.model.Variant;
import com.ecsite.module.catalog.application.model.command.AddVariantCommand;
import com.ecsite.module.catalog.application.model.command.CreateProductCommand;
import com.ecsite.module.catalog.application.model.command.ReorderVariantsCommand;
import com.ecsite.module.catalog.application.model.command.UpdateProductCommand;
import com.ecsite.module.catalog.application.model.view.ProductDetailView;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
                request.getPriceExclTax(),
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
                request.getPriceExclTax(),
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
     * 商品の詳細のレスポンスに変換する。
     *
     * @param view 商品の詳細のビュー
     * @return 商品の詳細のレスポンス
     */
    ProductDetail toProductDetail(ProductDetailView view) {
        return new ProductDetail(
                view.id(),
                view.productCode(),
                view.name(),
                view.description(),
                view.taxCategoryId(),
                view.priceExclTax(),
                view.priceInclTax(),
                view.categoryIds(),
                view.axes(),
                PublicationStatus.fromValue(view.status()),
                view.variants().stream().map(ProductMapper::toVariant).toList(),
                view.version(),
                toOffsetDateTime(view.createdAt()),
                toOffsetDateTime(view.updatedAt()))
                .firstShownAt(toOffsetDateTime(view.firstShownAt()))
                .publishStart(toOffsetDateTime(view.publishStart()))
                .publishEnd(toOffsetDateTime(view.publishEnd()));
    }

// ============================== プライベートメソッド ==============================

    private static Variant toVariant(ProductDetailView.VariantView view) {
        return new Variant(view.id(), view.variantCode(), view.axisValues(), view.salesSuspended());
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}

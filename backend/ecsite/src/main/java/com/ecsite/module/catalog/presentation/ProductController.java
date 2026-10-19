package com.ecsite.module.catalog.presentation;

import com.ecsite.api.admin.ProductApi;
import com.ecsite.api.admin.model.AddProductImageRequest;
import com.ecsite.api.admin.model.AddVariantRequest;
import com.ecsite.api.admin.model.CreateImageUploadRequest;
import com.ecsite.api.admin.model.CreateProductRequest;
import com.ecsite.api.admin.model.CreatedId;
import com.ecsite.api.admin.model.ImageUpload;
import com.ecsite.api.admin.model.ProductDetail;
import com.ecsite.api.admin.model.ProductSummaryPage;
import com.ecsite.api.admin.model.PublicationStatus;
import com.ecsite.api.admin.model.ReorderProductImagesRequest;
import com.ecsite.api.admin.model.ReorderVariantsRequest;
import com.ecsite.api.admin.model.SetPublicationPeriodRequest;
import com.ecsite.api.admin.model.SetVariantImageRequest;
import com.ecsite.api.admin.model.UpdateProductRequest;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.presentation.CreatedResponses;
import com.ecsite.module.catalog.application.ProductCommandService;
import com.ecsite.module.catalog.application.ProductImageService;
import com.ecsite.module.catalog.application.ProductPublicationService;
import com.ecsite.module.catalog.application.ProductQueryService;
import com.ecsite.module.catalog.application.VariantCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 商品のコントローラー（B 側。admin.yaml の tag: product）。
 */
@RestController
@RequiredArgsConstructor
class ProductController implements ProductApi {

    /**
     * 商品コマンドサービス
     */
    private final ProductCommandService commandService;

    /**
     * バリアントのコマンドサービス
     */
    private final VariantCommandService variantCommandService;

    /**
     * 公開のコマンドサービス
     */
    private final ProductPublicationService productPublicationService;

    /**
     * 商品クエリサービス
     */
    private final ProductQueryService queryService;

    /**
     * 商品画像のアプリケーションサービス
     */
    private final ProductImageService imageService;

    /**
     * 商品マッパー
     */
    private final ProductMapper mapper;

// ============================== GET ==============================

    /**
     * 商品の一覧・絞り込み
     *
     * @param code       商品品番またはバリアント品番の前方一致
     * @param name       商品名の部分一致
     * @param status     公開状態
     * @param categoryId カテゴリ ID（子孫のカテゴリを含む）
     * @param page       ページ番号
     * @param size       1 ページの件数
     * @return Http 200 : {@link ProductSummaryPage}
     */
    @Override
    public ResponseEntity<ProductSummaryPage> listProducts(String code, String name, PublicationStatus status,
                                                           UUID categoryId, Integer page, Integer size) {
        var query = mapper.toProductListQuery(code, name, status, categoryId);
        var result = queryService.listProducts(query, PageRequest.of(page, size));
        return ResponseEntity.ok(mapper.toProductSummaryPage(result));
    }

    /**
     * 商品の詳細
     *
     * @param productId 商品 ID
     * @return Http 200 : {@link ProductDetail}
     */
    @Override
    public ResponseEntity<ProductDetail> getProduct(UUID productId) {
        return ResponseEntity.ok(mapper.toProductDetail(queryService.getProduct(productId)));
    }

// ============================== POST ==============================

    /**
     * 商品の登録
     *
     * @param request 商品の登録リクエスト
     * @return Http 201 : {@link CreatedId}
     */
    @Override
    public ResponseEntity<CreatedId> createProduct(CreateProductRequest request) {
        // 商品登録
        UUID id = commandService.createProduct(mapper.toCreateProductCommand(request));

        // ロケーション：登録した商品の URL
        return CreatedResponses.admin(id);
    }

    /**
     * バリアントの登録。Location は、登録したバリアントの URL（削除などの操作の URL）
     *
     * @param productId 商品 ID
     * @param request   バリアントの登録リクエスト
     * @return Http 201 : {@link CreatedId}
     */
    @Override
    public ResponseEntity<CreatedId> addVariant(UUID productId, AddVariantRequest request) {
        UUID id = variantCommandService.addVariant(mapper.toAddVariantCommand(productId, request));
        return CreatedResponses.admin(id);
    }

// ============================== PUT ==============================

    /**
     * 商品の編集
     *
     * @param productId 商品 ID
     * @param request   商品の編集リクエスト
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> updateProduct(UUID productId, UpdateProductRequest request) {
        commandService.updateProduct(mapper.toUpdateProductCommand(productId, request));
        return ResponseEntity.noContent().build();
    }

    /**
     * 公開する。冪等
     *
     * @param productId 商品 ID
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> publishProduct(UUID productId) {
        productPublicationService.publishProduct(productId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 非公開にする。冪等
     *
     * @param productId 商品 ID
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> unpublishProduct(UUID productId) {
        productPublicationService.unpublishProduct(productId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 販売終了にする。冪等
     *
     * @param productId 商品 ID
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> discontinueProduct(UUID productId) {
        productPublicationService.discontinueProduct(productId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 公開期間の設定
     *
     * @param productId 商品 ID
     * @param request   公開期間の設定リクエスト
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> setPublicationPeriod(UUID productId, SetPublicationPeriodRequest request) {
        productPublicationService.changePublicationPeriod(mapper.toChangePublicationPeriodCommand(productId, request));
        return ResponseEntity.noContent().build();
    }

    /**
     * バリアントの表示順の変更
     *
     * @param productId 商品 ID
     * @param request   バリアントの並び順の変更リクエスト
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> reorderVariants(UUID productId, ReorderVariantsRequest request) {
        variantCommandService.reorderVariants(mapper.toReorderVariantsCommand(productId, request));
        return ResponseEntity.noContent().build();
    }

    /**
     * バリアントの販売停止。冪等
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> suspendVariant(UUID productId, UUID variantId) {
        variantCommandService.suspendVariant(productId, variantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * バリアントの販売停止の解除。冪等
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> resumeVariant(UUID productId, UUID variantId) {
        variantCommandService.resumeVariant(productId, variantId);
        return ResponseEntity.noContent().build();
    }

// ============================== 商品画像 ==============================

    /**
     * 画像のアップロードの受付
     *
     * @param productId 商品 ID
     * @param request   画像のアップロードの受付リクエスト
     * @return Http 201 : 受付（署名付き URL）
     */
    @Override
    public ResponseEntity<ImageUpload> createImageUpload(UUID productId, CreateImageUploadRequest request) {
        var upload = imageService.createUpload(productId, request.getContentType().getValue(), request.getContentLength());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toImageUpload(upload));
    }

    /**
     * 受け付けた画像の登録。商品の一部で単独の URL を持たないので、Location は返さない
     *
     * @param productId 商品 ID
     * @param request   画像の登録リクエスト
     * @return Http 201 : 画像 ID
     */
    @Override
    public ResponseEntity<CreatedId> addProductImage(UUID productId, AddProductImageRequest request) {
        var id = imageService.addImage(productId, request.getUploadId(), request.getVersion());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedId().id(id));
    }

    /**
     * 画像の並び順の変更
     *
     * @param productId 商品 ID
     * @param request   画像の並び順の変更リクエスト
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> reorderProductImages(UUID productId, ReorderProductImagesRequest request) {
        // imageIds は LinkedHashSet で受け取るので、送られた順を保つ
        imageService.reorderImages(productId, List.copyOf(request.getImageIds()), request.getVersion());
        return ResponseEntity.noContent().build();
    }

    /**
     * 画像の削除
     *
     * @param productId 商品 ID
     * @param imageId   画像 ID
     * @param version   取得時のバージョン
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> deleteProductImage(UUID productId, UUID imageId, Long version) {
        imageService.deleteImage(productId, imageId, version);
        return ResponseEntity.noContent().build();
    }

    /**
     * バリアントの画像の指定
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @param request   バリアントの画像の指定リクエスト
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> setVariantImage(UUID productId, UUID variantId, SetVariantImageRequest request) {
        imageService.setVariantImage(productId, variantId, request.getImageId().orElse(null), request.getVersion());
        return ResponseEntity.noContent().build();
    }

// ============================== DELETE ==============================

    /**
     * 商品の削除
     *
     * @param productId 商品 ID
     * @param version   取得時のバージョン
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> deleteProduct(UUID productId, Long version) {
        commandService.deleteProduct(productId, version);
        return ResponseEntity.noContent().build();
    }

    /**
     * バリアントの削除
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @param version   取得時の商品のバージョン
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> deleteVariant(UUID productId, UUID variantId, Long version) {
        variantCommandService.deleteVariant(productId, variantId, version);
        return ResponseEntity.noContent().build();
    }
}

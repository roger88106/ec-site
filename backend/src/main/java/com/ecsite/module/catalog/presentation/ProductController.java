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
import com.ecsite.module.catalog.application.ProductCommandService;
import com.ecsite.module.catalog.application.ProductQueryService;
import com.ecsite.module.catalog.application.model.query.ProductListQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

/**
 * 商品のコントローラー（B 側。admin.yaml の tag: product）。<br />
 * 後の段階で実装する endpoint は、実装するまで 501 を返す（docs/modules/catalog.md）。
 */
@RestController
@RequiredArgsConstructor
class ProductController implements ProductApi {

    /**
     * 商品コマンドサービス
     */
    private final ProductCommandService commandService;

    /**
     * 商品クエリサービス
     */
    private final ProductQueryService queryService;

    /**
     * 商品マッパー
     */
    private final ProductMapper mapper;

// ============================== GET ==============================

    /**
     * 商品の一覧・絞り込み（AC-47〜50）
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
        var query = new ProductListQuery(code, name, status == null ? null : status.getValue(), categoryId);
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
     * 商品の登録（AC-6、13〜17、25）
     *
     * @param request 商品の登録リクエスト
     * @return Http 201 : {@link CreatedId}
     */
    @Override
    public ResponseEntity<CreatedId> createProduct(CreateProductRequest request) {
        // 商品登録
        UUID id = commandService.createProduct(mapper.toCreateProductCommand(request));

        // ロケーション：登録した商品の URL
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();

        return ResponseEntity.created(location).body(new CreatedId(id));
    }

    /**
     * バリアントの登録（AC-20〜24）。商品の一部で単独の URL を持たないので、Location は返さない
     *
     * @param productId 商品 ID
     * @param request   バリアントの登録リクエスト
     * @return Http 201 : {@link CreatedId}
     */
    @Override
    public ResponseEntity<CreatedId> addVariant(UUID productId, AddVariantRequest request) {
        UUID id = commandService.addVariant(mapper.toAddVariantCommand(productId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedId(id));
    }

// ============================== PUT ==============================

    /**
     * 商品の編集（AC-6、18、19、25、26）
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
     * 公開する（AC-29、31）。冪等
     *
     * @param productId 商品 ID
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> publishProduct(UUID productId) {
        commandService.publishProduct(productId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 非公開にする（AC-30）。冪等
     *
     * @param productId 商品 ID
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> unpublishProduct(UUID productId) {
        commandService.unpublishProduct(productId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 販売終了にする（AC-32）。冪等
     *
     * @param productId 商品 ID
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> discontinueProduct(UUID productId) {
        commandService.discontinueProduct(productId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 公開期間の設定（AC-34〜36）
     *
     * @param productId 商品 ID
     * @param request   公開期間の設定リクエスト
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> setPublicationPeriod(UUID productId, SetPublicationPeriodRequest request) {
        commandService.changePublicationPeriod(mapper.toChangePublicationPeriodCommand(productId, request));
        return ResponseEntity.noContent().build();
    }

    /**
     * バリアントの表示順の変更（AC-27）
     *
     * @param productId 商品 ID
     * @param request   バリアントの並び順の変更リクエスト
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> reorderVariants(UUID productId, ReorderVariantsRequest request) {
        commandService.reorderVariants(mapper.toReorderVariantsCommand(productId, request));
        return ResponseEntity.noContent().build();
    }

    /**
     * バリアントの販売停止（AC-28）。冪等
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> suspendVariant(UUID productId, UUID variantId) {
        commandService.suspendVariant(productId, variantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * バリアントの販売停止の解除（AC-28）。冪等
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> resumeVariant(UUID productId, UUID variantId) {
        commandService.resumeVariant(productId, variantId);
        return ResponseEntity.noContent().build();
    }

// ============================== 商品画像（US-7。実装するまで 501） ==============================

    /**
     * 画像のアップロードの受付（AC-39、40）。P2（段階 10）で実装する
     *
     * @return Http 501
     */
    @Override
    public ResponseEntity<ImageUpload> createImageUpload(UUID productId, CreateImageUploadRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /**
     * 受け付けた画像の登録（AC-37、39、40）。P2（段階 10）で実装する
     *
     * @return Http 501
     */
    @Override
    public ResponseEntity<CreatedId> addProductImage(UUID productId, AddProductImageRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /**
     * 画像の並び順の変更（AC-37）。P2（段階 10）で実装する
     *
     * @return Http 501
     */
    @Override
    public ResponseEntity<Void> reorderProductImages(UUID productId, ReorderProductImagesRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /**
     * 画像の削除（AC-37）。P2（段階 10）で実装する
     *
     * @return Http 501
     */
    @Override
    public ResponseEntity<Void> deleteProductImage(UUID productId, UUID imageId, Long version) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /**
     * バリアントの画像の指定（AC-38）。P2（段階 10）で実装する
     *
     * @return Http 501
     */
    @Override
    public ResponseEntity<Void> setVariantImage(UUID productId, UUID variantId, SetVariantImageRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

// ============================== DELETE ==============================

    /**
     * 商品の削除（AC-33）
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
     * バリアントの削除（AC-71）
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @param version   取得時の商品のバージョン
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> deleteVariant(UUID productId, UUID variantId, Long version) {
        commandService.deleteVariant(productId, variantId, version);
        return ResponseEntity.noContent().build();
    }
}

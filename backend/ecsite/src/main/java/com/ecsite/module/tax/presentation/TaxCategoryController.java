package com.ecsite.module.tax.presentation;

import com.ecsite.api.admin.TaxApi;
import com.ecsite.api.admin.model.CreateTaxCategoryRequest;
import com.ecsite.api.admin.model.CreatedId;
import com.ecsite.api.admin.model.ReplaceTaxRatesRequest;
import com.ecsite.api.admin.model.TaxCategory;
import com.ecsite.api.admin.model.TaxCategoryList;
import com.ecsite.module.tax.application.TaxCategoryCommandService;
import com.ecsite.module.tax.application.TaxCategoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

/**
 * 税区分のコントローラー（B 側。admin.yaml の tag: tax）
 */
@RestController
@RequiredArgsConstructor
class TaxCategoryController implements TaxApi {

    /**
     * 税区分コマンドサービス
     */
    private final TaxCategoryCommandService commandService;

    /**
     * 税区分クエリサービス
     */
    private final TaxCategoryQueryService queryService;

    /**
     * 税区分マッパー
     */
    private final TaxCategoryMapper mapper;

// ============================== GET ==============================

    /**
     * 税区分の一覧
     *
     * @return Http 200 : {@link TaxCategoryList}
     */
    @Override
    public ResponseEntity<TaxCategoryList> listTaxCategories() {
        var items = queryService.listTaxCategories().stream().map(mapper::toTaxCategory).toList();
        return ResponseEntity.ok(new TaxCategoryList(items));
    }

    /**
     * 税区分の取得
     *
     * @param taxCategoryId 税区分 ID
     * @return Http 200 : {@link TaxCategory}
     */
    @Override
    public ResponseEntity<TaxCategory> getTaxCategory(UUID taxCategoryId) {
        return ResponseEntity.ok(mapper.toTaxCategory(queryService.getTaxCategory(taxCategoryId)));
    }

// ============================== POST ==============================

    /**
     * 税区分の登録
     *
     * @param request 税区分の登録リクエスト
     * @return Http 201 : {@link CreatedId}
     */
    @Override
    public ResponseEntity<CreatedId> createTaxCategory(CreateTaxCategoryRequest request) {
        // 税区分登録
        UUID id = commandService.createTaxCategory(mapper.toCreateTaxCategoryCommand(request));

        // ロケーション：登録した税区分の URL
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();

        return ResponseEntity.created(location).body(new CreatedId(id));
    }

// ============================== PUT ==============================

    /**
     * 税率の一覧の置き換え
     *
     * @param taxCategoryId 税区分 ID
     * @param request       税率の一覧の置き換えリクエスト
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> replaceTaxRates(UUID taxCategoryId, ReplaceTaxRatesRequest request) {
        commandService.replaceTaxRates(mapper.toReplaceTaxRatesCommand(taxCategoryId, request));
        return ResponseEntity.noContent().build();
    }
}

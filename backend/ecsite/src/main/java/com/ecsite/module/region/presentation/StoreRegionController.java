package com.ecsite.module.region.presentation;

import com.ecsite.api.store.RegionApi;
import com.ecsite.api.store.model.MunicipalityList;
import com.ecsite.api.store.model.PostalCodeAddressList;
import com.ecsite.api.store.model.PrefectureList;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.region.application.RegionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * 地域の店舗 API のコントローラー
 *
 * <p>store.yaml のタグ region の操作を、店舗モジュールを通さずに地域モジュールで受ける。
 */
@RestController
@RequiredArgsConstructor
class StoreRegionController implements RegionApi {

    /**
     * 地域のマスターデータの参照
     */
    private final RegionQueryService queryService;

    /**
     * API の型への変換
     */
    private final RegionMapper mapper;

    /**
     * 都道府県の一覧
     *
     * @return Http 200 : {@link PrefectureList}
     */
    @Override
    public ResponseEntity<PrefectureList> listPrefectures() {
        return ResponseEntity.ok(mapper.toPrefectureList(queryService.prefectures()));
    }

    /**
     * 都道府県の市区町村の一覧
     *
     * @param prefectureCode 都道府県のコード
     * @return Http 200 : {@link MunicipalityList}
     * @throws BusinessException 都道府県がない（{@code NOT_FOUND}）
     */
    @Override
    public ResponseEntity<MunicipalityList> listMunicipalities(String prefectureCode) {
        var found = queryService.municipalitiesOf(prefectureCode)
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
        return ResponseEntity.ok(mapper.toMunicipalityList(found.municipalities()));
    }

    /**
     * 郵便番号の住所
     *
     * @param postalCode 郵便番号（数字 7 桁）
     * @return Http 200 : {@link PostalCodeAddressList}。ないときは空の一覧
     */
    @Override
    public ResponseEntity<PostalCodeAddressList> findPostalCodeAddresses(String postalCode) {
        return ResponseEntity.ok(mapper.toPostalCodeAddressList(queryService.findPostalAddresses(postalCode)));
    }
}

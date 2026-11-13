package com.ecsite.module.region.application;

import com.ecsite.module.region.application.model.MunicipalityView;
import com.ecsite.module.region.application.model.PrefectureMunicipalitiesView;
import com.ecsite.module.region.application.port.RegionRepository;
import com.ecsite.module.region.domain.vo.PostalAddress;
import com.ecsite.module.region.domain.vo.Prefecture;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 地域のマスターデータの参照
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RegionQueryService {

    /**
     * 地域のマスターデータのリポジトリ
     */
    private final RegionRepository repository;

    /**
     * 都道府県の一覧を返す。
     *
     * @return コードの順の都道府県
     */
    public List<Prefecture> prefectures() {
        return repository.findPrefectures();
    }

    /**
     * 都道府県をコードで引く。
     *
     * @param code 都道府県のコード
     * @return 都道府県。なければ空
     */
    public Optional<Prefecture> findPrefecture(@NonNull String code) {
        return repository.findPrefecture(code);
    }

    /**
     * 都道府県と、その市区町村の一覧を返す。
     *
     * @param prefectureCode 都道府県のコード
     * @return 都道府県と、コードの順の市区町村。都道府県がなければ空
     */
    public Optional<PrefectureMunicipalitiesView> municipalitiesOf(@NonNull String prefectureCode) {
        // 都道府県の取得
        var prefecture = repository.findPrefecture(prefectureCode);
        if (prefecture.isEmpty()) {
            return Optional.empty();
        }

        // 市区町村の取得
        var municipalities = repository.findMunicipalities(prefectureCode);
        return Optional.of(new PrefectureMunicipalitiesView(prefecture.get(), municipalities));
    }

    /**
     * 市区町村と、属する都道府県をコードで引く。
     *
     * @param code 市区町村のコード
     * @return 都道府県と市区町村。なければ空
     */
    public Optional<MunicipalityView> findMunicipality(@NonNull String code) {
        return repository.findMunicipality(code);
    }

    /**
     * 郵便番号の住所を返す。
     *
     * @param postalCode 郵便番号（数字 7 桁）
     * @return 並び順の住所。なければ空の一覧
     */
    public List<PostalAddress> findPostalAddresses(@NonNull String postalCode) {
        return repository.findPostalAddresses(postalCode);
    }
}

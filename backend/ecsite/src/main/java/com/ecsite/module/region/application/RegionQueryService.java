package com.ecsite.module.region.application;

import com.ecsite.module.region.application.port.RegionRepository;
import com.ecsite.module.region.domain.vo.Municipality;
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
     * 都道府県の一覧
     *
     * @return コードの順の都道府県
     */
    public List<Prefecture> prefectures() {
        return repository.findPrefectures();
    }

    /**
     * 都道府県をコードで引く
     *
     * @param code 都道府県のコード
     * @return 都道府県。なければ空
     */
    public Optional<Prefecture> findPrefecture(@NonNull String code) {
        return repository.findPrefecture(code);
    }

    /**
     * 都道府県と、その市区町村の一覧
     *
     * @param prefectureCode 都道府県のコード
     * @return 都道府県と、コードの順の市区町村。都道府県がなければ空
     */
    public Optional<PrefectureMunicipalities> municipalitiesOf(@NonNull String prefectureCode) {
        // 都道府県の取得
        var prefecture = repository.findPrefecture(prefectureCode);
        if (prefecture.isEmpty()) {
            return Optional.empty();
        }

        // 市区町村の取得
        var municipalities = repository.findMunicipalities(prefectureCode);
        return Optional.of(new PrefectureMunicipalities(prefecture.get(), municipalities));
    }

    /**
     * 市区町村と、属する都道府県をコードで引く
     *
     * @param code 市区町村のコード
     * @return 都道府県と市区町村。なければ空
     */
    public Optional<PrefectureMunicipality> findMunicipality(@NonNull String code) {
        // 市区町村の取得
        var found = repository.findMunicipality(code);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        var municipality = found.get();

        // 属する都道府県の取得
        return repository.findPrefecture(municipality.prefectureCode())
                .map(prefecture -> new PrefectureMunicipality(prefecture, municipality));
    }

    /**
     * 郵便番号の住所
     *
     * @param postalCode 郵便番号（数字 7 桁）
     * @return 並び順の住所。なければ空の一覧
     */
    public List<PostalAddress> findPostalAddresses(@NonNull String postalCode) {
        return repository.findPostalAddresses(postalCode);
    }

    /**
     * 都道府県と、その市区町村の一覧
     *
     * @param prefecture     都道府県
     * @param municipalities コードの順の市区町村
     */
    public record PrefectureMunicipalities(Prefecture prefecture, List<Municipality> municipalities) {
    }

    /**
     * 市区町村と、属する都道府県
     *
     * @param prefecture   都道府県
     * @param municipality 市区町村
     */
    public record PrefectureMunicipality(Prefecture prefecture, Municipality municipality) {
    }
}

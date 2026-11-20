package com.ecsite.module.region.application;

import com.ecsite.module.region.application.model.MunicipalityView;
import com.ecsite.module.region.application.model.PrefectureMunicipalitiesView;
import com.ecsite.module.region.application.port.RegionRepository;
import com.ecsite.module.region.domain.vo.PostalAddress;
import com.ecsite.module.region.domain.vo.Prefecture;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 地域のマスターデータの参照
 *
 * <p>キャッシュから返す 4 つの照会は、{@code Propagation.SUPPORTS} にする。キャッシュにあれば、トランザクションを
 * 始めない。期限切れの読み込みは、呼ぶ側のトランザクションがあればそれに加わるので、2 本目の接続を取らない
 * （{@code NOT_SUPPORTED} だと、呼ぶ側の接続を保留したまま読み込みの接続を取り、接続が尽きるおそれがある）。
 * 参照のメソッドには伝播を指定しない、という規則（backend-coding.md の「トランザクションは application」）の例外である。
 * {@code readOnly = true} は、クラスの指定と同じにしただけで、新しくトランザクションを始めないので効果はない。
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RegionQueryService {

    /**
     * 地域のマスターデータのキャッシュ
     */
    private final RegionMasterCache cache;

    /**
     * 地域のマスターデータのリポジトリ（郵便番号の検索で使う）
     */
    private final RegionRepository repository;

    /**
     * 都道府県の一覧を返す。
     *
     * @return コードの順の都道府県
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public List<Prefecture> prefectures() {
        return cache.get().prefectures();
    }

    /**
     * 都道府県をコードで引く。
     *
     * @param code 都道府県のコード
     * @return 都道府県。なければ空
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<Prefecture> findPrefecture(@NonNull String code) {
        return cache.get().findPrefecture(code);
    }

    /**
     * 都道府県と、その市区町村の一覧を返す。
     *
     * @param prefectureCode 都道府県のコード
     * @return 都道府県と、コードの順の市区町村。都道府県がなければ空
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<PrefectureMunicipalitiesView> municipalitiesOf(@NonNull String prefectureCode) {
        return cache.get().municipalitiesOf(prefectureCode);
    }

    /**
     * 市区町村と、属する都道府県をコードで引く。
     *
     * @param code 市区町村のコード
     * @return 都道府県と市区町村。なければ空
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<MunicipalityView> findMunicipality(@NonNull String code) {
        return cache.get().findMunicipality(code);
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

package com.ecsite.module.region.infrastructure.persistence;

import com.ecsite.module.region.application.model.MunicipalityView;
import com.ecsite.module.region.application.port.RegionRepository;
import com.ecsite.module.region.domain.vo.Municipality;
import com.ecsite.module.region.domain.vo.PostalAddress;
import com.ecsite.module.region.domain.vo.Prefecture;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Records;
import org.jooq.SelectField;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.ecsite.jooq.region.Tables.MUNICIPALITY;
import static com.ecsite.jooq.region.Tables.POSTAL_CODE;
import static com.ecsite.jooq.region.Tables.PREFECTURE;
import static org.jooq.impl.DSL.row;

/**
 * 地域のマスターデータのリポジトリの jOOQ の実装
 */
@Repository
@RequiredArgsConstructor
class JooqRegionRepository implements RegionRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

// ============================== 取得列と変換 ==============================

    /**
     * 都道府県の列と、都道府県への変換
     *
     * <p>列の並びは {@link Prefecture} の項目の順にする。
     */
    private static final SelectField<Prefecture> PREFECTURE_ROW =
            row(PREFECTURE.CODE, PREFECTURE.NAME).mapping(Prefecture::new);

    /**
     * 市区町村の列と、市区町村への変換
     *
     * <p>列の並びは {@link Municipality} の項目の順にする。
     */
    private static final SelectField<Municipality> MUNICIPALITY_ROW =
            row(MUNICIPALITY.CODE, MUNICIPALITY.PREFECTURE_CODE, MUNICIPALITY.NAME).mapping(Municipality::new);

// ============================== 検索処理 ==============================

    /**
     * 都道府県の一覧
     *
     * @return コードの順の都道府県
     */
    @Override
    public List<Prefecture> findPrefectures() {
        return dsl.select(PREFECTURE_ROW).from(PREFECTURE).orderBy(PREFECTURE.CODE)
                .fetch(Record1::value1);
    }

    /**
     * 都道府県をコードで引く
     *
     * @param code 都道府県のコード
     * @return 都道府県。なければ空
     */
    @Override
    public Optional<Prefecture> findPrefecture(String code) {
        return dsl.select(PREFECTURE_ROW).from(PREFECTURE).where(PREFECTURE.CODE.eq(code))
                .fetchOptional(Record1::value1);
    }

    /**
     * 都道府県の市区町村の一覧
     *
     * @param prefectureCode 都道府県のコード
     * @return コードの順の市区町村。都道府県がなければ空の一覧
     */
    @Override
    public List<Municipality> findMunicipalities(String prefectureCode) {
        return dsl.select(MUNICIPALITY_ROW).from(MUNICIPALITY)
                .where(MUNICIPALITY.PREFECTURE_CODE.eq(prefectureCode))
                .orderBy(MUNICIPALITY.CODE)
                .fetch(Record1::value1);
    }

    /**
     * 市区町村と、属する都道府県をコードで引く
     *
     * @param code 市区町村のコード
     * @return 都道府県と市区町村。なければ空
     */
    @Override
    public Optional<MunicipalityView> findMunicipality(String code) {
        // 列の並びは MunicipalityView の項目の順にする
        return dsl.select(PREFECTURE_ROW, MUNICIPALITY_ROW)
                .from(MUNICIPALITY)
                .join(PREFECTURE).on(PREFECTURE.CODE.eq(MUNICIPALITY.PREFECTURE_CODE))
                .where(MUNICIPALITY.CODE.eq(code))
                .fetchOptional(Records.mapping(MunicipalityView::new));
    }

    /**
     * 郵便番号の住所
     *
     * <p>市区町村と都道府県を結合して、並び順に返す。
     *
     * @param postalCode 郵便番号（数字 7 桁）
     * @return 並び順の住所。なければ空の一覧
     */
    @Override
    public List<PostalAddress> findPostalAddresses(String postalCode) {
        // 列の並びは PostalAddress の項目の順にする
        return dsl.select(POSTAL_CODE.POSTAL_CODE_, PREFECTURE_ROW, MUNICIPALITY_ROW, POSTAL_CODE.TOWN_NAME)
                .from(POSTAL_CODE)
                .join(MUNICIPALITY).on(MUNICIPALITY.CODE.eq(POSTAL_CODE.MUNICIPALITY_CODE))
                .join(PREFECTURE).on(PREFECTURE.CODE.eq(MUNICIPALITY.PREFECTURE_CODE))
                .where(POSTAL_CODE.POSTAL_CODE_.eq(postalCode))
                .orderBy(POSTAL_CODE.DISPLAY_ORDER)
                .fetch(Records.mapping(PostalAddress::new));
    }
}

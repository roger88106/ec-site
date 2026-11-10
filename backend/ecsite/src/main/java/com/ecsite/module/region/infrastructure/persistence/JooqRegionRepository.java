package com.ecsite.module.region.infrastructure.persistence;

import com.ecsite.module.region.application.port.RegionRepository;
import com.ecsite.module.region.domain.Municipality;
import com.ecsite.module.region.domain.PostalAddress;
import com.ecsite.module.region.domain.Prefecture;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.Record3;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.ecsite.jooq.region.Tables.MUNICIPALITY;
import static com.ecsite.jooq.region.Tables.POSTAL_CODE;
import static com.ecsite.jooq.region.Tables.PREFECTURE;

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

    /**
     * 都道府県の一覧
     *
     * @return コードの順の都道府県
     */
    @Override
    public List<Prefecture> findPrefectures() {
        return dsl.select(PREFECTURE.CODE, PREFECTURE.NAME).from(PREFECTURE).orderBy(PREFECTURE.CODE)
                .fetch(JooqRegionRepository::toPrefecture);
    }

    /**
     * 都道府県をコードで引く
     *
     * @param code 都道府県のコード
     * @return 都道府県。なければ空
     */
    @Override
    public Optional<Prefecture> findPrefecture(String code) {
        return dsl.select(PREFECTURE.CODE, PREFECTURE.NAME).from(PREFECTURE).where(PREFECTURE.CODE.eq(code))
                .fetchOptional(JooqRegionRepository::toPrefecture);
    }

    /**
     * 都道府県の市区町村の一覧
     *
     * @param prefectureCode 都道府県のコード
     * @return コードの順の市区町村。都道府県がなければ空の一覧
     */
    @Override
    public List<Municipality> findMunicipalities(String prefectureCode) {
        return dsl.select(MUNICIPALITY.CODE, MUNICIPALITY.PREFECTURE_CODE, MUNICIPALITY.NAME).from(MUNICIPALITY)
                .where(MUNICIPALITY.PREFECTURE_CODE.eq(prefectureCode))
                .orderBy(MUNICIPALITY.CODE)
                .fetch(JooqRegionRepository::toMunicipality);
    }

    /**
     * 市区町村をコードで引く
     *
     * @param code 市区町村のコード
     * @return 市区町村。なければ空
     */
    @Override
    public Optional<Municipality> findMunicipality(String code) {
        return dsl.select(MUNICIPALITY.CODE, MUNICIPALITY.PREFECTURE_CODE, MUNICIPALITY.NAME).from(MUNICIPALITY)
                .where(MUNICIPALITY.CODE.eq(code))
                .fetchOptional(JooqRegionRepository::toMunicipality);
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
        return dsl.select(POSTAL_CODE.POSTAL_CODE_, POSTAL_CODE.TOWN_NAME,
                        MUNICIPALITY.CODE, MUNICIPALITY.PREFECTURE_CODE, MUNICIPALITY.NAME, PREFECTURE.NAME)
                .from(POSTAL_CODE)
                .join(MUNICIPALITY).on(MUNICIPALITY.CODE.eq(POSTAL_CODE.MUNICIPALITY_CODE))
                .join(PREFECTURE).on(PREFECTURE.CODE.eq(MUNICIPALITY.PREFECTURE_CODE))
                .where(POSTAL_CODE.POSTAL_CODE_.eq(postalCode))
                .orderBy(POSTAL_CODE.DISPLAY_ORDER)
                .fetch(r -> new PostalAddress(r.value1(), new Prefecture(r.value4(), r.value6()),
                        new Municipality(r.value3(), r.value4(), r.value5()), r.value2()));
    }

// ============================== プライベートメソッド ==============================

    /**
     * 都道府県の行（コード、名前）を都道府県にする。
     */
    private static Prefecture toPrefecture(Record2<String, String> row) {
        return new Prefecture(row.value1(), row.value2());
    }

    /**
     * 市区町村の行（コード、都道府県のコード、名前）を市区町村にする。
     */
    private static Municipality toMunicipality(Record3<String, String, String> row) {
        return new Municipality(row.value1(), row.value2(), row.value3());
    }
}

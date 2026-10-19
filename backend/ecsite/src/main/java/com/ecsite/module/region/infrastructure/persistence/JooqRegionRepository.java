package com.ecsite.module.region.infrastructure.persistence;

import com.ecsite.module.region.application.port.RegionRepository;
import com.ecsite.module.region.domain.Municipality;
import com.ecsite.module.region.domain.PostalAddress;
import com.ecsite.module.region.domain.Prefecture;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
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
     */
    @Override
    public List<Prefecture> findPrefectures() {
        return dsl.select(PREFECTURE.CODE, PREFECTURE.NAME).from(PREFECTURE).orderBy(PREFECTURE.CODE)
                .fetch(r -> new Prefecture(r.value1(), r.value2()));
    }

    /**
     * 都道府県をコードで引く
     */
    @Override
    public Optional<Prefecture> findPrefecture(String code) {
        return dsl.select(PREFECTURE.CODE, PREFECTURE.NAME).from(PREFECTURE).where(PREFECTURE.CODE.eq(code))
                .fetchOptional(r -> new Prefecture(r.value1(), r.value2()));
    }

    /**
     * 都道府県の市区町村の一覧
     */
    @Override
    public List<Municipality> findMunicipalities(String prefectureCode) {
        return dsl.select(MUNICIPALITY.CODE, MUNICIPALITY.PREFECTURE_CODE, MUNICIPALITY.NAME).from(MUNICIPALITY)
                .where(MUNICIPALITY.PREFECTURE_CODE.eq(prefectureCode))
                .orderBy(MUNICIPALITY.CODE)
                .fetch(r -> new Municipality(r.value1(), r.value2(), r.value3()));
    }

    /**
     * 市区町村をコードで引く
     */
    @Override
    public Optional<Municipality> findMunicipality(String code) {
        return dsl.select(MUNICIPALITY.CODE, MUNICIPALITY.PREFECTURE_CODE, MUNICIPALITY.NAME).from(MUNICIPALITY)
                .where(MUNICIPALITY.CODE.eq(code))
                .fetchOptional(r -> new Municipality(r.value1(), r.value2(), r.value3()));
    }

    /**
     * 郵便番号の住所。市区町村と都道府県を結んで、並び順に返す
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
}

package com.ecsite.module.region.api;

import com.ecsite.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.ecsite.jooq.region.Tables.MUNICIPALITY;
import static com.ecsite.jooq.region.Tables.POSTAL_CODE;
import static com.ecsite.jooq.region.Tables.PREFECTURE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 地域の api の受け入れテスト。最初のデータ（郵便番号のデータから作ったマイグレーション）を読む。
 */
@IntegrationTest
class RegionApiTest {

    /**
     * 市区町村の数（2026-09-30 の郵便番号のデータ。政令指定都市は区で数える）
     */
    private static final int MUNICIPALITIES = 1892;

    /**
     * 地域の api
     */
    @Autowired
    RegionLookupApi regionApi;

    /**
     * 件数を数える
     */
    @Autowired
    DSLContext dsl;

    @Test
    @DisplayName("AC-259 都道府県は 47、市区町村は全国の数だけ、最初のデータにある")
    void allPrefecturesAndMunicipalities() {
        assertThat(regionApi.prefectures()).hasSize(47)
                .first().isEqualTo(new RegionPrefecture("01", "北海道"));
        assertThat(regionApi.prefectures()).last().isEqualTo(new RegionPrefecture("47", "沖縄県"));
        assertThat(dsl.fetchCount(MUNICIPALITY)).isEqualTo(MUNICIPALITIES);
        assertThat(dsl.fetchCount(PREFECTURE)).isEqualTo(47);
    }

    @Test
    @DisplayName("AC-260 郵便番号は、最初のデータの一覧の郵便番号だけがある")
    void somePostalCodes() throws Exception {
        var listed = Files.readAllLines(Path.of(System.getProperty("region.postal-codes"))).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .distinct()
                .count();

        assertThat(dsl.fetchCount(dsl.selectDistinct(POSTAL_CODE.POSTAL_CODE_).from(POSTAL_CODE))).isEqualTo(listed);
    }

    @Test
    @DisplayName("都道府県の市区町村の一覧は、その都道府県の市区町村だけをコードの順に返す。ない都道府県は空")
    void municipalitiesOfPrefecture() {
        var tokyo = regionApi.municipalitiesOf("13").orElseThrow();

        assertThat(tokyo).isNotEmpty().allMatch(m -> m.prefectureCode().equals("13"));
        assertThat(tokyo).extracting(RegionMunicipality::code).isSorted();
        assertThat(tokyo).first().isEqualTo(new RegionMunicipality("13101", "千代田区", "13", "東京都"));
        assertThat(regionApi.municipalitiesOf("48")).isEmpty();
    }

    @Test
    @DisplayName("市区町村をコードで引くと、都道府県の名前も返す。ないコードは空")
    void findMunicipality() {
        assertThat(regionApi.findMunicipality("01101")).contains(new RegionMunicipality("01101", "札幌市中央区", "01", "北海道"));
        assertThat(regionApi.findMunicipality("99999")).isEmpty();
    }

    @Test
    @DisplayName("AC-253 郵便番号で引くと、都道府県・市区町村・町域を返す")
    void findByPostalCode() {
        assertThat(regionApi.findByPostalCode("1000001")).containsExactly(
                new RegionPostalAddress("1000001", new RegionMunicipality("13101", "千代田区", "13", "東京都"), "千代田"));
    }

    @Test
    @DisplayName("AC-254 1 つの郵便番号に複数の町域があれば、すべてを並び順に返す。2 つの市区町村にまたがることもある")
    void multipleAddresses() {
        assertThat(regionApi.findByPostalCode("0691334")).extracting(RegionPostalAddress::townName)
                .containsExactly("錦町北", "錦町南");
        assertThat(regionApi.findByPostalCode("0040000")).extracting(address -> address.municipality().name())
                .containsExactly("札幌市厚別区", "札幌市清田区");
    }

    @Test
    @DisplayName("AC-256 ない郵便番号は空を返す")
    void unknownPostalCode() {
        assertThat(regionApi.findByPostalCode("9999999")).isEmpty();
    }

    @Test
    @DisplayName("AC-283 町域名が注記だけの郵便番号は、町域を空で返す")
    void noteOnlyTownName() {
        assertThat(regionApi.findByPostalCode("0600000")).singleElement()
                .satisfies(address -> {
                    assertThat(address.townName()).isEmpty();
                    assertThat(address.municipality().name()).isEqualTo("札幌市中央区");
                });
    }
}

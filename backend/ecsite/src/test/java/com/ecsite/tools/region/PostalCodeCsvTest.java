package com.ecsite.tools.region;

import com.ecsite.module.region.domain.vo.Municipality;
import com.ecsite.module.region.domain.vo.Prefecture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 郵便番号のデータの変換の単体テスト
 *
 * <p>対象は、日本郵便の「住所の郵便番号（1 レコード 1 行、UTF-8 形式）」のデータ。入力は、注記の種類ごとに本物の形の行を数行並べたテストのデータ
 * （{@code /region/ken_all_sample.csv}）。期待値は、テストのデータの行から書く。
 */
class PostalCodeCsvTest {

    // ============================== テストのデータ ==============================

    /**
     * テストのデータを変換した結果
     */
    private static PostalCodeData data;

    @BeforeAll
    static void read() throws IOException {
        try (var in = Objects.requireNonNull(PostalCodeCsvTest.class.getResourceAsStream("/region/ken_all_sample.csv"));
             var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            data = PostalCodeCsv.read(reader);
        }
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("都道府県と市区町村")
    class Areas {

        @Test
        @DisplayName("都道府県は、地方公共団体コードの先頭 2 桁と都道府県名で、コードの順に 1 つずつ作る")
        void prefectures() {
            // 確認：テストのデータの 4 つの都道府県が、コードの順に 1 つずつある
            assertThat(data.prefectures()).containsExactly(
                    new Prefecture("01", "北海道"), new Prefecture("13", "東京都"),
                    new Prefecture("23", "愛知県"), new Prefecture("25", "滋賀県"));
        }

        @Test
        @DisplayName("市区町村は、地方公共団体コードごとに 1 つずつ、コードの順に作る")
        void municipalities() {
            // 確認：テストのデータの 5 つの市区町村が、コードの順に 1 つずつある
            assertThat(data.municipalities()).containsExactly(
                    new Municipality("01101", "01", "札幌市中央区"),
                    new Municipality("13101", "13", "千代田区"),
                    new Municipality("13362", "13", "利島村"),
                    new Municipality("23105", "23", "名古屋市中村区"),
                    new Municipality("25443", "25", "犬上郡多賀町"));
        }
    }

    @Nested
    @DisplayName("町域名の注記")
    class TownNameNotes {

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({"北一条西（１〜１９丁目）, 0600001, 北一条西", "大手町ＪＡビル（地階・階層不明）, 1006890, 大手町ＪＡビル"})
        @DisplayName("AC-283 町域名の括弧の中の補足を除く")
        void removesNotes(String original, String postalCode, String townName) {
            // 確認：データの町域名（original）から、括弧の中の補足を除いた町域名になる
            assertThat(townsOf(postalCode)).containsExactly(townName);
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({"旭ケ丘, 0640941", "一円, 5220317"})
        @DisplayName("AC-283 注記でない町域名は、そのまま残す")
        void keepsTownNamesWithoutNotes(String townName, String postalCode) {
            // 確認：データの町域名のまま（「一円」だけの町域名は、市区町村名＋一円の注記と違い、住所である）
            assertThat(townsOf(postalCode)).containsExactly(townName);
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({"以下に掲載がない場合, 0600000", "利島村一円, 1000301", "名古屋市中村区の次に番地がくる場合, 4530000"})
        @DisplayName("AC-283 注記だけの町域名は空にする")
        void emptiesNoteOnlyTownNames(String original, String postalCode) {
            // 確認：データの町域名（original）が注記だけなので、町域名が空になる
            assertThat(townsOf(postalCode)).containsExactly("");
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({"以下に掲載がない場合, 0600000, 01101", "利島村一円, 1000301, 13362", "名古屋市中村区の次に番地がくる場合, 4530000, 23105"})
        @DisplayName("AC-283 町域名が注記だけの郵便番号も、市区町村のコードを残す")
        void keepsMunicipalityOfNoteOnlyPostalCode(String original, String postalCode, String municipalityCode) {
            // 確認：郵便番号の行が 1 つ残り、データの市区町村のコードを持つ（都道府県と市区町村は返せる）
            assertThat(data.postalCodes()).filteredOn(entry -> entry.postalCode().equals(postalCode))
                    .singleElement()
                    .extracting(PostalCodeEntry::municipalityCode)
                    .isEqualTo(municipalityCode);
        }

        @Test
        @DisplayName("同じ郵便番号で、補足を除くと同じ町域名になる行は、1 つにまとめる")
        void mergesRowsWithSameTownNameAfterRemovingNotes() {
            // 確認：「外神田」と「外神田（次のビルを除く）」の 2 行が、並び順 1 の 1 行になる
            assertThat(data.postalCodes()).filteredOn(entry -> entry.postalCode().equals("1010021"))
                    .containsExactly(new PostalCodeEntry("1010021", "13101", "外神田", 1));
        }
    }

    @Nested
    @DisplayName("郵便番号を限る")
    class Restriction {

        @Test
        @DisplayName("郵便番号を限ると、限った郵便番号だけが残る")
        void keepsOnlyRestrictedPostalCodes() {
            // 操作：2 つの郵便番号に限る
            var restricted = data.restrictPostalCodes(Set.of("1000001", "5220317"));

            // 確認：限った郵便番号だけ
            assertThat(restricted.postalCodes()).extracting(PostalCodeEntry::postalCode).containsExactly("1000001", "5220317");
        }

        @Test
        @DisplayName("郵便番号を限っても、都道府県と市区町村は全件のまま")
        void keepsAllAreas() {
            // 操作：2 つの郵便番号に限る
            var restricted = data.restrictPostalCodes(Set.of("1000001", "5220317"));

            // 確認：都道府県と市区町村は、限る前と同じ
            assertThat(restricted.prefectures()).isEqualTo(data.prefectures());
            assertThat(restricted.municipalities()).isEqualTo(data.municipalities());
        }
    }

    // ============================== 補助 ==============================

    /**
     * テストのデータの、郵便番号の町域名を返す。
     *
     * @param postalCode 郵便番号
     * @return 町域名（データの順）
     */
    private static List<String> townsOf(String postalCode) {
        return data.postalCodes().stream()
                .filter(entry -> entry.postalCode().equals(postalCode))
                .map(PostalCodeEntry::townName)
                .toList();
    }
}

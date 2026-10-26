package com.ecsite.tools.region;

import com.ecsite.module.region.domain.Municipality;
import com.ecsite.module.region.domain.Prefecture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
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
 * 郵便番号のデータ（日本郵便の「住所の郵便番号（1 レコード 1 行、UTF-8 形式）」）の変換の単体テスト。<br />
 * 入力は、注記の種類ごとに本物の形の行を数行並べたテストのデータ。
 */
class PostalCodeCsvTest {

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

    @Test
    @DisplayName("都道府県は、地方公共団体コードの先頭 2 桁と都道府県名で、コードの順に 1 つずつ作る")
    void prefectures() {
        assertThat(data.prefectures()).containsExactly(
                new Prefecture("01", "北海道"), new Prefecture("13", "東京都"),
                new Prefecture("23", "愛知県"), new Prefecture("25", "滋賀県"));
    }

    @Test
    @DisplayName("市区町村は、地方公共団体コードごとに 1 つずつ、コードの順に作る")
    void municipalities() {
        assertThat(data.municipalities()).containsExactly(
                new Municipality("01101", "01", "札幌市中央区"),
                new Municipality("13101", "13", "千代田区"),
                new Municipality("13362", "13", "利島村"),
                new Municipality("23105", "23", "名古屋市中村区"),
                new Municipality("25443", "25", "犬上郡多賀町"));
    }

    @ParameterizedTest(name = "{0} の町域は「{1}」")
    @CsvSource({"0600001, 北一条西", "1006890, 大手町ＪＡビル", "0640941, 旭ケ丘", "5220317, 一円"})
    @DisplayName("AC-283 町域名の括弧の中の補足を除く。注記でない町域名はそのまま")
    void removesNotes(String postalCode, String townName) {
        assertThat(townsOf(postalCode)).containsExactly(townName);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"0600000", "1000301", "4530000"})
    @DisplayName("AC-283 注記だけの町域名（「以下に掲載がない場合」「〜の次に番地がくる場合」「市区町村名＋一円」）は空にする")
    void emptiesNoteOnlyTownNames(String postalCode) {
        assertThat(townsOf(postalCode)).containsExactly("");
    }

    @Test
    @DisplayName("同じ郵便番号の行は、データの順に並び順を振る。補足を除いて同じ町域名になった行は 1 つにする")
    void postalCodeOrder() {
        assertThat(data.postalCodes()).filteredOn(entry -> entry.postalCode().equals("1010021"))
                .containsExactly(new PostalCodeEntry("1010021", "13101", "外神田", 1));
    }

    @Test
    @DisplayName("郵便番号を限ると、限った郵便番号だけが残り、都道府県と市区町村は全件のまま")
    void restrictsPostalCodes() {
        var restricted = data.restrictPostalCodes(Set.of("1000001", "5220317"));

        assertThat(restricted.postalCodes()).extracting(PostalCodeEntry::postalCode).containsExactly("1000001", "5220317");
        assertThat(restricted.prefectures()).isEqualTo(data.prefectures());
        assertThat(restricted.municipalities()).isEqualTo(data.municipalities());
    }

    private static List<String> townsOf(String postalCode) {
        return data.postalCodes().stream()
                .filter(entry -> entry.postalCode().equals(postalCode))
                .map(PostalCodeEntry::townName)
                .toList();
    }
}

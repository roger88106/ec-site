package com.ecsite.module.reporting.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 都道府県の名前から全国地方公共団体コードへの読み替えの単体テスト（T0203、AC-220）
 */
class PrefectureCodeTest {

    /**
     * 全国地方公共団体コードの順（北海道 01 〜 沖縄県 47）
     */
    private static final List<String> NAMES = List.of(
            "北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県",
            "茨城県", "栃木県", "群馬県", "埼玉県", "千葉県", "東京都", "神奈川県",
            "新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県", "岐阜県", "静岡県", "愛知県",
            "三重県", "滋賀県", "京都府", "大阪府", "兵庫県", "奈良県", "和歌山県",
            "鳥取県", "島根県", "岡山県", "広島県", "山口県",
            "徳島県", "香川県", "愛媛県", "高知県",
            "福岡県", "佐賀県", "長崎県", "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県");

    @Test
    @DisplayName("AC-220 47 の都道府県を、全国地方公共団体コードの上 2 桁（01〜47）に読み替える")
    void mapsAllPrefectures() {
        IntStream.range(0, NAMES.size()).forEach(i ->
                assertThat(PrefectureCode.of(NAMES.get(i))).as(NAMES.get(i)).contains("%02d".formatted(i + 1)));
    }

    @ParameterizedTest(name = "{0} は {1}")
    @CsvSource({"北海道, 01", "東京都, 13", "神奈川県, 14", "愛知県, 23", "京都府, 26", "大阪府, 27", "福岡県, 40", "沖縄県, 47"})
    @DisplayName("AC-220 代表的な都道府県のコード")
    void knownCodes(String name, String code) {
        assertThat(PrefectureCode.of(name)).contains(code);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"東京", "Tokyo", "東京都 ", "架空県"})
    @DisplayName("表にない名前は空（不明）")
    void unknownName(String name) {
        assertThat(PrefectureCode.of(name)).isEmpty();
    }

    @Test
    @DisplayName("AC-220 コードの一覧は、北海道から沖縄県の順")
    void codesInOrder() {
        assertThat(PrefectureCode.codes()).hasSize(47).isSorted().startsWith("01").endsWith("47");
    }
}

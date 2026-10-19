package com.ecsite.module.reporting.domain;

import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 都道府県の名前から、全国地方公共団体コードの上 2 桁への読み替え（docs/backend/modules/reporting.md「都道府県のコード」）。<br />
 * 注文がお届け先をコードで持つようになったら、この表を除く。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PrefectureCode {

    /**
     * 全国地方公共団体コードの順の、都道府県の名前（北海道 01 〜 沖縄県 47）
     */
    private static final List<String> NAMES = List.of(
            "北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県",
            "茨城県", "栃木県", "群馬県", "埼玉県", "千葉県", "東京都", "神奈川県",
            "新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県", "岐阜県", "静岡県", "愛知県",
            "三重県", "滋賀県", "京都府", "大阪府", "兵庫県", "奈良県", "和歌山県",
            "鳥取県", "島根県", "岡山県", "広島県", "山口県",
            "徳島県", "香川県", "愛媛県", "高知県",
            "福岡県", "佐賀県", "長崎県", "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県");

    /**
     * 名前 → コード（コードの順）
     */
    private static final Map<String, String> CODES = IntStream.range(0, NAMES.size()).boxed()
            .collect(Collectors.toMap(NAMES::get, i -> "%02d".formatted(i + 1), (a, b) -> a, LinkedHashMap::new));

    /**
     * 都道府県の名前をコードに読み替える。
     *
     * @param name 都道府県の名前（「東京都」など。前後の空白も含めて完全に一致するもの）
     * @return コード（{@code 01}〜{@code 47}）。表にない名前なら空
     */
    public static Optional<String> of(@Nullable String name) {
        return Optional.ofNullable(name).map(CODES::get);
    }

    /**
     * テスト用（main からは呼ばない。消すとテストが変わるので残す。扱いは都道府県の主データを作るときに決める）。<br />
     *
     * @return すべてのコード（北海道から沖縄県の順）
     */
    public static List<String> codes() {
        return List.copyOf(CODES.values());
    }
}

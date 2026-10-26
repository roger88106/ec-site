package com.ecsite.tools.region;

import com.ecsite.module.region.domain.Municipality;
import com.ecsite.module.region.domain.Prefecture;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 郵便番号のデータ（日本郵便の「住所の郵便番号（1 レコード 1 行、UTF-8 形式）」の CSV）を、
 * マスターデータの行に変える（docs/design/modules/region.md「最初のデータ」）。<br />
 * 最初のデータを作るタスクと、1 → 10 の取り込みの仕組みが同じ変換を使う。
 */
public final class PostalCodeCsv {

    /**
     * インスタンスを作らない
     */
    private PostalCodeCsv() {
    }

    /**
     * 列の数
     */
    private static final int COLUMNS = 15;

    /**
     * 列の位置：地方公共団体コード
     */
    private static final int CODE = 0;

    /**
     * 列の位置：郵便番号
     */
    private static final int POSTAL_CODE = 2;

    /**
     * 列の位置：都道府県名
     */
    private static final int PREFECTURE_NAME = 6;

    /**
     * 列の位置：市区町村名
     */
    private static final int MUNICIPALITY_NAME = 7;

    /**
     * 列の位置：町域名
     */
    private static final int TOWN_NAME = 8;

    /**
     * 町域名の括弧の中の補足（「（１〜１９丁目）」「（地階・階層不明）」「（次のビルを除く）」など）
     */
    private static final Pattern NOTE = Pattern.compile("（[^）]*）");

    /**
     * 町域がないことを示す町域名
     */
    private static final String NOT_LISTED = "以下に掲載がない場合";

    /**
     * 市区町村名の後に番地が続くことを示す町域名の末尾
     */
    private static final String ADDRESS_NUMBER_FOLLOWS = "の次に番地がくる場合";

    /**
     * 市区町村の全体を示す町域名の末尾（「利島村一円」）。市区町村名に続くときだけ注記とみなす
     */
    private static final String WHOLE_AREA = "一円";

    /**
     * CSV を読み、マスターデータにする
     *
     * @param reader CSV（UTF-8 で読んだもの）
     * @return 都道府県・市区町村・郵便番号の表の行
     * @throws IOException      読めないとき
     * @throws IllegalArgumentException 列の数が合わない行があるとき
     */
    public static PostalCodeData read(Reader reader) throws IOException {
        var prefectures = new TreeMap<String, Prefecture>();
        var municipalities = new TreeMap<String, Municipality>();
        var townsByPostalCode = new TreeMap<String, Map<String, Set<String>>>();

        try (var lines = new BufferedReader(reader)) {
            String line;
            var lineNo = 0;
            while ((line = lines.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                var fields = split(line);
                if (fields.size() != COLUMNS) {
                    throw new IllegalArgumentException("%d 行目の列の数が %d です（%d が必要だ）".formatted(lineNo, fields.size(), COLUMNS));
                }
                var code = fields.get(CODE);
                var prefectureCode = code.substring(0, 2);
                var municipalityName = fields.get(MUNICIPALITY_NAME);
                prefectures.putIfAbsent(prefectureCode, new Prefecture(prefectureCode, fields.get(PREFECTURE_NAME)));
                municipalities.putIfAbsent(code, new Municipality(code, prefectureCode, municipalityName));
                townsByPostalCode.computeIfAbsent(fields.get(POSTAL_CODE), key -> new LinkedHashMap<>())
                        .computeIfAbsent(code, key -> new LinkedHashSet<>())
                        .add(townName(fields.get(TOWN_NAME), municipalityName));
            }
        }

        var postalCodes = new ArrayList<PostalCodeEntry>();
        townsByPostalCode.forEach((postalCode, towns) -> {
            var order = 0;
            for (var entry : towns.entrySet()) {
                for (var town : entry.getValue()) {
                    postalCodes.add(new PostalCodeEntry(postalCode, entry.getKey(), town, ++order));
                }
            }
        });
        return new PostalCodeData(List.copyOf(prefectures.values()), List.copyOf(municipalities.values()),
                List.copyOf(postalCodes));
    }

    /**
     * 町域名から、住所でない注記を除く
     *
     * @param raw              データの町域名
     * @param municipalityName 市区町村名
     * @return 注記を除いた町域名。注記だけなら空
     */
    static String townName(String raw, String municipalityName) {
        var name = NOTE.matcher(raw).replaceAll("");
        if (name.equals(NOT_LISTED) || name.endsWith(ADDRESS_NUMBER_FOLLOWS)
                || name.equals(municipalityName + WHOLE_AREA)) {
            return "";
        }
        return name;
    }

    /**
     * 1 行を列に分ける。値は引用符で囲まれていてもよく、引用符の中に区切りの文字はない
     *
     * @param line 行
     * @return 列の値（引用符と前後の空白を除いたもの）
     */
    private static List<String> split(String line) {
        var fields = new ArrayList<String>();
        for (var field : line.split(",", -1)) {
            var value = field.strip();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1).strip();
            }
            fields.add(value);
        }
        return fields;
    }
}

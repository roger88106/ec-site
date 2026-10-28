package com.ecsite.tools.region;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 地域の最初のデータを入れる Flyway のマイグレーションを、郵便番号のデータから作る
 * （docs/design/modules/region.md「最初のデータ」）。<br />
 * Gradle のタスク {@code generateRegionMigration} から呼ぶ。本番の jar には入らない。<br />
 * 先頭のコメントは、適用済みのマイグレーションと同じ文字にする。適用済みのマイグレーションは変えられない
 * （Flyway のチェックサムが変わるため）ので、コメントが指す設計書のパスは、文書を移す前の
 * {@code docs/backend/modules/region.md} のままである。今の設計書のパスは {@code docs/design/modules/region.md}。
 * 先頭の 3 行がコミット済みのマイグレーションと同じことは、単体テストで確認する。
 */
public final class RegionMigrationGenerator {

    /**
     * 1 つの INSERT にまとめる行の数
     */
    private static final int ROWS_PER_INSERT = 500;

    /**
     * インスタンスを作らない
     */
    private RegionMigrationGenerator() {
    }

    /**
     * マイグレーションを作る
     *
     * @param args 郵便番号のデータの CSV、入れる郵便番号の一覧、書き出すマイグレーション
     * @throws IOException 読み書きに失敗したとき
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException("引数：<郵便番号のデータの CSV> <郵便番号の一覧> <マイグレーション>");
        }
        var csv = Path.of(args[0]);
        if (!Files.exists(csv)) {
            throw new IllegalArgumentException("郵便番号のデータがありません：" + csv + "（地域モジュールの設計書「最初のデータ」）");
        }
        PostalCodeData all;
        try (var reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            all = PostalCodeCsv.read(reader);
        }
        var wanted = readPostalCodes(Path.of(args[1]));
        var data = all.restrictPostalCodes(wanted);

        var found = data.postalCodes().stream().map(entry -> entry.postalCode()).collect(Collectors.toSet());
        var missing = new TreeSet<>(wanted);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("郵便番号のデータにない郵便番号：" + missing);
        }

        Files.writeString(Path.of(args[2]), sql(data, csv.getFileName().toString()), StandardCharsets.UTF_8);
        System.out.printf("都道府県 %d、市区町村 %d、郵便番号 %d（%d 行）%n", data.prefectures().size(),
                data.municipalities().size(), found.size(), data.postalCodes().size());
    }

    /**
     * 郵便番号の一覧を読む。空の行と「#」で始まる行は除く
     *
     * @param path 一覧のファイル
     * @return 郵便番号
     * @throws IOException 読めないとき
     */
    private static Set<String> readPostalCodes(Path path) throws IOException {
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * マイグレーションの SQL
     *
     * @param data   入れるデータ
     * @param source 出典のファイルの名前
     * @return SQL
     */
    static String sql(PostalCodeData data, String source) {
        var out = new StringBuilder();
        out.append("-- 地域の最初のデータ（docs/backend/modules/region.md「最初のデータ」、spec 016 AC-259、260）\n");
        out.append("-- 日本郵便の郵便番号のデータ（").append(source).append("）から、Gradle の generateRegionMigration で作った。手で直さない\n");
        out.append("-- 都道府県と市区町村は全件、郵便番号は src/tools/resources/region/postal-codes.txt の分だけ\n\n");
        insert(out, "region.prefecture (code, name)",
                data.prefectures().stream().map(p -> "(%s, %s)".formatted(text(p.code()), text(p.name()))).toList());
        insert(out, "region.municipality (code, prefecture_code, name)",
                data.municipalities().stream().map(m -> "(%s, %s, %s)".formatted(
                        text(m.code()), text(m.prefectureCode()), text(m.name()))).toList());
        insert(out, "region.postal_code (postal_code, display_order, municipality_code, town_name)",
                data.postalCodes().stream().map(e -> "(%s, %d, %s, %s)".formatted(
                        text(e.postalCode()), e.displayOrder(), text(e.municipalityCode()), text(e.townName()))).toList());
        return out.toString();
    }

    /**
     * 行を INSERT に分けて書く
     *
     * @param out    書き出す先
     * @param target 表と列
     * @param rows   VALUES の行
     */
    private static void insert(StringBuilder out, String target, List<String> rows) {
        var chunks = new ArrayList<List<String>>();
        for (var i = 0; i < rows.size(); i += ROWS_PER_INSERT) {
            chunks.add(rows.subList(i, Math.min(i + ROWS_PER_INSERT, rows.size())));
        }
        for (var chunk : chunks) {
            out.append("INSERT INTO ").append(target).append("\nVALUES ");
            out.append(String.join(",\n       ", chunk));
            out.append(";\n\n");
        }
    }

    /**
     * SQL の文字の値
     *
     * @param value 値
     * @return 引用符で囲んだ値
     */
    private static String text(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}

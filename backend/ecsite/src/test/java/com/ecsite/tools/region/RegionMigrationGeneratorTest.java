package com.ecsite.tools.region;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 地域のマイグレーションの生成の処理の単体テスト
 */
class RegionMigrationGeneratorTest {

    // ============================== 定数 ==============================

    /**
     * 先頭のコメントの行数
     */
    private static final int HEADER_LINES = 3;

    /**
     * コミット済みの最初のデータのマイグレーション
     */
    private static final String COMMITTED_MIGRATION = "/db/migration/V202610200910__region_seed_master.sql";

    // ============================== テスト ==============================

    @Test
    @DisplayName("先頭のコメントは、コミット済みの最初のデータのマイグレーションの先頭の 3 行と同じ")
    void headerMatchesCommittedMigration() throws IOException {
        // 準備：コミット済みのマイグレーションの先頭の行
        List<String> committed;
        try (var in = Objects.requireNonNull(RegionMigrationGeneratorTest.class.getResourceAsStream(COMMITTED_MIGRATION))) {
            committed = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().limit(HEADER_LINES).toList();
        }

        // 操作：空のデータからマイグレーションを作る
        var generated = RegionMigrationGenerator.sql(new PostalCodeData(List.of(), List.of(), List.of()),
                "utf_ken_all.csv").lines().limit(HEADER_LINES).toList();

        // 確認：先頭の行が同じ
        assertThat(generated).isEqualTo(committed);
    }
}

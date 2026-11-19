package com.ecsite;

import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.autoconfigure.task.TaskSchedulingProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static com.ecsite.jooq.common.Tables.EVENT_PUBLICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * アプリケーションコンテキストの起動とマイグレーションの検証
 */
@IntegrationTest
class EcSiteApplicationTests {

    // ============================== Bean ==============================

    /**
     * DB の定義を読む JdbcTemplate
     */
    @Autowired
    JdbcTemplate jdbcTemplate;

    /**
     * 生成したコードで DB を読む jOOQ
     */
    @Autowired
    DSLContext dsl;

    /**
     * 起動したコンテキスト
     */
    @Autowired
    ApplicationContext context;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("起動")
    class Startup {

        @Test
        @DisplayName("Spring のコンテキストが起動する")
        void contextLoads() {
            // 確認：起動に失敗すると、このテストの前に失敗する
        }

        @Test
        @DisplayName("定期ジョブの切り替え（ecsite.scheduling.enabled）が false なら、定期ジョブを自動では動かさない")
        void schedulingIsDisabled() {
            // 操作と確認：@Scheduled を動かす部品がない
            // @EnableScheduling が 1 つでも有効だと、@Scheduled のジョブがテストと並んで動き、テストが呼ぶジョブを分散ロックで飛ばす
            assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
        }

        @Test
        @DisplayName("スケジューラーのスレッドの数が、定期ジョブの数（6）である")
        void schedulerPoolSizeIsJobCount() {
            // 操作：テストでは定期ジョブが無効でスケジューラーの Bean がないので、スケジューラーを作る設定の値を束縛する
            var properties = Binder.get(context.getEnvironment())
                    .bind("spring.task.scheduling", TaskSchedulingProperties.class)
                    .get();

            // 確認：定期ジョブの数と同じ。ジョブが互いを待たない
            assertThat(properties.getPool().getSize()).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("マイグレーション")
    class Migration {

        @Test
        @DisplayName("モジュールごとの schema が作られる")
        void schemasAreCreated() {
            // 操作：schema の一覧を読む
            var schemas = jdbcTemplate.queryForList(
                    "SELECT schema_name FROM information_schema.schemata", String.class);

            // 確認：共通の schema（common）と、schema を持つ全てのモジュールの schema がある
            assertThat(schemas).contains("common", "tax", "catalog", "media", "inventory", "cart", "ordering",
                    "member", "payment", "shipping", "notification", "reporting", "region");
        }

        @Test
        @DisplayName("イベント発行記録のテーブルが common schema に作られる")
        void eventPublicationTableIsCreated() {
            // 操作：common schema の event_publication のテーブルを数える
            var count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'common' AND table_name = 'event_publication'",
                    Integer.class);

            // 確認：1 つ
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("jOOQ の生成コードでテーブルにアクセスできる")
        void generatedJooqCodeCanAccessTable() {
            // 操作と確認：生成したテーブルの定義で数えられる
            // 件数は、先に動いたテスト（ロールバックしないもの）が発行したイベントで変わるので見ない
            assertThatCode(() -> dsl.fetchCount(EVENT_PUBLICATION)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("すべてのテーブルに論理名のコメントがある")
        void everyTableHasLogicalName() {
            // 操作：コメントのないテーブルを集める（PostgreSQL のシステムの schema と、Flyway の履歴のテーブルは除く）
            var tables = jdbcTemplate.queryForList("""
                    SELECT n.nspname || '.' || c.relname
                      FROM pg_class c
                      JOIN pg_namespace n ON n.oid = c.relnamespace
                     WHERE c.relkind IN ('r', 'p')
                       AND n.nspname NOT IN ('pg_catalog', 'information_schema')
                       AND n.nspname NOT LIKE 'pg\\_%'
                       AND c.relname <> 'flyway_schema_history'
                       AND obj_description(c.oid, 'pg_class') IS NULL
                    """, String.class);

            // 確認：ない
            assertThat(tables).isEmpty();
        }

        @Test
        @DisplayName("すべてのカラムに論理名のコメントがある")
        void everyColumnHasLogicalName() {
            // 操作：テーブルの検査と同じ範囲の、削除済みでないカラムのうち、コメントのないものを集める
            var columns = jdbcTemplate.queryForList("""
                    SELECT n.nspname || '.' || c.relname || '.' || a.attname
                      FROM pg_attribute a
                      JOIN pg_class c ON c.oid = a.attrelid
                      JOIN pg_namespace n ON n.oid = c.relnamespace
                     WHERE c.relkind IN ('r', 'p')
                       AND a.attnum > 0
                       AND NOT a.attisdropped
                       AND n.nspname NOT IN ('pg_catalog', 'information_schema')
                       AND n.nspname NOT LIKE 'pg\\_%'
                       AND c.relname <> 'flyway_schema_history'
                       AND col_description(c.oid, a.attnum) IS NULL
                    """, String.class);

            // 確認：ない
            assertThat(columns).isEmpty();
        }
    }
}

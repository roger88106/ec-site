package com.ecsite.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 定期ジョブの分散ロックの設定（バックエンドのアーキテクチャの「水平スケール」）。<br />
 * {@code @SchedulerLock} を付けたメソッドは、同じ名前のロックを持つ間、ほかのインスタンスでは動かずに飛ばされる。
 * ロックはスケジューラーからの起動だけでなく、直接の呼び出しにも掛かる（テストからジョブを呼ぶときも同じ）。
 * ロックは無駄な重複を減らすためのもので、業務の正しさは行ロックと冪等な状態の遷移で保つ。
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = SchedulerLockConfig.LOCK_AT_MOST_FOR)
class SchedulerLockConfig {

    /**
     * ロックを持つ時間の上限（インスタンスが止まってもロックが残り続けないように）。<br />
     * ShedLock の注釈は定数しか取らないので、設定の型にせず定数にする。環境で変えることもない。
     */
    static final String LOCK_AT_MOST_FOR = "PT10M";

    /**
     * ロックの表（common.shedlock）を使うロックの provider。<br />
     * ロックの期限は DB の時計で決める。インスタンスの時計がずれていても、ロックの判定がずれないようにするため。
     *
     * @param dataSource データソース
     * @return ロックの provider
     */
    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName("common.shedlock")
                .usingDbTime()
                .build());
    }
}

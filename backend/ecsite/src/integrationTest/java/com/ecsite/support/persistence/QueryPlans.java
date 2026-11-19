package com.ecsite.support.persistence;

import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 検索が使う索引を、実行計画で確認する補助
 *
 * <p>索引の名前が計画に出るだけでは足りない。シーケンシャルスキャンを無効にすると、照合順序が C でない列でも、
 * 索引を全て読んで Filter で絞る計画になり、索引の名前が計画に出るため。
 * 索引を範囲の条件（Index Cond）で使うことを、{@link #indexRangeCondition} の正規表現で確認する。
 *
 * <p>条件の式はテストに書かず、アプリが実行する SQL を集めて検査する。
 * 理由：検索の SQL の形を変えても、同じテストで確認するため。テストのためにリポジトリの可視性を広げないため。
 *
 * <p>行がなくても、シーケンシャルスキャンを無効にすれば、使える索引があれば計画に選ばれる。
 * 設定はトランザクションの中だけで有効なので、テストごとにロールバックするテストで使う。
 */
public final class QueryPlans {

    /**
     * インスタンスを作らない。
     */
    private QueryPlans() {
    }

    /**
     * シーケンシャルスキャンを無効にして action を実行し、実行された各 Query の実行計画を返す
     *
     * <p>action には、実行された Query を集める DSLContext を渡す。リポジトリは、この DSLContext で組む。
     *
     * @param dsl    トランザクションの接続で動く DSLContext
     * @param action 検索を実行する処理（引数は、実行された Query を集める DSLContext）
     * @return 実行された各 Query（一覧と件数など）の実行計画。実行順
     */
    public static List<String> capture(DSLContext dsl, Consumer<DSLContext> action) {
        dsl.execute("SET LOCAL enable_seqscan = off");
        List<Query> executed = new ArrayList<>();
        DSLContext recording = dsl.configuration().derive(new ExecuteListener() {
            @Override
            public void executeStart(ExecuteContext ctx) {
                executed.add(ctx.query());
            }
        }).dsl();
        action.accept(recording);
        return executed.stream().map(query -> dsl.explain(query).plan()).toList();
    }

    /**
     * 索引を範囲の条件で使う計画を探す正規表現を返す
     *
     * <p>索引の名前がある行の次の行に、Index Cond と列の名前が出る形。索引を全て読んで Filter で絞る計画には、Index Cond の行がない。
     *
     * @param indexName 索引の名前
     * @param column    範囲の条件に出る列の名前
     * @return 正規表現
     */
    public static Pattern indexRangeCondition(String indexName, String column) {
        return Pattern.compile("(?s)%s[^\\n]*\\n[^\\n]*Index Cond:[^\\n]*%s".formatted(indexName, column));
    }
}

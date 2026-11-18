package com.ecsite.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.MethodInvocationTree;

/**
 * テストが entity と集約を等価（{@code equals}）で比べる書き方を見つける検査
 *
 * <p>entity と集約の {@code equals} は識別子だけで比べるので、等価で比べると識別子以外の項目の違いを見落とす。
 * 根拠はバックエンドのテストの書き方の「テストの本文の書き方」。
 * 対象はテストのソースセット（test、integrationTest、testFixtures）のコンパイルだけで、{@code build.gradle.kts} で有効にする。
 *
 * <p>判定の本体は未実装で、今は何も検出しない。
 */
@BugPattern(
        name = "EntityEqualityAssertion",
        summary = "entity と集約を等価で比べている。識別子だけで一致するので、項目ごとか usingRecursiveComparison で比べる",
        severity = BugPattern.SeverityLevel.ERROR)
public final class EntityEqualityAssertion extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        return Description.NO_MATCH;
    }
}

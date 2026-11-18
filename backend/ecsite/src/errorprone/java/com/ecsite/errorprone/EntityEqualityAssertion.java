package com.ecsite.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Type;

import java.util.List;
import java.util.Set;
import javax.lang.model.type.TypeKind;

import static com.google.errorprone.matchers.Matchers.instanceMethod;
import static com.google.errorprone.matchers.Matchers.staticMethod;

/**
 * テストが entity と集約を等価（{@code equals}）で比べる書き方を見つける検査
 *
 * <p>entity と集約の {@code equals} は識別子だけで比べるので、等価で比べると識別子以外の項目の違いを見落とす。
 * 根拠はバックエンドのテストの書き方の「テストの本文の書き方」。
 * 対象はテストのソースセット（test、integrationTest、testFixtures）のコンパイルだけで、{@code build.gradle.kts} で有効にする。
 *
 * <p>判定：
 * <ul>
 *   <li>対象の型は、jMolecules の {@code Entity}・{@code AggregateRoot} の注釈が付いたクラスと、基底クラス
 *       {@code com.ecsite.common.model.base.AggregateRoot} とその継承先。
 *       型が対象の型を含むとは、型そのもの、配列の要素、総称型の型引数（入れ子もたどる）のどれかが対象の型であること。
 *       型変数とワイルドカードは上限の型で見る。</li>
 *   <li>AssertJ の assert の、等価で比べるメソッド（{@link #ASSERTJ_EQUALITY_METHODS}）の引数の型が対象の型を含むとき。
 *       連鎖の途中に名前が {@code using} で始まる呼び出しがあれば、比べ方を替えているので検出しない。</li>
 *   <li>JUnit の {@code assertEquals} の系統、{@code equals}（メソッド参照を含む）、{@code Objects.equals}、
 *       {@code Collection} の {@code contains} の系統の引数の型が対象の型を含むとき。</li>
 *   <li>参照で比べる {@code isSameAs} の系統、Mockito、対象の型を成分に持つ record は検出しない。</li>
 * </ul>
 */
@BugPattern(
        name = "EntityEqualityAssertion",
        summary = "entity と集約を等価で比べている。識別子だけで一致するので、項目ごとか usingRecursiveComparison で比べる",
        severity = BugPattern.SeverityLevel.ERROR)
public final class EntityEqualityAssertion extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher, BugChecker.MemberReferenceTreeMatcher {

    private static final long serialVersionUID = 1L;

    // ============================== 定数 ==============================

    /** 対象の型の注釈（jMolecules） */
    private static final List<String> ENTITY_ANNOTATIONS = List.of(
            "org.jmolecules.ddd.annotation.Entity",
            "org.jmolecules.ddd.annotation.AggregateRoot");

    /** 対象の型の基底クラス。継承先も対象にする */
    private static final String AGGREGATE_ROOT_BASE = "com.ecsite.common.model.base.AggregateRoot";

    /** 検出を打ち切る型の入れ子の深さ（型変数の自己参照の対策） */
    private static final int MAX_TYPE_DEPTH = 8;

    /** 比べ方を替える呼び出しの名前の先頭 */
    private static final String COMPARISON_STRATEGY_PREFIX = "using";

    /** AssertJ の、等価で比べるメソッドの閉じた一覧（否定の形を含む。参照で比べる isSameAs・containsSame は含めない） */
    private static final Set<String> ASSERTJ_EQUALITY_METHODS = Set.of(
            "isEqualTo", "isNotEqualTo", "isIn", "isNotIn",
            "contains", "containsOnly", "containsOnlyOnce", "containsExactly", "containsExactlyInAnyOrder",
            "containsExactlyElementsOf", "containsExactlyInAnyOrderElementsOf", "containsOnlyElementsOf",
            "containsAll", "containsAnyOf", "containsAnyElementsOf", "containsSequence", "containsSubsequence",
            "doesNotContain", "doesNotContainAnyElementsOf", "doesNotContainSequence", "doesNotContainSubsequence",
            "startsWith", "endsWith", "isSubsetOf", "hasSameElementsAs",
            "containsEntry", "doesNotContainEntry", "containsValue", "containsValues", "doesNotContainValue",
            "containsKey", "containsKeys", "containsOnlyKeys", "doesNotContainKey", "doesNotContainKeys",
            "containsAllEntriesOf", "containsExactlyEntriesOf", "hasValue");

    /** JUnit の、等価で比べるメソッド */
    private static final Set<String> JUNIT_EQUALITY_METHODS = Set.of(
            "assertEquals", "assertNotEquals", "assertIterableEquals", "assertArrayEquals");

    /** {@code Collection} の、等価で探すメソッド */
    private static final Set<String> COLLECTION_EQUALITY_METHODS = Set.of(
            "contains", "containsAll", "indexOf", "remove");

    private static final Matcher<ExpressionTree> ASSERTJ_EQUALITY_CALL = instanceMethod()
            .onDescendantOf("org.assertj.core.api.Assert")
            .namedAnyOf(ASSERTJ_EQUALITY_METHODS);

    private static final Matcher<ExpressionTree> JUNIT_EQUALITY_CALL = staticMethod()
            .onClassAny("org.junit.jupiter.api.Assertions", "org.junit.Assert")
            .namedAnyOf(JUNIT_EQUALITY_METHODS);

    private static final Matcher<ExpressionTree> OBJECT_EQUALS_CALL = instanceMethod()
            .anyClass()
            .named("equals")
            .withParameters("java.lang.Object");

    private static final Matcher<ExpressionTree> OBJECTS_EQUALS_CALL = staticMethod()
            .onClass("java.util.Objects")
            .named("equals");

    private static final Matcher<ExpressionTree> COLLECTION_EQUALITY_CALL = instanceMethod()
            .onDescendantOf("java.util.Collection")
            .namedAnyOf(COLLECTION_EQUALITY_METHODS);

    // ============================== 検査 ==============================

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (ASSERTJ_EQUALITY_CALL.matches(tree, state)) {
            return changesComparisonStrategy(tree) || !anyArgumentContainsEntity(tree, state)
                    ? Description.NO_MATCH
                    : describeMatch(tree);
        }
        boolean equalityCall = JUNIT_EQUALITY_CALL.matches(tree, state)
                || OBJECT_EQUALS_CALL.matches(tree, state)
                || OBJECTS_EQUALS_CALL.matches(tree, state)
                || COLLECTION_EQUALITY_CALL.matches(tree, state);
        return equalityCall && anyArgumentContainsEntity(tree, state) ? describeMatch(tree) : Description.NO_MATCH;
    }

    @Override
    public Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
        var symbol = ASTHelpers.getSymbol(tree);
        if (symbol == null) {
            return Description.NO_MATCH;
        }
        String name = symbol.getSimpleName().toString();
        Type qualifier = ASTHelpers.getType(tree.getQualifierExpression());
        boolean equalityReference = "equals".equals(name)
                || COLLECTION_EQUALITY_METHODS.contains(name) && isCollection(qualifier, state);
        return equalityReference && containsEntity(qualifier, state, 0) ? describeMatch(tree) : Description.NO_MATCH;
    }

    // ============================== 判定の補助 ==============================

    /**
     * assert の連鎖を受け手の方へたどり、名前が {@code using} で始まる呼び出しがあるかを返す。
     *
     * @param call 等価で比べる呼び出し
     * @return 比べ方を替える呼び出しがあるとき true
     */
    private static boolean changesComparisonStrategy(MethodInvocationTree call) {
        ExpressionTree receiver = ASTHelpers.getReceiver(call);
        while (receiver instanceof MethodInvocationTree previous) {
            var symbol = ASTHelpers.getSymbol(previous);
            if (symbol != null && symbol.getSimpleName().toString().startsWith(COMPARISON_STRATEGY_PREFIX)) {
                return true;
            }
            receiver = ASTHelpers.getReceiver(previous);
        }
        return false;
    }

    private static boolean anyArgumentContainsEntity(MethodInvocationTree call, VisitorState state) {
        for (ExpressionTree argument : call.getArguments()) {
            // ラムダとメソッド参照の型は関数型インターフェースで、比べる値ではない
            if (argument.getKind() == Tree.Kind.LAMBDA_EXPRESSION || argument.getKind() == Tree.Kind.MEMBER_REFERENCE) {
                continue;
            }
            if (containsEntity(ASTHelpers.getType(argument), state, 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCollection(Type type, VisitorState state) {
        Type collection = state.getTypeFromString("java.util.Collection");
        return type != null && collection != null && ASTHelpers.isSubtype(type, collection, state);
    }

    /**
     * 型が対象の型を含むかを返す。型そのもの、配列の要素、総称型の型引数（入れ子もたどる）、型変数とワイルドカードの上限を見る。
     *
     * @param type  調べる型（null のときは含まない）
     * @param state 解析の状態
     * @param depth 今の入れ子の深さ
     * @return 含むとき true
     */
    private static boolean containsEntity(Type type, VisitorState state, int depth) {
        if (type == null || depth > MAX_TYPE_DEPTH) {
            return false;
        }
        return switch (type.getKind()) {
            case ARRAY -> containsEntity(state.getTypes().elemtype(type), state, depth + 1);
            case TYPEVAR -> containsEntity(type.getUpperBound(), state, depth + 1);
            case WILDCARD -> containsEntity(state.getTypes().wildUpperBound(type), state, depth + 1);
            case DECLARED -> isEntity(type, state)
                    || type.getTypeArguments().stream().anyMatch(argument -> containsEntity(argument, state, depth + 1));
            default -> false;
        };
    }

    /** クラスか継承元のクラスに、対象の注釈が付いている、または基底クラスであるとき true */
    private static boolean isEntity(Type type, VisitorState state) {
        for (Type current = type; current != null && current.getKind() == TypeKind.DECLARED;
                current = state.getTypes().supertype(current)) {
            if (AGGREGATE_ROOT_BASE.contentEquals(current.tsym.getQualifiedName())) {
                return true;
            }
            for (String annotation : ENTITY_ANNOTATIONS) {
                if (ASTHelpers.hasAnnotation(current.tsym, annotation, state)) {
                    return true;
                }
            }
        }
        return false;
    }
}

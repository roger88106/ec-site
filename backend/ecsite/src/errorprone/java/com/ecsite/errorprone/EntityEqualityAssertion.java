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
 *       型が対象の型を含むとは、型そのもの、配列の要素、要素や値を持つ型（{@code Iterable}、{@code Iterator}、
 *       {@code BaseStream}、{@code Optional}、{@code Map}、{@code Map.Entry}）の型引数（入れ子もたどる）のどれかが対象の型であること。
 *       型変数とワイルドカードは上限の型で見る。{@code Class}・{@code Comparator}・{@code Function} などの型引数は対象を含まないとみなす。</li>
 *   <li>AssertJ の assert の、等価で比べるメソッド（{@link #ASSERTJ_EQUALITY_METHODS}）の引数の型が対象の型を含むとき。
 *       引数の {@code tuple(...)} は、その引数の型を見る。
 *       連鎖の途中で名前が {@code using} で始まる呼び出しが比べ方を替えているときは検出しない。
 *       ただし、比べ方を {@code equals} に戻す {@code usingDefault} で始まる呼び出しより後は検出する。
 *       {@code RecursiveComparisonAssert} の比較は、項目ごとの比べ方なので検出しない。</li>
 *   <li>Hamcrest の matcher を作る static メソッド、JUnit の {@code assertEquals} の系統、{@code equals}（メソッド参照を含む）、
 *       {@code Objects.equals}、{@code Collection} と {@code Map} の探すメソッドの引数の型が対象の型を含むとき。</li>
 *   <li>参照で比べる {@code isSameAs} の系統、Mockito、対象の型を成分に持つ record は検出しない。</li>
 * </ul>
 */
@BugPattern(
        name = "EntityEqualityAssertion",
        summary = "entity と集約を等価で比べている。識別子だけで一致するので、項目ごとか usingRecursiveComparison で比べる",
        severity = BugPattern.SeverityLevel.ERROR)
public final class EntityEqualityAssertion extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher, BugChecker.MemberReferenceTreeMatcher {

    /** シリアライズのバージョン */
    private static final long serialVersionUID = 1L;

    // ============================== 定数 ==============================

    /** 対象の型の注釈（jMolecules） */
    private static final List<String> ENTITY_ANNOTATIONS = List.of(
            "org.jmolecules.ddd.annotation.Entity",
            "org.jmolecules.ddd.annotation.AggregateRoot");

    /** 対象の型の基底クラス。継承先も対象にする */
    private static final String AGGREGATE_ROOT_BASE = "com.ecsite.common.model.base.AggregateRoot";

    /** 型引数をたどる、要素や値を持つ型。これ以外の総称型（{@code Class}・{@code Comparator}・{@code Function} など）は型引数をたどらない */
    private static final List<String> VALUE_HOLDER_TYPES = List.of(
            "java.lang.Iterable", "java.util.Iterator", "java.util.stream.BaseStream",
            "java.util.Optional", "java.util.Map", "java.util.Map.Entry");

    /** 検出を打ち切る型の入れ子の深さ（型変数の自己参照の対策） */
    private static final int MAX_TYPE_DEPTH = 8;

    /** 比べ方を替える呼び出しの名前の先頭 */
    private static final String COMPARISON_STRATEGY_PREFIX = "using";

    /** 比べ方を {@code equals} に戻す呼び出しの名前の先頭 */
    private static final String DEFAULT_STRATEGY_PREFIX = "usingDefault";

    /** AssertJ の、等価で比べるメソッドの閉じた一覧（否定の形を含む。参照で比べる isSameAs・containsSame は含めない） */
    private static final Set<String> ASSERTJ_EQUALITY_METHODS = Set.of(
            "isEqualTo", "isNotEqualTo", "isIn", "isNotIn",
            "contains", "containsOnly", "containsOnlyOnce", "containsExactly", "containsExactlyInAnyOrder",
            "containsExactlyElementsOf", "containsExactlyInAnyOrderElementsOf", "containsOnlyElementsOf",
            "containsOnlyOnceElementsOf",
            "containsAll", "containsAnyOf", "containsAnyElementsOf", "containsSequence", "containsSubsequence",
            "doesNotContain", "doesNotContainAnyElementsOf", "doesNotContainSequence", "doesNotContainSubsequence",
            "startsWith", "endsWith", "isSubsetOf", "hasSameElementsAs",
            "containsEntry", "doesNotContainEntry", "containsValue", "containsValues", "doesNotContainValue",
            "containsKey", "containsKeys", "containsOnlyKeys", "doesNotContainKey", "doesNotContainKeys",
            "containsAllEntriesOf", "containsExactlyEntriesOf", "containsExactlyInAnyOrderEntriesOf", "hasValue",
            "returns", "doesNotReturn", "hasFieldOrPropertyWithValue",
            "hasSameHashCodeAs", "doesNotHaveSameHashCodeAs");

    /** Hamcrest の、等価で比べる matcher を作る static メソッド */
    private static final Set<String> HAMCREST_EQUALITY_METHODS = Set.of(
            "equalTo", "is", "not", "isIn", "in", "oneOf", "hasItem", "hasItems",
            "contains", "containsInAnyOrder", "containsInRelativeOrder",
            "arrayContaining", "arrayContainingInAnyOrder", "hasItemInArray",
            "hasEntry", "hasKey", "hasValue");

    /** JUnit の、等価で比べるメソッド */
    private static final Set<String> JUNIT_EQUALITY_METHODS = Set.of(
            "assertEquals", "assertNotEquals", "assertIterableEquals", "assertArrayEquals");

    /** {@code Collection} の、等価で探すメソッド */
    private static final Set<String> COLLECTION_EQUALITY_METHODS = Set.of(
            "contains", "containsAll", "indexOf", "lastIndexOf", "remove", "removeAll", "retainAll");

    /** {@code Map} の、等価で探すメソッド */
    private static final Set<String> MAP_EQUALITY_METHODS = Set.of(
            "containsValue", "containsKey", "get", "remove");

    /** AssertJ の、等価で比べるメソッドの呼び出し */
    private static final Matcher<ExpressionTree> ASSERTJ_EQUALITY_CALL = instanceMethod()
            .onDescendantOf("org.assertj.core.api.Assert")
            .namedAnyOf(ASSERTJ_EQUALITY_METHODS);

    /** 受け手が {@code RecursiveComparisonAssert} の呼び出し */
    private static final Matcher<ExpressionTree> RECURSIVE_COMPARISON_CALL = instanceMethod()
            .onDescendantOf("org.assertj.core.api.RecursiveComparisonAssert")
            .withAnyName();

    /** AssertJ の tuple を作る呼び出し */
    private static final Matcher<ExpressionTree> TUPLE_CALL = staticMethod()
            .onClassAny("org.assertj.core.api.Assertions", "org.assertj.core.groups.Tuple")
            .named("tuple");

    /** Hamcrest の、等価で比べる matcher を作る呼び出し */
    private static final Matcher<ExpressionTree> HAMCREST_EQUALITY_CALL = staticMethod()
            .onClassAny("org.hamcrest.Matchers", "org.hamcrest.CoreMatchers", "org.hamcrest.core.Is",
                    "org.hamcrest.core.IsEqual")
            .namedAnyOf(HAMCREST_EQUALITY_METHODS);

    /** JUnit の、等価で比べる呼び出し */
    private static final Matcher<ExpressionTree> JUNIT_EQUALITY_CALL = staticMethod()
            .onClassAny("org.junit.jupiter.api.Assertions", "org.junit.Assert")
            .namedAnyOf(JUNIT_EQUALITY_METHODS);

    /** {@code equals} の呼び出し */
    private static final Matcher<ExpressionTree> OBJECT_EQUALS_CALL = instanceMethod()
            .anyClass()
            .named("equals")
            .withParameters("java.lang.Object");

    /** {@code Objects.equals} の呼び出し */
    private static final Matcher<ExpressionTree> OBJECTS_EQUALS_CALL = staticMethod()
            .onClass("java.util.Objects")
            .named("equals");

    /** {@code Collection} の、等価で探す呼び出し */
    private static final Matcher<ExpressionTree> COLLECTION_EQUALITY_CALL = instanceMethod()
            .onDescendantOf("java.util.Collection")
            .namedAnyOf(COLLECTION_EQUALITY_METHODS);

    /** {@code Map} の、等価で探す呼び出し */
    private static final Matcher<ExpressionTree> MAP_EQUALITY_CALL = instanceMethod()
            .onDescendantOf("java.util.Map")
            .namedAnyOf(MAP_EQUALITY_METHODS);

    // ============================== 検査 ==============================

    /** {@inheritDoc} */
    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (ASSERTJ_EQUALITY_CALL.matches(tree, state)) {
            // 項目ごとの比べ方の assert と、連鎖の途中で比べ方を替えた assert は、等価で比べない
            return RECURSIVE_COMPARISON_CALL.matches(tree, state)
                    || changesComparisonStrategy(tree)
                    || !anyArgumentContainsEntity(tree, state)
                    ? Description.NO_MATCH
                    : describeMatch(tree);
        }
        boolean equalityCall = HAMCREST_EQUALITY_CALL.matches(tree, state)
                || JUNIT_EQUALITY_CALL.matches(tree, state)
                || OBJECT_EQUALS_CALL.matches(tree, state)
                || OBJECTS_EQUALS_CALL.matches(tree, state)
                || COLLECTION_EQUALITY_CALL.matches(tree, state)
                || MAP_EQUALITY_CALL.matches(tree, state);
        return equalityCall && anyArgumentContainsEntity(tree, state) ? describeMatch(tree) : Description.NO_MATCH;
    }

    /** {@inheritDoc} */
    @Override
    public Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
        var symbol = ASTHelpers.getSymbol(tree);
        if (symbol == null) {
            return Description.NO_MATCH;
        }
        String name = symbol.getSimpleName().toString();
        Type qualifier = ASTHelpers.getType(tree.getQualifierExpression());
        boolean equalityReference = "equals".equals(name)
                || COLLECTION_EQUALITY_METHODS.contains(name) && isSubtypeOf(qualifier, "java.util.Collection", state);
        return equalityReference && containsEntity(qualifier, state, 0) ? describeMatch(tree) : Description.NO_MATCH;
    }

    // ============================== 判定の補助 ==============================

    /**
     * assert の連鎖を受け手の方へたどり、比べ方を替える呼び出しがあるかを返す。
     *
     * <p>最も後ろの {@code using} で始まる呼び出しで決める。{@code usingDefault} で始まる呼び出しは比べ方を {@code equals} に戻す。
     *
     * @param call 等価で比べる呼び出し
     * @return 比べ方を替える呼び出しがあるとき true
     */
    private static boolean changesComparisonStrategy(MethodInvocationTree call) {
        ExpressionTree receiver = ASTHelpers.getReceiver(call);
        while (receiver instanceof MethodInvocationTree previous) {
            var symbol = ASTHelpers.getSymbol(previous);
            if (symbol != null) {
                String name = symbol.getSimpleName().toString();
                if (name.startsWith(DEFAULT_STRATEGY_PREFIX)) {
                    return false;
                }
                if (name.startsWith(COMPARISON_STRATEGY_PREFIX)) {
                    return true;
                }
            }
            receiver = ASTHelpers.getReceiver(previous);
        }
        return false;
    }

    /**
     * 呼び出しの引数のどれかの型が、対象の型を含むかを返す。
     *
     * @param call  呼び出し
     * @param state 解析の状態
     * @return 含むとき true
     */
    private static boolean anyArgumentContainsEntity(MethodInvocationTree call, VisitorState state) {
        for (ExpressionTree argument : call.getArguments()) {
            if (argumentContainsEntity(argument, state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 比べる引数が対象の型を含むかを返す。{@code tuple(...)} は、その引数の型を見る。
     *
     * @param argument 比べる引数
     * @param state    解析の状態
     * @return 含むとき true
     */
    private static boolean argumentContainsEntity(ExpressionTree argument, VisitorState state) {
        // ラムダとメソッド参照の型は関数型インターフェースで、比べる値ではない
        if (argument.getKind() == Tree.Kind.LAMBDA_EXPRESSION || argument.getKind() == Tree.Kind.MEMBER_REFERENCE) {
            return false;
        }
        if (argument instanceof MethodInvocationTree call && TUPLE_CALL.matches(call, state)) {
            return anyArgumentContainsEntity(call, state);
        }
        return containsEntity(ASTHelpers.getType(argument), state, 0);
    }

    /**
     * 型が、指定した型の継承先かを返す。
     *
     * @param type  調べる型（null のときは false）
     * @param name  継承元の型の完全修飾名
     * @param state 解析の状態
     * @return 継承先のとき true
     */
    private static boolean isSubtypeOf(Type type, String name, VisitorState state) {
        Type supertype = state.getTypeFromString(name);
        return type != null && supertype != null && ASTHelpers.isSubtype(type, supertype, state);
    }

    /**
     * 型が対象の型を含むかを返す。型そのもの、配列の要素、要素や値を持つ型の型引数（入れ子もたどる）、型変数とワイルドカードの上限を見る。
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
                    || holdsValues(type, state)
                    && type.getTypeArguments().stream().anyMatch(argument -> containsEntity(argument, state, depth + 1));
            default -> false;
        };
    }

    /**
     * 型が、要素や値を持つ型かを返す。
     *
     * @param type  調べる型
     * @param state 解析の状態
     * @return 要素や値を持つ型のとき true
     */
    private static boolean holdsValues(Type type, VisitorState state) {
        return VALUE_HOLDER_TYPES.stream().anyMatch(holder -> isSubtypeOf(type, holder, state));
    }

    /**
     * クラスか継承元のクラスに、対象の注釈が付いている、または基底クラスであるかを返す。
     *
     * @param type  調べる型
     * @param state 解析の状態
     * @return 対象の型のとき true
     */
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

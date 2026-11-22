package com.ecsite.architecture;

import com.ecsite.architecture.sample.identifier.common.model.UuidInCommonModel;
import com.ecsite.architecture.sample.identifier.module.sample.api.ExampleApi;
import com.ecsite.architecture.sample.identifier.module.sample.application.ExampleProperties;
import com.ecsite.architecture.sample.identifier.module.sample.application.model.ExampleCommand;
import com.ecsite.architecture.sample.identifier.module.sample.domain.EventIdCopy;
import com.ecsite.architecture.sample.identifier.module.sample.domain.ExampleId;
import com.ecsite.architecture.sample.identifier.module.sample.domain.ExampleIdWithOther;
import com.ecsite.architecture.sample.identifier.module.sample.domain.UuidConstructor;
import com.ecsite.architecture.sample.identifier.module.sample.domain.UuidField;
import com.ecsite.architecture.sample.identifier.module.sample.domain.UuidMethod;
import com.ecsite.architecture.sample.identifier.module.sample.domain.UuidTypeArguments;
import com.ecsite.architecture.sample.identifier.module.sample.event.ExampleEvent;
import com.ecsite.architecture.sample.identifier.module.sample.infrastructure.UsesUuid;
import com.ecsite.common.model.base.DomainEvent;
import com.ecsite.common.model.base.EntityId;
import com.ecsite.support.ProductionClasses;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 識別子の型の検査
 *
 * <p>domain・application・api・event と共通のモデル（{@code common.model}）の、項目と、メソッド・コンストラクターの引数と戻り値に、
 * {@link UUID} を使わず、識別子の型を使う。型引数（{@code List<UUID>}、{@code Optional<UUID>}、{@code Map} のキーなど）と配列の要素も見る。
 * UUID を使ってよいのは、presentation、infrastructure、共通のクラス（{@code AdvisoryLocks}、{@code CreatedResponses}）と、次の例外だけ。
 * <ul>
 *   <li>{@link EntityId} の実装の項目 {@code value}、メソッド {@code value()}、コンストラクター</li>
 *   <li>イベント ID：{@link DomainEvent} の {@code newEventId()}、項目 {@code eventId}、メソッド {@code eventId()}。
 *       項目 {@code eventId} を持つクラス（イベントと、受け取ったイベントの ID の写しを持つ記録）では、コンストラクターの最初の引数</li>
 *   <li>設定の型（{@link ConfigurationProperties} を付けたクラス）。環境変数の文字列から作るので、使う側で識別子の型に変換する</li>
 * </ul>
 */
class IdentifierTypeTest {

    // ============================== 定数 ==============================

    /**
     * 検査の対象のパッケージ（モジュールの domain・application・api・event と、共通のモデル。それぞれの下を含む）の形
     */
    private static final DescribedPredicate<JavaClass> CHECKED_PACKAGE = JavaClass.Predicates.resideInAnyPackage(
            "..module.*.domain..", "..module.*.application..", "..module.*.api..", "..module.*.event..", "..common.model..");

    // ============================== テスト ==============================

    @Test
    @DisplayName("本番のクラスに、UUID を使う項目・引数・戻り値がない")
    void productionCodeDoesNotUseUuid() {
        // 準備：検査の対象のクラスが 1 件以上ある
        ProductionClasses.requireNotEmpty(checkedClasses(ProductionClasses.handwritten()), "識別子の型の検査の対象のクラス");

        // 操作と確認：違反がない
        assertThat(violations(ProductionClasses.handwritten())).isEmpty();
    }

    @Test
    @DisplayName("規則は、検査の対象のクラスの UUID を使う項目・引数・戻り値・型引数だけに当たる")
    void ruleMatchesUuidInCheckedClasses() {
        // 準備：違反の例と、規則に合う例（識別子の型、イベント ID、設定の型、対象外の infrastructure）
        JavaClasses examples = new ClassFileImporter().importClasses(
                UuidField.class, UuidMethod.class, UuidConstructor.class, UuidTypeArguments.class,
                ExampleId.class, ExampleIdWithOther.class, ExampleEvent.class, EventIdCopy.class, ExampleCommand.class, ExampleApi.class,
                ExampleProperties.class, UuidInCommonModel.class, UsesUuid.class);

        // 操作と確認：違反の例の UUID だけが違反になる
        assertThat(violations(examples).keySet()).containsExactlyInAnyOrder(
                UuidField.class.getName() + ".orderId",
                UuidMethod.class.getName() + ".convert(java.util.UUID)",
                UuidConstructor.class.getName() + ".<init>(java.util.UUID)",
                UuidTypeArguments.class.getName() + ".ids",
                UuidTypeArguments.class.getName() + ".find(java.util.Map)",
                UuidTypeArguments.class.getName() + ".array()",
                ExampleIdWithOther.class.getName() + ".other()",
                ExampleEvent.class.getName() + ".orderId",
                ExampleEvent.class.getName() + ".orderId()",
                ExampleEvent.class.getName() + ".<init>(java.util.UUID, java.time.Instant, java.util.UUID)",
                EventIdCopy.class.getName() + ".orderId",
                EventIdCopy.class.getName() + ".orderId()",
                EventIdCopy.class.getName() + ".<init>(java.util.UUID, java.util.UUID)",
                ExampleCommand.class.getName() + ".productId",
                ExampleCommand.class.getName() + ".productId()",
                ExampleCommand.class.getName() + ".<init>(java.util.UUID)",
                ExampleApi.class.getName() + ".memberId",
                UuidInCommonModel.class.getName() + ".ownerId",
                UuidInCommonModel.class.getName() + ".ownerId()",
                UuidInCommonModel.class.getName() + ".<init>(java.util.UUID)");
    }

    // ============================== 補助 ==============================

    /**
     * 検査の対象のクラスを返す。
     *
     * @param classes 調べるクラス
     * @return 対象のパッケージのクラスのうち、設定の型でないもの
     */
    private static List<JavaClass> checkedClasses(JavaClasses classes) {
        return classes.stream()
                .filter(CHECKED_PACKAGE)
                .filter(javaClass -> !javaClass.isAnnotatedWith(ConfigurationProperties.class))
                .toList();
    }

    /**
     * UUID を使う項目・メソッド・コンストラクターを返す。
     *
     * @param classes 調べるクラス
     * @return 違反の項目・メソッド・コンストラクターの完全な名前から、そのクラスのパッケージの名前への対応（名前の順）
     */
    private static Map<String, String> violations(JavaClasses classes) {
        Map<String, String> found = new TreeMap<>();
        for (JavaClass javaClass : checkedClasses(classes)) {
            javaClass.getFields().stream()
                    .filter(field -> usesUuid(field.getType()))
                    .filter(field -> !isException(javaClass, field))
                    .forEach(field -> add(found, field));
            javaClass.getMethods().stream()
                    .filter(method -> !method.getModifiers().contains(JavaModifier.SYNTHETIC))
                    .filter(method -> usesUuid(method.getReturnType()) || usesUuid(method.getParameterTypes()))
                    .filter(method -> !isException(javaClass, method))
                    .forEach(method -> add(found, method));
            javaClass.getConstructors().stream()
                    .filter(constructor -> !constructor.getModifiers().contains(JavaModifier.SYNTHETIC))
                    .filter(constructor -> !isAllowed(javaClass, constructor))
                    .forEach(constructor -> add(found, constructor));
        }
        return found;
    }

    /**
     * 違反を一覧に加える。
     *
     * @param found  違反の一覧
     * @param member 違反の項目・メソッド・コンストラクター
     */
    private static void add(Map<String, String> found, JavaMember member) {
        found.put(member.getFullName(), member.getOwner().getPackageName());
    }

    /**
     * 型に UUID を使っているかを返す（型引数と配列の要素を含む）。
     *
     * @param type 型
     * @return UUID を含めば true
     */
    private static boolean usesUuid(JavaType type) {
        return type.getAllInvolvedRawTypes().stream().anyMatch(raw -> raw.isEquivalentTo(UUID.class));
    }

    /**
     * 型のどれかに UUID を使っているかを返す。
     *
     * @param types 型の一覧
     * @return UUID を含めば true
     */
    private static boolean usesUuid(List<? extends JavaType> types) {
        return types.stream().anyMatch(IdentifierTypeTest::usesUuid);
    }

    /**
     * 例外の項目か（識別子の型の {@code value}、イベント ID の {@code eventId}）を返す。
     *
     * @param owner 項目を持つクラス
     * @param field 項目
     * @return 例外なら true
     */
    private static boolean isException(JavaClass owner, JavaField field) {
        return isExceptionName(owner, field.getName());
    }

    /**
     * 例外のメソッドか（識別子の型の {@code value()}、イベント ID の {@code eventId()}、{@code DomainEvent.newEventId()}）を返す。
     *
     * @param owner  メソッドを持つクラス
     * @param method メソッド
     * @return 例外なら true
     */
    private static boolean isException(JavaClass owner, JavaMethod method) {
        boolean newEventId = owner.isEquivalentTo(DomainEvent.class) && method.getName().equals("newEventId");
        return newEventId || (method.getRawParameterTypes().isEmpty() && isExceptionName(owner, method.getName()));
    }

    /**
     * 名前が、そのクラスの例外の項目かメソッドの名前かを返す。
     *
     * @param owner クラス
     * @param name  項目かメソッドの名前
     * @return 識別子の型の {@code value}、またはイベント ID を持つクラスの {@code eventId} なら true
     */
    private static boolean isExceptionName(JavaClass owner, String name) {
        return (name.equals("value") && owner.isAssignableTo(EntityId.class))
                || (name.equals("eventId") && hasEventId(owner));
    }

    /**
     * イベント ID を持つクラス（イベントか、項目 {@code eventId} を持つクラス）かを返す。
     *
     * @param owner クラス
     * @return イベント ID を持てば true
     */
    private static boolean hasEventId(JavaClass owner) {
        return owner.isAssignableTo(DomainEvent.class) || owner.tryGetField("eventId").isPresent();
    }

    /**
     * コンストラクターが違反でないかを返す。
     *
     * <p>識別子の型は、UUID を受け取ってよい。イベント ID を持つクラスは、最初の引数がイベント ID なので除き、2 つ目以降に UUID があれば違反にする。
     *
     * @param owner       コンストラクターを持つクラス
     * @param constructor コンストラクター
     * @return 違反でなければ true
     */
    private static boolean isAllowed(JavaClass owner, JavaConstructor constructor) {
        if (owner.isAssignableTo(EntityId.class)) {
            return true;
        }
        List<? extends JavaType> parameters = constructor.getParameterTypes();
        boolean eventIdFirst = hasEventId(owner) && !parameters.isEmpty() && usesUuid(parameters.get(0));
        return !usesUuid(eventIdFirst ? parameters.subList(1, parameters.size()) : parameters);
    }
}

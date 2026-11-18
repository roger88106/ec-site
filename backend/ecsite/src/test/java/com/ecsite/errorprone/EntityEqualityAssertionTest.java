package com.ecsite.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * テストの等価の検査 {@link EntityEqualityAssertion} のテスト
 *
 * <p>テストの入力のソース（{@code example} パッケージ）に検査をかけ、
 * 検出する書き方は直前の行の {@code // BUG: Diagnostic contains:} の期待で、検出しない書き方は期待を書かないことで確認する
 * （期待のない行を検出すると、{@code CompilationTestHelper} が失敗にする）。
 * 入力の中の entity は、jMolecules の注釈を付けた小さなクラス（{@link #MODEL}）で、{@code equals} は識別子だけで比べる。
 * 「main のコードは対象にしない」は、検査を有効にするソースセットの設定（{@code build.gradle.kts}）で決まるので、このテストでは確認しない。
 */
class EntityEqualityAssertionTest {

    // ============================== 定数 ==============================

    /** 検出する行の直前に置く期待。検査の要約の一部 */
    private static final String EXPECT = "        // BUG: Diagnostic contains: 等価で比べている";

    /**
     * 入力の中のモデル。
     * entity と集約は {@code Order}・{@code Product}・{@code ProductImage}・{@code Payment}・{@code Customer}（jMolecules の注釈）と
     * {@code Cart}（基底クラス {@code AggregateRoot} の継承だけ）。
     * 値は record の {@code Money}・{@code PaymentSnapshot}・{@code OrderId}・{@code Result}、
     * entity を成分に持つ record の {@code OrderPlaced}。
     */
    private static final String MODEL = """
            package example;

            import java.util.List;
            import java.util.Optional;
            import java.util.UUID;
            import org.jmolecules.ddd.annotation.Entity;

            record Money(long amount) {}

            record OrderId(UUID value) {}

            record PaymentSnapshot(UUID id, Money total) {}

            record Result(UUID id, Money total) {}

            record OrderPlaced(Order order) {}

            enum Status { OPEN, CLOSED }

            @org.jmolecules.ddd.annotation.AggregateRoot
            class Customer {
                private final UUID id;
                Customer(UUID id) { this.id = id; }
                UUID id() { return id; }
                @Override public boolean equals(Object o) { return o instanceof Customer c && c.id.equals(id); }
                @Override public int hashCode() { return id.hashCode(); }
            }

            @Entity
            class ProductImage {
                private final UUID id;
                ProductImage(UUID id) { this.id = id; }
                UUID id() { return id; }
                @Override public boolean equals(Object o) { return o instanceof ProductImage c && c.id.equals(id); }
                @Override public int hashCode() { return id.hashCode(); }
            }

            @org.jmolecules.ddd.annotation.AggregateRoot
            class Product {
                private final UUID id;
                Product(UUID id) { this.id = id; }
                UUID id() { return id; }
                List<ProductImage> images() { return List.of(); }
                @Override public boolean equals(Object o) { return o instanceof Product c && c.id.equals(id); }
                @Override public int hashCode() { return id.hashCode(); }
            }

            @org.jmolecules.ddd.annotation.AggregateRoot
            class Payment {
                private final UUID id;
                Payment(UUID id) { this.id = id; }
                UUID id() { return id; }
                PaymentSnapshot toSnapshot() { return new PaymentSnapshot(id, new Money(0)); }
                @Override public boolean equals(Object o) { return o instanceof Payment c && c.id.equals(id); }
                @Override public int hashCode() { return id.hashCode(); }
            }

            class Item {
                Product product() { return new Product(UUID.randomUUID()); }
                UUID variantId() { return UUID.randomUUID(); }
                int quantity() { return 1; }
            }

            @org.jmolecules.ddd.annotation.AggregateRoot
            class Order {
                private final UUID id;
                Order(UUID id) { this.id = id; }
                UUID id() { return id; }
                OrderId orderId() { return new OrderId(id); }
                Money total() { return new Money(0); }
                Status status() { return Status.OPEN; }
                long version() { return 0; }
                Customer customer() { return new Customer(id); }
                List<Item> items() { return List.of(); }
                @Override public boolean equals(Object o) { return o instanceof Order c && c.id.equals(id); }
                @Override public int hashCode() { return id.hashCode(); }
            }

            class Cart extends com.ecsite.common.model.base.AggregateRoot {
                @Override public long version() { return 0; }
            }

            interface Repository {
                Optional<Order> findById(UUID id);
                Optional<UUID> findIdByMemberId(UUID memberId);
                Optional<Payment> lockById(UUID id);
                void save(Order order);
            }
            """;

    /** 入力の中の、確認の対象の変数（フィールド）。入力の確認の文は、これを使う */
    private static final String FIELDS = """
            Order order = new Order(UUID.randomUUID());
            Order other = new Order(UUID.randomUUID());
            Order expected = new Order(UUID.randomUUID());
            Order loaded = new Order(UUID.randomUUID());
            Order extra = new Order(UUID.randomUUID());
            Order a = new Order(UUID.randomUUID());
            Order b = new Order(UUID.randomUUID());
            Product product = new Product(UUID.randomUUID());
            Payment payment = new Payment(UUID.randomUUID());
            Customer customer = new Customer(UUID.randomUUID());
            ProductImage image = new ProductImage(UUID.randomUUID());
            Cart cart = new Cart();
            com.ecsite.common.model.base.AggregateRoot aggregate = cart;
            UUID id = UUID.randomUUID();
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<Order> orders = List.of();
            List<Order> expectedOrders = List.of();
            List<Order> all = List.of();
            Set<Order> orderSet = Set.of();
            Order[] orderArray = new Order[0];
            Order[] expectedArray = new Order[0];
            List<ProductImage> images = List.of();
            List<List<Order>> groups = List.of();
            Map<String, List<Order>> byCategory = Map.of();
            Map<UUID, Order> ordersById = Map.of();
            Map<String, UUID> idByNumber = Map.of();
            Map<String, String> settings = Map.of();
            Repository repository = null;
            Optional<Payment> optionalPayment = Optional.empty();
            AtomicInteger calls = new AtomicInteger();
            String url = "";
            """;

    // ============================== テスト ==============================

    @Test
    @DisplayName("assert の対象が entity と集約のとき、等価で比べる呼び出しを検出する")
    void detectsEntityComparedByEquality() {
        // 準備：entity と集約を、等価・否定・包含の系統で比べる文
        var helper = helper(detect(
                "assertThat(order).isEqualTo(expected);",
                "assertThat(product).isNotEqualTo(other);",
                "assertThat(order).isIn(a, b);",
                "assertThat(order).isNotIn(orders);",
                "assertThat(payment).isEqualTo(new Payment(id));",
                "assertThat(customer).isEqualTo(order.customer());",
                "assertThat(image).isEqualTo(images.get(0));",
                "assertThat(order).as(\"説明\").isEqualTo(expected);",
                "assertThat(order).isNotNull().isEqualTo(expected);",
                "assertThat(order).describedAs(\"説明\").isNotEqualTo(expected);"));

        // 操作と確認：全ての行を検出する
        helper.doTest();
    }

    @Test
    @DisplayName("一覧・Set・配列・Stream の要素が entity のとき、要素の等価で比べる呼び出しを検出する")
    void detectsEntityElementsComparedByEquality() {
        // 準備：要素が entity の、一覧・Set・配列・Stream を比べる文
        var helper = helper(detect(
                "assertThat(images).containsExactly(new ProductImage(id));",
                "assertThat(orders).containsExactlyElementsOf(expectedOrders);",
                "assertThat(orders).isEqualTo(List.of(a, b));",
                "assertThat(orders).isNotEqualTo(expectedOrders);",
                "assertThat(orders).hasSameElementsAs(expectedOrders);",
                "assertThat(orders).startsWith(a);",
                "assertThat(orders).endsWith(b);",
                "assertThat(orders).isSubsetOf(all);",
                "assertThat(orders).contains(a);",
                "assertThat(orders).containsOnly(a, b);",
                "assertThat(orders).containsOnlyOnce(a);",
                "assertThat(orders).containsExactlyInAnyOrder(a, b);",
                "assertThat(orders).containsAll(expectedOrders);",
                "assertThat(orders).containsAnyOf(a, b);",
                "assertThat(orders).containsSequence(a, b);",
                "assertThat(orders).containsSubsequence(a, b);",
                "assertThat(orders).doesNotContain(a);",
                "assertThat(orders).doesNotContainAnyElementsOf(expectedOrders);",
                "assertThat(orders).containsExactlyInAnyOrderElementsOf(expectedOrders);",
                "assertThat(orderSet).contains(a);",
                "assertThat(orderArray).containsExactly(a, b);",
                "assertThat(orderArray).isEqualTo(expectedArray);",
                "assertThat(orders.stream()).containsExactly(a);"));

        // 操作と確認：全ての行を検出する
        helper.doTest();
    }

    @Test
    @DisplayName("Optional の中身が entity のとき、中身の等価で比べる呼び出しを検出する")
    void detectsOptionalEntityComparedByEquality() {
        // 準備：Optional の中身を比べる文
        var helper = helper(detect(
                "assertThat(repository.findById(id)).contains(order);",
                "assertThat(repository.findById(id)).hasValue(order);",
                "assertThat(repository.findById(id)).isEqualTo(Optional.of(order));",
                "assertThat(repository.findById(id)).isNotEqualTo(Optional.of(order));"));

        // 操作と確認：全ての行を検出する
        helper.doTest();
    }

    @Test
    @DisplayName("Map の値が entity のとき、値の等価で比べる呼び出しを検出する")
    void detectsMapEntityValueComparedByEquality() {
        // 準備：Map の値を比べる文（値が一覧の Map も含む）
        var helper = helper(detect(
                "assertThat(ordersById).containsEntry(id, order);",
                "assertThat(ordersById).containsValue(order);",
                "assertThat(ordersById).containsValues(a, b);",
                "assertThat(ordersById).isEqualTo(Map.of(id, order));",
                "assertThat(byCategory).containsEntry(\"c\", List.of(a));"));

        // 操作と確認：全ての行を検出する
        helper.doTest();
    }

    @Test
    @DisplayName("型引数の入れ子・型変数・基底クラスの型でも、entity を含む型は検出する")
    void detectsNestedGenericTypeVariableAndBaseClass() {
        // 準備：入れ子の総称型、上限が entity の型変数とワイルドカード、基底クラスの型の変数を比べるメソッド
        var helper = helper("""
                <T extends Order> void typeVariable(T actual, T expectedValue) {
                    // BUG: Diagnostic contains: 等価で比べている
                    assertThat(actual).isEqualTo(expectedValue);
                }

                void wildcard(List<? extends Order> actual, List<? extends Order> expectedValues) {
                    // BUG: Diagnostic contains: 等価で比べている
                    assertThat(actual).first().isEqualTo(expectedValues.get(0));
                }

                void nested() {
                    // BUG: Diagnostic contains: 等価で比べている
                    assertThat(groups).containsExactly(List.of(a));
                    // BUG: Diagnostic contains: 等価で比べている
                    assertThat(Optional.of(List.of(a))).contains(List.of(a));
                }

                void baseClass() {
                    com.ecsite.common.model.base.AggregateRoot base = new Cart();
                    // BUG: Diagnostic contains: 等価で比べている
                    assertThat(aggregate).isEqualTo(base);
                    // BUG: Diagnostic contains: 等価で比べている
                    assertThat(cart).isEqualTo(new Cart());
                }
                """);

        // 操作と確認：全てのメソッドで検出する
        helper.doTest();
    }

    @Test
    @DisplayName("assert の連鎖の途中や取り出した後でも、entity の等価で比べる呼び出しを検出する")
    void detectsAfterIntermediateAssertCalls() {
        // 準備：first・singleElement・get・hasSize・filteredOn などを挟んだ文
        var helper = helper(detect(
                "assertThat(orders).first().isEqualTo(expected);",
                "assertThat(orders).last().isEqualTo(expected);",
                "assertThat(orders).element(1).isEqualTo(expected);",
                "assertThat(orders).singleElement().isEqualTo(expected);",
                "assertThat(orders).hasSize(2).contains(a);",
                "assertThat(orders).isNotEmpty().containsExactly(a);",
                "assertThat(orders).filteredOn(o -> o.status() == Status.OPEN).containsExactly(a);",
                "assertThat(repository.findById(id)).get().isEqualTo(order);",
                "assertThat(repository.findById(id)).isPresent().get().isEqualTo(order);"));

        // 操作と確認：全ての行を検出する
        helper.doTest();
    }

    @Test
    @DisplayName("extracting・flatExtracting・map の結果が entity のとき、結果の等価で比べる呼び出しを検出する")
    void detectsExtractedEntity() {
        // 準備：取り出した結果が entity の文
        var helper = helper(detect(
                "assertThat(orders).extracting(Order::customer).isEqualTo(customer);",
                "assertThat(orders).extracting(Order::customer).containsExactly(customer);",
                "assertThat(orders).flatExtracting(Order::items).extracting(Item::product).containsExactly(product);",
                "assertThat(orders).map(Order::customer).containsExactly(customer);"));

        // 操作と確認：全ての行を検出する
        helper.doTest();
    }

    @Test
    @DisplayName("ラムダの中の assert でも、entity の等価で比べる呼び出しを検出する")
    void detectsInsideLambda() {
        // 準備：ラムダの本体に assert を持つ文
        var helper = helper(detect(
                "assertThat(repository.findById(id)).hasValueSatisfying(o -> assertThat(o).isEqualTo(order));",
                "assertThat(orders).allSatisfy(o -> assertThat(o).isNotEqualTo(extra));",
                "assertThat(orders).noneMatch(o -> o.equals(extra));"));

        // 操作と確認：全ての行を検出する
        helper.doTest();
    }

    @Test
    @DisplayName("JUnit の等価の比較で、引数が entity を含むとき検出する")
    void detectsJUnitEqualityAssertions() {
        // 準備：assertEquals の系統（説明つきを含む）
        var helper = helper(detect(
                "assertEquals(payment, new Payment(id));",
                "assertEquals(a, b, \"説明\");",
                "assertNotEquals(a, b);",
                "assertIterableEquals(expectedOrders, orders);",
                "assertArrayEquals(expectedArray, orderArray);",
                "assertEquals(Optional.of(a), repository.findById(id));"));

        // 操作と確認：全ての行を検出する
        helper.doTest();
    }

    @Test
    @DisplayName("entity の equals の直接の呼び出しを検出する")
    void detectsDirectEqualsCalls() {
        // 準備：equals・Objects.equals・メソッド参照・Collection の contains などの文
        var helper = helper(detect(
                "assertThat(a.equals(b)).isTrue();",
                "assertThat(Objects.equals(a, b)).isTrue();",
                "assertTrue(a.equals(b));",
                "assertFalse(a.equals(b));",
                "assertThat(orders.contains(a)).isTrue();",
                "assertThat(orders.indexOf(a)).isEqualTo(0);",
                "assertThat(orders.containsAll(expectedOrders)).isTrue();",
                "assertThat(orderSet.contains(a)).isTrue();",
                "assertThat(orders).filteredOn(a::equals).hasSize(1);",
                "assertThat(orders.stream().filter(a::equals).count()).isEqualTo(1);"));

        // 操作と確認：全ての行を検出する
        helper.doTest();
    }

    @Test
    @DisplayName("識別子と値は、entity でないので検出しない")
    void ignoresIdentifiersAndValues() {
        // 準備：識別子・値の record・数値・列挙型・スナップショット・識別子と数量の Tuple などを比べる文
        var helper = helper(pass(
                "assertThat(order.id()).isEqualTo(id);",
                "assertThat(order.orderId()).isEqualTo(new OrderId(id));",
                "assertThat(order.total()).isEqualTo(new Money(100));",
                "assertThat(order.version()).isEqualTo(2L);",
                "assertThat(order.status()).isEqualTo(Status.OPEN);",
                "assertThat(payment.toSnapshot()).isEqualTo(new PaymentSnapshot(id, new Money(0)));",
                "assertThat(new Result(order.id(), order.total())).isEqualTo(new Result(id, new Money(0)));",
                "assertThat(a.hashCode()).isEqualTo(b.hashCode());",
                "assertThat(Optional.of(order.status())).contains(Status.OPEN);",
                "assertThat(orders).extracting(Order::id).containsExactly(id1, id2);",
                "assertThat(images).extracting(ProductImage::id).containsExactly(id1);",
                "assertThat(orders).extracting(Order::orderId).containsExactly(new OrderId(id1));",
                "assertThat(order.items()).extracting(Item::variantId, Item::quantity).containsExactly(tuple(id1, 2));",
                "assertThat(order.items()).extracting(item -> item.variantId().toString()).containsExactly(\"x\");",
                "assertThat(orders).hasSize(2);",
                "assertThat(orders).isEmpty();",
                "assertThat(orders).isNotEmpty();",
                "assertThat(orders).doesNotContainNull();",
                "assertEquals(id, payment.id());",
                "assertThat(order).isNotNull();",
                "assertThat(order).isInstanceOf(Order.class);"));

        // 操作と確認：検出しない
        helper.doTest();
    }

    @Test
    @DisplayName("中身が entity でない Optional・Map・配列と、有無の確認は、検出しない")
    void ignoresContainersOfNonEntities() {
        // 準備：中身が識別子・文字列・数値の Optional・Map・配列を比べる文と、有無の確認
        var helper = helper(pass(
                "assertThat(repository.findIdByMemberId(id)).contains(id1);",
                "assertThat(Optional.of(url)).isPresent().isEqualTo(Optional.of(url));",
                "assertThat(settings).containsEntry(\"k\", \"false\");",
                "assertThat(idByNumber).containsEntry(\"N1\", id);",
                "assertThat(ordersById).containsKey(id);",
                "assertThat(ordersById).containsOnlyKeys(id);",
                "assertThat(new Throwable().getStackTrace()).containsExactly(new Throwable().getStackTrace());",
                "assertThat(calls).hasValue(3);",
                "assertThat(repository.lockById(id)).isPresent();",
                "assertThat(repository.lockById(id)).isEmpty();",
                "assertThat(optionalPayment).isNotPresent();"));

        // 操作と確認：検出しない
        helper.doTest();
    }

    @Test
    @DisplayName("比較の方法を替える using から始まる呼び出しが連鎖にあれば、検出しない")
    void ignoresWhenComparisonStrategyIsChanged() {
        // 準備：usingRecursiveComparison などで項目ごとに比べる文（途中・後続の呼び出しを含む）
        var helper = helper(pass(
                "assertThat(order).usingRecursiveComparison().isEqualTo(expected);",
                "assertThat(order).usingRecursiveComparison().ignoringFields(\"version\").isEqualTo(expected);",
                "assertThat(order).usingRecursiveComparison().comparingOnlyFields(\"id\").isNotEqualTo(expected);",
                "assertThat(orders).usingRecursiveFieldByFieldElementComparator().containsExactly(a);",
                "assertThat(orders).usingRecursiveFieldByFieldElementComparatorIgnoringFields(\"version\").containsExactly(a);",
                "assertThat(orders).usingRecursiveFieldByFieldElementComparatorOnFields(\"id\").containsExactly(a);",
                "assertThat(orders).usingElementComparator(Comparator.comparing(Order::id)).containsExactly(a);",
                "assertThat(order).usingComparator(Comparator.comparing(Order::id)).isEqualTo(expected);",
                "assertThat(orders).first().usingRecursiveComparison().isEqualTo(expected);",
                "assertThat(orders).hasSize(1).first().usingRecursiveComparison().isEqualTo(expected);",
                "assertThat(repository.findById(id)).get().usingRecursiveComparison().isEqualTo(order);"));

        // 操作と確認：検出しない
        helper.doTest();
    }

    @Test
    @DisplayName("参照で比べる isSameAs・containsSame・assertSame は、検出しない")
    void ignoresReferenceComparison() {
        // 準備：参照の同一性を確認する文
        var helper = helper(pass(
                "assertThat(order).isSameAs(loaded);",
                "assertThat(order).isNotSameAs(other);",
                "assertThat(repository.findById(id)).containsSame(order);",
                "assertSame(expected, order);",
                "assertNotSame(expected, order);"));

        // 操作と確認：検出しない
        helper.doTest();
    }

    @Test
    @DisplayName("Mockito の呼び出しの確認と設定は、検出しない")
    void ignoresMockitoArgumentMatching() {
        // 準備：verify・when・eq で entity を引数に渡す文
        var helper = helper(pass(
                "verify(repository).save(order);",
                "verify(repository).save(eq(order));",
                "when(repository.findById(id)).thenReturn(Optional.of(order));"));

        // 操作と確認：検出しない
        helper.doTest();
    }

    @Test
    @DisplayName("entity を成分に持つ record の比較は、検出しない")
    void ignoresRecordHoldingEntity() {
        // 準備：成分が entity の record を比べる文
        var helper = helper(pass(
                "assertThat(new OrderPlaced(order)).isEqualTo(new OrderPlaced(expected));",
                "assertEquals(new OrderPlaced(order), new OrderPlaced(expected));"));

        // 操作と確認：検出しない
        helper.doTest();
    }

    @Test
    @DisplayName("equals の仕様を確認するテストは、抑える注釈で検出しない")
    void ignoresWhenSuppressed() {
        // 準備：理由のコメントと @SuppressWarnings を付けた宣言の中で、entity を等価で比べるメソッド
        var helper = helper("""
                // equals の仕様（識別子が同じなら等価）を確かめるテスト
                @SuppressWarnings("EntityEqualityAssertion")
                void sameIdIsEqual() {
                    assertThat(a).isEqualTo(b);
                    assertThat(a.equals(b)).isFalse();
                }
                """);

        // 操作と確認：検出しない
        helper.doTest();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 入力の確認の文を、検出する書き方として並べる。各文の直前に検出の期待を置く。
     *
     * @param statements 1 行で書いた文
     * @return 入力のクラスのメンバー
     */
    private static String detect(String... statements) {
        return "void check() {\n"
                + Stream.of(statements).map(statement -> EXPECT + "\n        " + statement).collect(Collectors.joining("\n"))
                + "\n}\n";
    }

    /**
     * 入力の確認の文を、検出しない書き方として並べる。
     *
     * @param statements 1 行で書いた文
     * @return 入力のクラスのメンバー
     */
    private static String pass(String... statements) {
        return "void check() {\n        " + String.join("\n        ", statements) + "\n}\n";
    }

    /**
     * 検査をかける入力を組む。モデルと、確認の文を持つクラスを渡す。
     *
     * @param members 確認の文を持つ、入力のクラスのメンバー
     * @return 検査のテストの補助
     */
    private CompilationTestHelper helper(String members) {
        var sample = """
                package example;

                import static org.assertj.core.api.Assertions.assertThat;
                import static org.assertj.core.api.Assertions.tuple;
                import static org.junit.jupiter.api.Assertions.assertArrayEquals;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                import static org.junit.jupiter.api.Assertions.assertFalse;
                import static org.junit.jupiter.api.Assertions.assertIterableEquals;
                import static org.junit.jupiter.api.Assertions.assertNotEquals;
                import static org.junit.jupiter.api.Assertions.assertNotSame;
                import static org.junit.jupiter.api.Assertions.assertSame;
                import static org.junit.jupiter.api.Assertions.assertTrue;
                import static org.mockito.ArgumentMatchers.eq;
                import static org.mockito.Mockito.verify;
                import static org.mockito.Mockito.when;

                import java.util.Comparator;
                import java.util.List;
                import java.util.Map;
                import java.util.Objects;
                import java.util.Optional;
                import java.util.Set;
                import java.util.UUID;
                import java.util.concurrent.atomic.AtomicInteger;

                class Sample {
                """
                + FIELDS.indent(4)
                + "\n"
                + members.indent(4)
                + "}\n";
        return CompilationTestHelper.newInstance(EntityEqualityAssertion.class, getClass())
                .addSourceLines("example/Model.java", MODEL.split("\n"))
                .addSourceLines("example/Sample.java", sample.split("\n"));
    }
}

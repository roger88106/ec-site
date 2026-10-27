import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { readFileSync } from 'node:fs'
import { listGitFiles } from './lib/git-files.mjs'
import {
  ASYNC_EVENT_HELPERS,
  EVENT_PUBLICATION_READS,
  loadRules,
  STEP_FINDERS,
  TARGET_FILE,
  TEST_WRITING_DOC,
  buildTableRules,
  findHelperViolations,
  isHelperFile,
  parseHelperTable,
} from './test-helpers.mjs'

const IT = 'backend/ecsite/src/integrationTest/java/com/ecsite/'

/** 違反を「行 規則の名前」で並べる */
const violationsOf = (source, rules = [EVENT_PUBLICATION_READS], file = `${IT}shipping/ShippingFlowTest.java`) =>
  findHelperViolations(file, source, rules).map((hit) => `${hit.line} ${hit.rule}`)

describe('統合テストの補助の外で、イベントの発行の記録を読む操作を呼ばない', () => {
  test('補助の外で、記録を読む操作（find で始まる操作）を呼ぶ行は、違反にする', () => {
    const source = [
      'class ShippingFlowTest {',
      '    @Autowired',
      '    EventPublicationRegistry registry;',
      '',
      '    void test() {',
      '        assertThat(registry.findIncompletePublications()).isEmpty();',
      '        var completed = this.registry.findCompletedPublications();',
      '    }',
      '}',
    ].join('\n')
    assert.deepEqual(violationsOf(source), [`6 ${EVENT_PUBLICATION_READS.name}`, `7 ${EVENT_PUBLICATION_READS.name}`])
  })

  test('メソッドの参照、改行の後の呼び出し、引数や getBean で得た記録の読み取りも、違反にする', () => {
    const source = [
      'class FooTest {',
      '    void test(EventPublicationRegistry publications, ApplicationContext context) {',
      '        Supplier<?> s = publications::findIncompletePublications;',
      '        publications',
      '            .findIncompletePublicationsPublishedBefore(Instant.now());',
      '        context.getBean(EventPublicationRegistry.class).findCompletedPublications();',
      '        var bean = context.getBean(EventPublicationRegistry.class);',
      '        bean.findIncompletePublications();',
      '    }',
      '}',
    ].join('\n')
    assert.deepEqual(
      violationsOf(source).map((entry) => entry.split(' ')[0]),
      ['3', '4', '6', '8'],
    )
  })

  test('補助へ渡すために注入するだけのテストは、違反にしない', () => {
    const source = [
      'class EventCleanupJobTest {',
      '    @Autowired',
      '    EventPublicationRegistry registry;',
      '',
      '    @BeforeEach',
      '    void setUp() {',
      '        publications = new EventPublications(dsl, transactionTemplate, eventPublisher, registry, asyncEvents, clock);',
      '    }',
      '}',
    ].join('\n')
    assert.deepEqual(violationsOf(source), [])
  })

  test('import、コメント、記録の型でない変数の find は、違反にしない', () => {
    const source = [
      'import org.springframework.modulith.events.core.EventPublicationRegistry;',
      '',
      '/** registry.findIncompletePublications() は AsyncEvents が呼ぶ */',
      'class FooTest {',
      '    // registry.findCompletedPublications()',
      '    EventPublicationRegistry registry;',
      '    ProductRepository repository;',
      '    void test() {',
      '        repository.findById(1L);',
      '        registryHelper.findIncompletePublications();',
      '    }',
      '}',
    ].join('\n')
    assert.deepEqual(violationsOf(source), [])
  })

  test('補助（AsyncEvents、EventPublications）の中では、記録を読んでよい', () => {
    const source = ['class AsyncEvents {', '    EventPublicationRegistry registry;', '    void a() { registry.findIncompletePublications(); }', '}'].join('\n')
    for (const file of ASYNC_EVENT_HELPERS) {
      assert.equal(isHelperFile(file, EVENT_PUBLICATION_READS), true, file)
      assert.deepEqual(violationsOf(source, [EVENT_PUBLICATION_READS], file), [], file)
    }
    assert.equal(isHelperFile(`${IT}config/event/EventCleanupJobTest.java`, EVENT_PUBLICATION_READS), false)
    assert.equal(violationsOf(source, [EVENT_PUBLICATION_READS], `${IT}config/event/EventCleanupJobTest.java`).length, 1)
  })

  test('補助の一覧は、リポジトリに実在するファイルを指す', async () => {
    const { existsSync } = await import('node:fs')
    for (const file of ASYNC_EVENT_HELPERS) assert.ok(existsSync(file), file)
  })

  test('対象は統合テストの Java のファイルだけ（本番のコードと単体テストは見ない）', () => {
    assert.equal(TARGET_FILE.test(`${IT}shipping/ShippingFlowTest.java`), true)
    assert.equal(TARGET_FILE.test(`${IT}AsyncEvents.java`), true)
    assert.equal(TARGET_FILE.test('backend/ecsite/src/test/java/com/ecsite/FooTest.java'), false)
    assert.equal(TARGET_FILE.test('backend/ecsite/src/main/java/com/ecsite/Foo.java'), false)
    assert.equal(TARGET_FILE.test(`${IT}shipping/application.yml`), false)
  })
})

describe('規則を足しやすい形', () => {
  test('規則の表は、規則ごとに対象と補助を持ち、違反の報告に規則の名前を添える', () => {
    const rule = {
      name: '試しの規則',
      helpers: [`${IT}FooHelper.java`],
      find: (code) => [...code.matchAll(/forbiddenCall\(/g)].map((match) => match.index),
    }
    const source = ['class A {', '    void t() { forbiddenCall(); }', '}'].join('\n')
    assert.deepEqual(violationsOf(source, [rule]), ['2 試しの規則'])
    assert.deepEqual(violationsOf(source, [rule], `${IT}FooHelper.java`), [])
    // 規則が増えても、補助は規則ごとに決まる
    assert.deepEqual(violationsOf(source, [EVENT_PUBLICATION_READS, rule], `${IT}AsyncEvents.java`), ['2 試しの規則'])
  })

  test('違反の報告に、元の行の文字を添える', () => {
    const source = ['class A {', '    EventPublicationRegistry registry;', '    void t() { registry.findIncompletePublications(); }', '}'].join('\n')
    const [hit] = findHelperViolations(`${IT}FooTest.java`, source, [EVENT_PUBLICATION_READS])
    assert.equal(hit.text, 'void t() { registry.findIncompletePublications(); }')
  })
})

describe('テストの書き方の表から読む規則', () => {
  const HEADER = ['| 手順 | 補助 | その操作そのものを確認するテスト |', '|------|------|----------------------------------|']
  const table = (...rows) => [...HEADER, ...rows].join('\n')
  const ROWS = {
    stock: '| 在庫の作成 | `InventoryTestData` | `JooqStockItemRepositoryTest` |',
    tax: '| 税区分の登録の要求 | `CatalogTestData` | `TaxCategoryApiTest`、`AuthorizationTest` |',
    id: '| 作成の応答から ID を取り出す処理 | `TestJson`、`CatalogTestData` | `CategoryApiTest`、`PlaceOrderConcurrencyTest`（確認する API の作成の応答） |',
    category: '| カテゴリの登録の要求 | `CatalogTestData` | `CategoryApiTest` |',
    payment: '| 支払の API の作成の操作 | `PaymentTestData` | `PaymentApiTest` |',
  }
  const markdown = table(...Object.values(ROWS))
  const files = [
    'InventoryTestData',
    'CatalogTestData',
    'TestJson',
    'PaymentTestData',
    'module/inventory/infrastructure/persistence/JooqStockItemRepositoryTest',
    'module/tax/presentation/TaxCategoryApiTest',
    'config/security/AuthorizationTest',
    'module/catalog/presentation/CategoryApiTest',
    'module/order/presentation/PlaceOrderConcurrencyTest',
    'module/payment/api/PaymentApiTest',
  ].map((name) => `${IT}${name}.java`)
  const rules = buildTableRules(markdown, files)
  const rule = (step) => rules.find((candidate) => candidate.name.includes(step))
  const FOO = `${IT}module/foo/FooTest.java`
  const hits = (step, body, file = FOO) => findHelperViolations(file, `class FooTest {\n${body}\n}`, [rule(step)]).map((hit) => hit.line)

  test('表の行ごとに規則を作り、補助と除外のファイルを表から求める', () => {
    assert.equal(rules.length, 5)
    const stock = rule('在庫の作成')
    assert.equal(isHelperFile(`${IT}InventoryTestData.java`, stock), true)
    assert.equal(isHelperFile(`${IT}module/inventory/infrastructure/persistence/JooqStockItemRepositoryTest.java`, stock), true)
    assert.equal(isHelperFile(FOO, stock), false)
    const id = rule('ID を取り出す')
    assert.equal(isHelperFile(`${IT}TestJson.java`, id), true)
    assert.equal(isHelperFile(`${IT}CatalogTestData.java`, id), true)
    // 括弧の注記の中の語は、クラスに数えない（バッククォートの中だけ読む）
    assert.equal(isHelperFile(`${IT}module/order/presentation/PlaceOrderConcurrencyTest.java`, id), true)
  })

  test('在庫の作成：createIfAbsent の呼び出しと、在庫の表への insert を、違反にする', () => {
    const body = [
      '    void t() {',
      '        stockItemRepository.createIfAbsent(variantId, productId);',
      '        dsl.insertInto(STOCK_ITEM).set(STOCK_ITEM.ID, id).execute();',
      '        dsl.execute("insert into inventory.stock_item (id) values (?)", id);',
      '        inventory.create(variantId, productId);',
      '        dsl.deleteFrom(STOCK_ITEM).execute();',
      '    }',
    ].join('\n')
    assert.deepEqual(hits('在庫の作成', body), [3, 4, 5])
    assert.deepEqual(hits('在庫の作成', body, `${IT}InventoryTestData.java`), [])
    assert.deepEqual(hits('在庫の作成', body, `${IT}module/inventory/infrastructure/persistence/JooqStockItemRepositoryTest.java`), [])
  })

  test('税区分の登録の要求：登録の path への post（定数でも）を違反にし、取得と税率の変更は違反にしない', () => {
    const body = [
      '    private static final String TAX = "/api/admin/tax-categories";',
      '    void t() {',
      '        mockMvc.perform(post("/api/admin/tax-categories").content(json));',
      '        mockMvc.perform(withToken(post(TAX), token));',
      '        mockMvc.perform(post(TAX)',
      '            .content(json));',
      '        mockMvc.perform(get("/api/admin/tax-categories/" + id));',
      '        mockMvc.perform(put("/api/admin/tax-categories/" + id + "/rates"));',
      '        mockMvc.perform(post(OTHER));',
      '    }',
    ].join('\n')
    assert.deepEqual(hits('税区分の登録', body), [4, 5, 6])
    assert.deepEqual(hits('税区分の登録', body, `${IT}module/tax/presentation/TaxCategoryApiTest.java`), [])
    assert.deepEqual(hits('税区分の登録', body, `${IT}CatalogTestData.java`), [])
  })

  test('カテゴリの登録の要求：カテゴリの path への post を違反にし、別の path の定数は違反にしない', () => {
    const body = [
      '    private static final String BASE = "/api/admin/categories";',
      '    private static final String PRODUCTS = "/api/admin/products";',
      '    void t() {',
      '        mockMvc.perform(post(BASE).content(json));',
      '        mockMvc.perform(post("/api/admin/categories"));',
      '        mockMvc.perform(post(PRODUCTS));',
      '        mockMvc.perform(put(BASE + "/" + id));',
      '    }',
    ].join('\n')
    assert.deepEqual(hits('カテゴリの登録', body), [5, 6])
    assert.deepEqual(hits('カテゴリの登録', body, `${IT}module/catalog/presentation/CategoryApiTest.java`), [])
  })

  test('応答から ID を取り出す処理：JsonPath の $.id の読み取りを違反にし、他の path と検証は違反にしない', () => {
    const body = [
      '    void t() {',
      '        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");',
      '        var other = JsonPath.read(body, "$.orderNumber");',
      '        var ids = JsonPath.read(detail, "$.images[*].id");',
      '        mockMvc.perform(get("/x")).andExpect(jsonPath("$.id").isString());',
      '        var parsed = JsonPath.parse(body).read("$.id");',
      '        var created = TestJson.createdId(body);',
      '    }',
    ].join('\n')
    assert.deepEqual(hits('ID を取り出す', body), [3, 7])
    assert.deepEqual(hits('ID を取り出す', body, `${IT}TestJson.java`), [])
    assert.deepEqual(hits('ID を取り出す', body, `${IT}module/catalog/presentation/CategoryApiTest.java`), [])
  })

  test('応答から ID を取り出す処理：型引数、型を渡す形、後ろの引数がある読み取りも、違反にする', () => {
    const body = [
      '    void t() {',
      '        String a = JsonPath.<String>read(body, "$.id");',
      '        String b = JsonPath.read(body, "$.id", String.class);',
      '        String c = context.read("$.id", String.class);',
      '        Long d = JsonPath.parse(body).read("$.id", Long.class);',
      '        var e = JsonPath.read(body, "$.orderNumber", String.class);',
      '        mockMvc.perform(get("/x")).andExpect(jsonPath("$.id", is("a")));',
      '    }',
    ].join('\n')
    assert.deepEqual(hits('ID を取り出す', body), [3, 4, 5, 6])
  })

  test('登録の要求：RestClient などの .uri(…) の引数（文字列そのもの、同じファイルの定数）も探し、他の path は探さない', () => {
    const body = [
      '    private static final String TAX = "/api/admin/tax-categories";',
      '    void t() {',
      '        client.post().uri("/api/admin/tax-categories").body(json).retrieve();',
      '        client.post()',
      '            .uri(TAX)',
      '            .body(json);',
      '        client.post().uri(TAX, id).body(json);',
      '        client.get().uri(TAX).retrieve();',
      '        client.post().uri("/api/admin/tax-categories/" + id + "/rates");',
      '        client.post().uri(OTHER);',
      '    }',
    ].join('\n')
    assert.deepEqual(hits('税区分の登録', body), [4, 5, 8])
    const category = ['    private static final String BASE = "/api/admin/categories";', '    void t() {', '        client.post().uri(BASE).body(json);', '    }'].join('\n')
    assert.deepEqual(hits('カテゴリの登録', category), [4])
  })

  test('支払の作成の操作：PaymentApi の変数の create を違反にし、他の create と補助の created は違反にしない', () => {
    const body = [
      '    @Autowired',
      '    PaymentApi paymentApi;',
      '    void t() {',
      '        paymentApi.create(orderId, Money.of(6940), at);',
      '        context.getBean(PaymentApi.class).create(orderId, amount, at);',
      '        Payment.create(id, orderId, amount, deadline);',
      '        var orderId = payments.created(at);',
      '        paymentApi.pageUrl(orderId);',
      '    }',
    ].join('\n')
    assert.deepEqual(hits('支払の API', body), [5, 6])
    assert.deepEqual(hits('支払の API', body, `${IT}PaymentTestData.java`), [])
    assert.deepEqual(hits('支払の API', body, `${IT}module/payment/api/PaymentApiTest.java`), [])
  })

  test('表の書式が読めないときは、失敗にする', () => {
    assert.throws(() => parseHelperTable('表がない文書'), /表/)
    assert.throws(() => parseHelperTable(['| 手順 | 補助 |', '|---|---|', '| 在庫の作成 | `InventoryTestData` |'].join('\n')), /表/)
    assert.throws(() => buildTableRules(table('| 知らない手順 | `InventoryTestData` | `JooqStockItemRepositoryTest` |'), files), /知らない手順/)
    assert.throws(() => buildTableRules(table('| 在庫の作成 | `NoSuchHelper` | `JooqStockItemRepositoryTest` |'), files), /NoSuchHelper/)
    assert.throws(() => buildTableRules(table('| 在庫の作成 | 補助 | `JooqStockItemRepositoryTest` |'), files), /補助/)
  })

  test('表に、script が探す手順の行がないとき、データの行がないとき、列が 3 つでないときは、期待のメッセージで失敗にする', () => {
    const { stock, ...withoutStock } = ROWS
    assert.throws(() => buildTableRules(table(...Object.values(withoutStock)), files), /表に手順の行がない（在庫の作成）/)
    assert.throws(() => parseHelperTable(table()), /表に行がない/)
    assert.throws(() => parseHelperTable(table('| 在庫の作成 | `InventoryTestData` |')), /列が 3 つでない/)
    assert.throws(() => parseHelperTable(table('| 在庫の作成 | `InventoryTestData` | `JooqStockItemRepositoryTest` | 余分 |')), /列が 3 つでない/)
  })

  test('手順の名前ごとに、探す関数を持つ', () => {
    assert.equal(Object.keys(STEP_FINDERS).length, 5)
    for (const find of Object.values(STEP_FINDERS)) assert.equal(typeof find, 'function')
  })

  test('テストの書き方の今の表から、表の全ての行の規則を作る（loadRules が返す）', () => {
    const current = buildTableRules(readFileSync(TEST_WRITING_DOC, 'utf8'), listGitFiles())
    assert.equal(current.length, Object.keys(STEP_FINDERS).length)
    assert.equal(loadRules().length, 1 + current.length)
  })
})

import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { ASYNC_EVENT_HELPERS, EVENT_PUBLICATION_READS, TARGET_FILE, findHelperViolations, isHelperFile } from './test-helpers.mjs'

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

import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findUnnamedTests } from './test-names.mjs'

describe('表示の名前のないテストの検査', () => {
  test('@Test・@ParameterizedTest に @DisplayName がないメソッドを見つける', () => {
    const source = [
      'class FooTest {',
      '',
      '    @Test',
      '    void contextLoads() {',
      '    }',
      '',
      '    @ParameterizedTest',
      '    @CsvSource({',
      '            "1, 2",',
      '    })',
      '    void adds(int a, int b) {',
      '    }',
      '}',
    ].join('\n')
    assert.deepEqual(findUnnamedTests(source), [
      { line: 3, method: 'contextLoads' },
      { line: 7, method: 'adds' },
    ])
  })

  test('@DisplayName が @Test の前にあっても後にあっても見つけない', () => {
    const source = [
      '    /** 説明 */',
      '    @Test',
      '    @DisplayName("AC-1 登録できる")',
      '    void registers() {',
      '    }',
      '',
      '    @DisplayName("AC-2 消せる")',
      '    @ParameterizedTest',
      '    @ValueSource(ints = {1, 2})',
      '    public void deletes(int value) throws Exception {',
      '    }',
    ].join('\n')
    assert.deepEqual(findUnnamedTests(source), [])
  })

  test('前のメソッドの @DisplayName を、次のテストの名前とみなさない', () => {
    const source = [
      '    @Test',
      '    @DisplayName("名前がある")',
      '    void named() {',
      '    }',
      '    @Test',
      '    void unnamed() {',
      '    }',
    ].join('\n')
    assert.deepEqual(findUnnamedTests(source), [{ line: 5, method: 'unnamed' }])
  })

  test('テストでない注釈のメソッドと、クラスは見ない', () => {
    const source = [
      '@DisplayName("まとまり")',
      'class FooTest {',
      '    @BeforeEach',
      '    void setUp() {',
      '    }',
      '    @Nested',
      '    class Inner {',
      '    }',
      '    @TestConfiguration',
      '    static class Config {',
      '    }',
      '}',
    ].join('\n')
    assert.deepEqual(findUnnamedTests(source), [])
  })
})

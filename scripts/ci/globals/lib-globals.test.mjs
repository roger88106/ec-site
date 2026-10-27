import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { diffNames, extractGlobalNames, referencedLibs } from './lib-globals.mjs'

describe('lib の宣言のファイルからグローバルの値の名前を読む', () => {
  test('declare の var・let・const・function・class・namespace・enum の名前を読む', () => {
    const text = [
      'declare var status: string;',
      'declare let counter: number;',
      'declare const NaN: number;',
      'declare function alert(message?: any): void;',
      'declare class Legacy {}',
      'declare namespace Intl {',
      '}',
      'declare enum Mode { A }',
    ].join('\n')
    assert.deepEqual(extractGlobalNames(text), ['Intl', 'Legacy', 'Mode', 'NaN', 'alert', 'counter', 'status'])
  })

  test('型だけの名前（interface・type）は値をシャドーイングしないので読まない', () => {
    const text = ['interface Window {', '  name: string;', '}', 'type Foo = string;', 'declare var Window: {', '    prototype: Window;', '};'].join('\n')
    assert.deepEqual(extractGlobalNames(text), ['Window'])
  })

  test('入れ子の宣言、メンバー、コメントの中の宣言は読まない', () => {
    const text = [
      'declare namespace Intl {',
      '    var inner: string;',
      '    function format(): void;',
      '}',
      '// declare var commented: string;',
      '/** declare var documented: string; */',
      '    declare var indented: string;',
    ].join('\n')
    assert.deepEqual(extractGlobalNames(text), ['Intl'])
  })

  test('同じ名前の宣言（関数の重ね書き）は 1 つにして、名前の順に並べる', () => {
    const text = ['declare function parseInt(a: string): number;', 'declare function parseInt(a: number): number;', 'declare var Array: ArrayConstructor;'].join('\n')
    assert.deepEqual(extractGlobalNames(text), ['Array', 'parseInt'])
  })

  test('$ と _ を含む名前を読む', () => {
    assert.deepEqual(extractGlobalNames('declare var $x_1: any;'), ['$x_1'])
  })
})

describe('lib の宣言のファイルが参照する別の lib', () => {
  test('参照の指示の lib の名前を、書いた順に返す', () => {
    const text = ['/// <reference lib="es2024" />', '/// <reference lib="esnext.intl" />', 'declare var x: 1;'].join('\n')
    assert.deepEqual(referencedLibs(text), ['es2024', 'esnext.intl'])
  })

  test('参照のない宣言のファイルは空', () => {
    assert.deepEqual(referencedLibs('declare var x: 1;'), [])
  })
})

describe('一覧の差', () => {
  test('足りない名前（一覧にない）と余る名前（lib にない）を名前の順に返す', () => {
    assert.deepEqual(diffNames(['a', 'b', 'c'], ['b', 'c', 'd', 'e']), { missing: ['d', 'e'], extra: ['a'] })
  })

  test('同じなら両方が空', () => {
    assert.deepEqual(diffNames(['a'], ['a']), { missing: [], extra: [] })
  })
})

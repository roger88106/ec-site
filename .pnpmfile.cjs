// TypeScript 7 には JavaScript の API がなく、openapi-typescript はその API で型を組み立てる。
// そのため openapi-typescript にだけ TypeScript 6 を渡す。
// pnpm の overrides は peer の依存に効かないので、インストールのフックで渡す。
// openapi-typescript が新しい版で peer の範囲を変えたら、フックを消す時期に気づけるよう、install を失敗させる。
// このファイルを変えたら pnpm install で lock も更新する（lock がフックのチェックサムを持つ）。
// openapi-typescript が TypeScript 7 で動くようになったら、このファイルを消す。
const EXPECTED_PEER = '^5.x'

module.exports = {
  hooks: {
    readPackage(pkg) {
      if (pkg.name === 'openapi-typescript') {
        const peer = pkg.peerDependencies?.typescript
        if (peer !== EXPECTED_PEER) {
          throw new Error(
            `openapi-typescript の peer の依存 typescript が ${peer ?? '(なし)'} に変わった。` +
              'TypeScript 7 で動くなら .pnpmfile.cjs を削除し、動かないならフックの対象の範囲を直す（フロントのアーキテクチャ 第 3 節）',
          )
        }
        const { typescript: _peer, ...peerDependencies } = pkg.peerDependencies
        pkg.peerDependencies = peerDependencies
        pkg.dependencies = { ...pkg.dependencies, typescript: '6.0.3' }
      }
      return pkg
    },
  },
}

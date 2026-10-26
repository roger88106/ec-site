// TypeScript 7 には JavaScript の API がなく、openapi-typescript はその API で型を組み立てる。
// そのため openapi-typescript にだけ TypeScript 6 を渡す。
// pnpm の overrides は peer の依存に効かないので、インストールのフックで渡す。
// openapi-typescript が TypeScript 7 で動くようになったら、このファイルを消す。
module.exports = {
  hooks: {
    readPackage(pkg) {
      if (pkg.name === 'openapi-typescript') {
        const { typescript: _peer, ...peerDependencies } = pkg.peerDependencies ?? {}
        pkg.peerDependencies = peerDependencies
        pkg.dependencies = { ...pkg.dependencies, typescript: '6.0.3' }
      }
      return pkg
    },
  },
}

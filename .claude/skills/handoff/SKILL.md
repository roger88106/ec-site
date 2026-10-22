---
name: handoff
description: Compact the current conversation into a handoff document for another agent to pick up.
argument-hint: "What will the next session be used for?"
disable-model-invocation: true
---

<!-- 説明（日本語）：今の会話を、別のエージェントが続きから作業できる引き継ぎの文書にまとめる。spec の区切りや、作業を途中でやめて新しいセッションに移るときに、利用者が /handoff で呼ぶ。出典は mattpocock/skills（MIT）で、本文は原文のまま。末尾にこのリポジトリ向けの追記がある。 -->

Write a handoff document summarising the current conversation so a fresh agent can continue the work. Save to the temporary directory of the user's OS (`$TMPDIR`, else `/tmp`; `%TEMP%` on Windows) - not the current workspace.

Include a "suggested skills" section in the document, naming which skills the next agent should call the Skill tool for.

Do not duplicate content already captured in other artifacts (specs, plans, ADRs, issues, commits, diffs). Reference them by path or URL instead.

Redact any sensitive information, such as API keys, passwords, or personally identifiable information.

If the user passed arguments, treat them as a description of what the next session will focus on and tailor the doc accordingly.

## このリポジトリでの使い方

- 出典：[mattpocock/skills](https://github.com/mattpocock/skills)（MIT。`../LICENSE-mattpocock-skills`）。以下はこのリポジトリ向けの追記。
- 文書は OS の一時ディレクトリではなく、リポジトリの `/.local/handoff/yyyyMMddHHmmss-<名前>/` に置く（例：`/.local/handoff/20261012152403-003-done/`）。`/.local/` は Git に入れないものの置き場所（AGENTS.md）。上の原文の置き場所より、こちらを優先する。日時は作成した時刻。中の MD は 1 つでも、内容ごとに複数に分けてもよい。
- 新しいセッションは、利用者の指示がなければ、名前の日時が最も新しいディレクトリを読み、書かれた状態（push していない commit、次のタスク）を `git log` と tasks ファイルで確認してから始める。
- spec 担当のセッションを途中で別のセッションに替えるときに使う。引き継ぎの文書に、追跡の issue の番号を書く。平常のやり取りには使わない。平常は、セッションの親子の関係の通知と、追跡の issue のコメントで行う（[エージェントの作業フロー](../../../docs/rules/subagents.md)）。
- 古い引き継ぎの文書は利用者が削除する。エージェントは削除せず、削除するかどうかも聞かない。
- 主に、spec の区切りや、作業を途中でやめて新しいセッションに移るときに使う（AGENTS.md「セッションの始めと終わり」）。作業の正本は tasks ファイル（`docs/specs/NNN-*/tasks/`）と README の spec の状態なので、引き継ぎの文書には、次に着手するタスク番号と、tasks・commit に残っていないこと（未確定の判断、手動確認で気づいたこと、ローカルの DB に残したテストデータなど）だけを書く。
- 「suggested skills」には、AGENTS.md の skill の表から、次の作業で使うものを挙げる。

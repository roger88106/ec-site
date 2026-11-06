# ============================================================================
# ローカルの環境のポートを、Windows のポートの予約（WinNAT などが動的に取る範囲）から外す（開発環境の起動）
#
#   使い方（管理者の PowerShell で）：powershell -ExecutionPolicy Bypass -File scripts\dev\windows\exclude-ports.ps1
#     -WhatIf を付けると、照合した結果を示すだけで何も変えない
#
# 先に compose（docker compose stop）、バックエンド、画面を止める。使っているポートは予約できない。
# 管理者の権限が必要だ。管理者でなければ、照合した結果を示して何も変えずに終わる。
# すること：
#   1. 今の予約の範囲と、一覧のポートを照合して示す
#   2. 変えること（WinNAT を止める → 各ポートを 1 つずつ予約する → WinNAT を始める）を示し、確認を求める
#   3. 確認したら変える。予約したポートは、ほかのプログラムが動的に取る範囲に入らなくなる（再起動の後も残る）
# 予約を消すとき：netsh int ipv4 delete excludedportrange protocol=tcp startport=<ポート> numberofports=1
#
# ポートの一覧は scripts/dev/ports.json を読む。ポートを追加したら（compose、バックエンド、画面、E2E）、一覧に加える。
# ============================================================================
param([switch]$WhatIf)

$ErrorActionPreference = 'Stop'

# ポートの一覧（scripts/dev/ports.json）を読み、スロットごとにずらした分（スロット 1 以降は、スロットを使う項目だけ）を含めて展開する
$list = Get-Content -Raw -Encoding UTF8 (Join-Path $PSScriptRoot '..\ports.json') | ConvertFrom-Json
$ports = @()
foreach ($slot in 0..($list.slotCount - 1)) {
  foreach ($entry in $list.ports) {
    if ($slot -gt 0 -and -not $entry.slotted) { continue }
    $name = if ($slot -eq 0) { $entry.name } else { "$($entry.name)（スロット $slot）" }
    $ports += [pscustomobject]@{ Port = [int]$entry.port + [int]$list.slotOffset * $slot; Name = $name }
  }
}

# 今の予約の範囲（netsh の出力の、数字の 2 列の行）。末尾の * は管理者が追加した予約
function Get-ExcludedRanges {
  netsh interface ipv4 show excludedportrange protocol=tcp |
    ForEach-Object {
      if ($_ -match '^\s*(\d+)\s+(\d+)\s*(\*)?\s*$') {
        [pscustomobject]@{ Start = [int]$Matches[1]; End = [int]$Matches[2]; Administered = [bool]$Matches[3] }
      }
    }
}

$ranges = @(Get-ExcludedRanges)
$toAdd = @()
Write-Host 'ポートと今の予約の範囲：'
foreach ($item in $ports) {
  $port = $item.Port
  $range = $ranges | Where-Object { $_.Start -le $port -and $port -le $_.End } | Select-Object -First 1
  if ($null -eq $range) {
    $state = '予約の範囲の外（使える。予約して、後で範囲に入らないようにする）'
    $toAdd += $port
  } elseif ($range.Administered -and $range.Start -eq $port -and $range.End -eq $port) {
    $state = '予約済み（変えない）'
  } else {
    $state = "予約の範囲 $($range.Start)〜$($range.End) の中（今は使えない）"
    $toAdd += $port
  }
  Write-Host ('  {0,5}  {1,-20} {2}' -f $port, $item.Name, $state)
}

if ($toAdd.Count -eq 0) {
  Write-Host 'すべて予約済み。何も変えない。'
  exit 0
}

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
  [Security.Principal.WindowsBuiltInRole]::Administrator)
Write-Host ''
Write-Host '変えること：'
Write-Host '  0. （先に止めておく）compose（docker compose stop）、バックエンド、画面。使っているポートは予約できない'
Write-Host '  1. WinNAT を止める（net stop winnat。動いている Docker のコンテナの通信が少しの間切れる）'
Write-Host "  2. 次のポートを 1 つずつ予約する：$($toAdd -join '、')"
Write-Host '  3. WinNAT を始める（net start winnat）'
if ($WhatIf) {
  Write-Host '-WhatIf なので、何も変えずに終わる。'
  exit 0
}
if (-not $admin) {
  Write-Host '管理者の権限がないので、何も変えずに終わる。管理者の PowerShell で流し直す。'
  exit 1
}
$answer = Read-Host '変えるなら y を入れる'
if ($answer -ne 'y') {
  Write-Host '何も変えずに終わる。'
  exit 0
}

net stop winnat
try {
  foreach ($port in $toAdd) {
    netsh int ipv4 add excludedportrange protocol=tcp startport=$port numberofports=1
    if ($LASTEXITCODE -ne 0) { Write-Host "  $port を予約できなかった（ほかのプログラムが使っているか、管理者の予約と重なる）" }
  }
} finally {
  net start winnat
}
Write-Host '終えた。今の予約の範囲：'
netsh interface ipv4 show excludedportrange protocol=tcp

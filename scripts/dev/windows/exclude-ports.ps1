# ============================================================================
# ローカルの環境のポートを、Windows のポートの予約（WinNAT などが動的に取る範囲）から外す（開発環境の起動）
#
#   使い方（管理者の PowerShell で）：powershell -ExecutionPolicy Bypass -File scripts\dev\windows\exclude-ports.ps1
#     -WhatIf を付けると、照合した結果を示すだけで何も変えない
#
# 先に compose（docker compose stop）、バックエンド、画面を止める。使っているポートは予約できない。
# 管理者の権限が必要だ。管理者でなければ、照合した結果を示して何も変えずに終わる。
# すること：
#   1. 今の予約の範囲と、下の表のポートを照合して示す
#   2. 変えること（WinNAT を止める → 各ポートを 1 つずつ予約する → WinNAT を始める）を示し、確認を求める
#   3. 確認したら変える。予約したポートは、ほかのプログラムが動的に取る範囲に入らなくなる（再起動の後も残る）
# 予約を消すとき：netsh int ipv4 delete excludedportrange protocol=tcp startport=<ポート> numberofports=1
#
# ポートを追加したら（compose、バックエンド、画面、E2E）、この表も直す。一覧は scripts/dev/ports.json。この表との一致は CI の検査（local-ports）が確認する。
# ============================================================================
param([switch]$WhatIf)

$ErrorActionPreference = 'Stop'

$ports = [ordered]@{
  5432 = 'PostgreSQL'
  9411 = 'Zipkin'
  8333 = 'SeaweedFS'
  8081 = 'imgproxy'
  8180 = 'Keycloak'
  8181 = 'Keycloak（E2E）'
  2525 = 'Mailpit の SMTP'
  8025 = 'Mailpit の画面'
  8080 = 'バックエンド'
  8090 = 'バックエンド（E2E）'
  9080 = 'バックエンドの運用ポート'
  9090 = 'バックエンドの運用ポート（E2E）'
  5173 = '管理画面'
  4173 = '管理画面（E2E）'
  3000 = '店舗画面'
  3100 = '店舗画面（E2E）'
  18080 = 'バックエンド（スロット 1）'
  19080 = 'バックエンドの運用ポート（スロット 1）'
  13000 = '店舗画面（スロット 1）'
  15173 = '管理画面（スロット 1）'
  28080 = 'バックエンド（スロット 2）'
  29080 = 'バックエンドの運用ポート（スロット 2）'
  23000 = '店舗画面（スロット 2）'
  25173 = '管理画面（スロット 2）'
  38080 = 'バックエンド（スロット 3）'
  39080 = 'バックエンドの運用ポート（スロット 3）'
  33000 = '店舗画面（スロット 3）'
  35173 = '管理画面（スロット 3）'
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
foreach ($port in $ports.Keys) {
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
  Write-Host ('  {0,5}  {1,-20} {2}' -f $port, $ports[$port], $state)
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

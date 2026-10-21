#requires -Version 7.0
<#
Checks pending Git content by default, or the tracked checkout with -Tracked.
Reports locations and rule names only, never matched values.
#>
[CmdletBinding()]
param([switch] $Tracked)

$ErrorActionPreference = 'Stop'
$root = & git rev-parse --show-toplevel 2>$null
if ($LASTEXITCODE -ne 0) { throw 'Run this check inside the repository.' }

function Get-GitPaths([string[]] $Arguments) {
    $output = & git -C $root -c core.quotepath=false @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Could not enumerate Git candidates.' }
    return (($output -join "`n") -split "`0" | Where-Object { $_.Length -gt 0 })
}

$staged = @(Get-GitPaths @('diff', '--cached', '--name-only', '--diff-filter=ACMR', '-z'))
$working = @(Get-GitPaths @('diff', '--name-only', '--diff-filter=ACMR', '-z'))
$working += @(Get-GitPaths @('ls-files', '--others', '--exclude-standard', '-z'))
if ($Tracked) { $working += @(Get-GitPaths @('ls-files', '-z')) }

$rules = [ordered]@{
    'user-home' = '(?i)(?:[a-z]:[\\/]+(?:Users|Documents and Settings)[\\/]+[^\s\\/"''<>]+|/(?:Users|home)/[^\s/"''<>]+)'
    'private-key' = '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'
    'provider-token' = '\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}|sk-(?:proj-)?[A-Za-z0-9_-]{30,})\b'
    'assigned-secret' = '(?i)(?:access[_-]?token|refresh[_-]?token|client[_-]?secret|password)["'']?\s*[=:]\s*["''][a-z0-9+/_.=-]{20,}["'']'
}
# Derive this checkout's path at runtime; never put a developer's path in the script.
$rootVariants = @($root, $root.Replace('/', '\'), $root.Replace('\', '/'))
$rootVariants += @($rootVariants | ForEach-Object { $_.Replace('\', '\\') })
$rules['checkout-path'] = '(?i)' + (($rootVariants | Sort-Object -Unique | ForEach-Object { [regex]::Escape($_) }) -join '|')
$privateName = '(^|/)(?:\.env(?:\.(?!example$|sample$)[^/]+)?|accounts\.json|credentials\.json|local\.properties|gradle-local\.properties|id_rsa|id_ed25519)$|\.(?:p12|pfx|key)$'
$findings = [System.Collections.Generic.List[string]]::new()
$binary = 0
$checked = 0

function Test-ShareText([string] $Path, [string] $Version, [string] $Content) {
    $script:checked++
    if ($Path -match $privateName) { $findings.Add("${Path} [$Version/private-file]") }
    if ($Content.Contains([char]0)) { $script:binary++; return }
    $lines = $Content -split '\r?\n'
    for ($i = 0; $i -lt $lines.Length; $i++) {
        foreach ($rule in $rules.Keys) {
            if ($lines[$i] -match $rules[$rule]) {
                $findings.Add("${Path}:$($i + 1) [$Version/$rule]")
            }
        }
    }
}

foreach ($path in ($staged | Sort-Object -Unique)) {
    $content = & git -C $root show ":$path"
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect an index candidate.' }
    Test-ShareText $path 'index' ($content -join "`n")
}
foreach ($path in ($working | Sort-Object -Unique)) {
    $fullPath = Join-Path $root $path
    if (-not (Test-Path -LiteralPath $fullPath)) { continue }
    $item = Get-Item -LiteralPath $fullPath -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        $findings.Add("${path} [worktree/link-needs-review]")
        continue
    }
    Test-ShareText $path 'worktree' ([IO.File]::ReadAllText($fullPath))
}

if ($findings.Count -gt 0) {
    $findings | Sort-Object -Unique | Write-Output
    Write-Output 'Share check failed. Review these locations locally; do not publish matched values.'
    exit 1
}
Write-Output "Share text check passed ($checked candidate versions; $binary binary versions require separate review)."

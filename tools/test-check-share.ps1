#requires -Version 7.0
# Isolated scanner regression cases; no production files or real credentials.
$ErrorActionPreference = 'Stop'
$check = Join-Path $PSScriptRoot 'check-share.ps1'
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$fixture = Join-Path $tempRoot ('zpl-share-test-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $fixture
$passed = 0

function Assert-Check([int] $ExpectedExit, [string] $ExpectedRule, [string] $ForbiddenOutput = '') {
    $output = (& pwsh -NoProfile -File $check 2>&1) -join "`n"
    if ($LASTEXITCODE -ne $ExpectedExit) { throw 'Unexpected scanner exit code.' }
    if ($ExpectedRule -and -not $output.Contains($ExpectedRule)) { throw 'Expected scanner rule was not reported.' }
    if ($ForbiddenOutput -and $output.Contains($ForbiddenOutput)) { throw 'Scanner exposed a matched value.' }
    $script:passed++
}

Push-Location $fixture
try {
    & git init --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Cannot initialize isolated fixture.' }
    Set-Content -LiteralPath 'sample.txt' -Value 'relative/path.txt' -Encoding utf8
    Assert-Check 0 ''

    $fakeHome = 'C:' + '\Users\' + 'fictional-person\project'
    Set-Content -LiteralPath 'sample.txt' -Value $fakeHome -Encoding utf8
    Assert-Check 1 'worktree/user-home' $fakeHome

    & git add -- sample.txt
    if ($LASTEXITCODE -ne 0) { throw 'Cannot stage isolated fixture.' }
    Set-Content -LiteralPath 'sample.txt' -Value 'clean working copy' -Encoding utf8
    Assert-Check 1 'index/user-home' $fakeHome

    & git add -- sample.txt
    if ($LASTEXITCODE -ne 0) { throw 'Cannot replace isolated index fixture.' }
    Assert-Check 0 ''

    $fakeToken = 'ghp_' + ('a' * 36)
    Set-Content -LiteralPath 'sample.txt' -Value $fakeToken -Encoding utf8
    Assert-Check 1 'provider-token' $fakeToken

    Set-Content -LiteralPath 'sample.txt' -Value $fixture -Encoding utf8
    Assert-Check 1 'checkout-path' $fixture

    Set-Content -LiteralPath 'sample.txt' -Value 'clean' -Encoding utf8
    Set-Content -LiteralPath 'credentials.json' -Value '{}' -Encoding utf8
    Assert-Check 1 'private-file'

    Remove-Item -LiteralPath 'credentials.json'
    Set-Content -LiteralPath '.gitignore' -Value 'local-report.txt' -Encoding utf8
    Set-Content -LiteralPath 'local-report.txt' -Value $fakeHome -Encoding utf8
    Assert-Check 0 ''

    Write-Output "Share checker regression cases passed: $passed."
}
finally {
    Pop-Location
    $resolved = [IO.Path]::GetFullPath($fixture)
    if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($resolved) -notmatch '^zpl-share-test-[a-f0-9]{32}$') {
        throw 'Refusing cleanup outside the generated fixture.'
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

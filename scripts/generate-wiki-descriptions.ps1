<#
.SYNOPSIS
    Regenerates the bundled iron.wiki spell descriptions.

.DESCRIPTION
    The one generator behind BOTH refresh paths (owner's periodic-check
    brief): a human runs it before a release, and .github/workflows/
    wiki-descriptions.yml runs it weekly to open a refresh PR. It fetches
    https://iron.wiki/spells/ (or reads a saved page with -PageFile),
    pulls each spell's prose out of its spell-container, and writes the
    shipped resource in the exact bundled shape: provenance keys first,
    then every spell id sorted, UTF-8 without BOM.

    Keys are emitted for every documented spell - the loader looks entries
    UP by registered id, so a key no spell claims is inert weight, while a
    spell the page drops simply stays description-less (the "when
    possible" contract). The run fails loudly below -MinimumEntries so a
    markup change can never ship a gutted file: the cron goes red instead
    of opening a PR that hollowed out the pane.

.PARAMETER PageFile
    Read a saved copy of the page instead of fetching (deterministic
    local runs and tests).

.PARAMETER OutFile
    Where to write; defaults to the shipped resource.

.PARAMETER MinimumEntries
    Fail below this many parsed spells (a markup-break tripwire).

.PARAMETER Source
    The page URL the weekly cron fetches.
#>
[CmdletBinding()]
param(
    [string]$PageFile,
    [string]$OutFile = "$PSScriptRoot/../src/main/resources/assets/ability_iss/spell_descriptions.json",
    [int]$MinimumEntries = 100,
    [string]$Namespace = "irons_spellbooks",
    [string]$Source = "https://iron.wiki/spells/"
)

$ErrorActionPreference = "Stop"

if ($PageFile) {
    $html = Get-Content $PageFile -Raw -Encoding UTF8
    Write-Host "Reading saved page: $PageFile"
} else {
    Write-Host "Fetching $Source"
    # -OutFile, not .Content: Windows PowerShell 5.1's response parser
    # NREs without the IE engine, and the raw file sidesteps it on both
    # shells. UTF8 pinned - the page is UTF-8, the prose must decode the
    # same way locally and on the runner.
    $temp = [System.IO.Path]::GetTempFileName()
    try {
        Invoke-WebRequest -Uri $Source -OutFile $temp -TimeoutSec 60 -MaximumRedirection 5
        $html = Get-Content $temp -Raw -Encoding UTF8
    } finally {
        Remove-Item $temp -Force -ErrorAction SilentlyContinue
    }
}

# One block per spell: the id rides the spell's icon path, the prose is
# the styled margin div after the stat table (the rules the offline build
# used - see the shipped file's _source).
$prosePattern = '<div style="margin-top: 7px; margin-left: 5px; margin-right: 10px">\s*(.*?)\s*</div>'
$entries = [ordered]@{}
$containers = [regex]::Matches($html, '<div class="spell-container">')
$skipped = @()

for ($c = 0; $c -lt $containers.Count; $c++) {
    $start = $containers[$c].Index
    $end = if ($c + 1 -lt $containers.Count) { $containers[$c + 1].Index } else { $html.Length }
    $chunk = $html.Substring($start, $end - $start)

    $idMatch = [regex]::Match($chunk, '/img/spells/([a-z0-9_]+)\.png')
    if (-not $idMatch.Success) { continue }
    $id = $idMatch.Groups[1].Value
    if ($entries.Contains($id)) { continue }

    $proseMatch = [regex]::Match($chunk, $prosePattern, 'Singleline')
    if (-not $proseMatch.Success) { $skipped += $id; continue }
    $raw = $proseMatch.Groups[1].Value

    if ($raw -match '<ul>') {
        # bullet lists (counterspell) keep their lines
        $items = [regex]::Matches($raw, '<li>(.*?)(?=<li>|</ul>\s*$)', 'Singleline') |
            ForEach-Object { ($_.Groups[1].Value -replace '<[^>]+>', ' ' -replace '\s+', ' ').Trim() }
        $prose = ($items | Where-Object { $_ } | ForEach-Object { "- $_" }) -join "`n"
    } else {
        $prose = ($raw -replace '<br\s*/?>', ' ' -replace '<[^>]+>', ' ' -replace '\s+', ' ').Trim()
    }
    $prose = [System.Net.WebUtility]::HtmlDecode($prose)
    if ([string]::IsNullOrWhiteSpace($prose)) { $skipped += $id; continue }
    $entries[$id] = $prose
}

if ($entries.Count -lt $MinimumEntries) {
    throw ("Parsed only $($entries.Count) spells (minimum $MinimumEntries) - " +
        "the page's markup has changed. Fix the parser; do not ship a gutted file.")
}

# Sorted keys, provenance first - the exact bundled shape.
$retrieved = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add('{')
$lines.Add('  "_source": "https://iron.wiki/spells/",')
$lines.Add(('  "_retrieved": "{0}",' -f $retrieved))
$sorted = @($entries.Keys | Sort-Object)
for ($i = 0; $i -lt $sorted.Count; $i++) {
    $key = $sorted[$i]
    $comma = if ($i -lt $sorted.Count - 1) { ',' } else { '' }
    $lines.Add(('  "{0}:{1}": {2}{3}' -f $Namespace, $key,
        (ConvertTo-Json $entries[$key]), $comma))
}
$lines.Add('}')

$directory = Split-Path -Parent $OutFile
if ($directory -and -not (Test-Path $directory)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}
[System.IO.File]::WriteAllText($OutFile, (($lines -join "`n") + "`n"),
    [System.Text.UTF8Encoding]::new($false))

Write-Host "Wrote $($entries.Count) spells to $OutFile"
if ($skipped.Count -gt 0) {
    Write-Host "No prose (stays description-less): $($skipped -join ', ')"
}

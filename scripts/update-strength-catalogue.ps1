param([string]$Path = "$PSScriptRoot/../app/src/main/assets/strength-exercise-catalogue.json")
$raw = Get-Content -Raw $Path
$document = $raw | ConvertFrom-Json
$start = $raw.IndexOf('[', $raw.IndexOf('"exercises"'))
$depth = 0; $quoted = $false; $escaped = $false; $end = -1
for ($i = $start; $i -lt $raw.Length; $i++) {
    $char = $raw[$i]
    if ($quoted) { if ($escaped) { $escaped = $false } elseif ($char -eq '\') { $escaped = $true } elseif ($char -eq '"') { $quoted = $false } }
    elseif ($char -eq '"') { $quoted = $true } elseif ($char -eq '[') { $depth++ } elseif ($char -eq ']') { $depth--; if ($depth -eq 0) { $end = $i; break } }
}
$builder = [Text.StringBuilder]::new(); $quoted = $false; $escaped = $false
foreach ($char in $raw.Substring($start, $end - $start + 1).ToCharArray()) {
    if ($quoted) { [void]$builder.Append($char); if ($escaped) { $escaped = $false } elseif ($char -eq '\') { $escaped = $true } elseif ($char -eq '"') { $quoted = $false } }
    elseif ($char -eq '"') { $quoted = $true; [void]$builder.Append($char) } elseif (-not [char]::IsWhiteSpace($char)) { [void]$builder.Append($char) }
}
$payload = $builder.ToString()
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $checksum = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($payload)))).Replace('-', '').ToLower()
} finally { $sha.Dispose() }
$document.exerciseCount = $document.exercises.Count
$document.payloadChecksum = $checksum
[IO.File]::WriteAllText((Resolve-Path $Path), ($document | ConvertTo-Json -Depth 20 -Compress), [Text.UTF8Encoding]::new($false))
Write-Output "Catalogue $($document.catalogueVersion): $($document.exerciseCount) exercises, SHA-256 $checksum"

# Imports a folder of audio files into hermes-audiobooks-api as one audiobook.
# Chapters are uploaded in file-name order; "NN - Chapter title.mp3" becomes position NN, title "Chapter title".
# MP3 durations are read from the file headers (no ffprobe needed).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\import-audiobook.ps1 `
#     -Folder "C:\path\to\book" -Title "Book title" -Author "Author" [-Narrator "..."] [-Language en] `
#     [-PublishedYear 1997] [-Description "..."] [-Cover cover.jpg] [-BaseUrl http://hermes.localtest.me] [-Force]

param(
    [Parameter(Mandatory)] [string]$Folder,
    [Parameter(Mandatory)] [string]$Title,
    [Parameter(Mandatory)] [string]$Author,
    [string]$Narrator,
    [string]$Language,
    [int]$PublishedYear,
    [string]$Description,
    [string]$Cover,
    [string]$BaseUrl = 'http://hermes.localtest.me',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$api = "$($BaseUrl.TrimEnd('/'))/api/v1"
$contentTypes = @{ '.mp3' = 'audio/mpeg'; '.m4a' = 'audio/mp4'; '.m4b' = 'audio/mp4'; '.aac' = 'audio/aac'; '.ogg' = 'audio/ogg'; '.flac' = 'audio/flac'; '.wav' = 'audio/wav' }

# Duration of an MP3 in seconds: uses the Xing/Info frame count when present (VBR), else bitrate (CBR).
function Get-Mp3Duration([string]$Path) {
    $fs = [IO.File]::OpenRead($Path)
    try {
        $buf = New-Object byte[] 65536
        $n = $fs.Read($buf, 0, $buf.Length)
        $pos = 0
        if ($n -ge 10 -and $buf[0] -eq 0x49 -and $buf[1] -eq 0x44 -and $buf[2] -eq 0x33) {   # "ID3" tag
            # cast first: -shl on a [byte] stays a byte and loses the high bits
            $tag = ([int]$buf[6] -shl 21) -bor ([int]$buf[7] -shl 14) -bor ([int]$buf[8] -shl 7) -bor [int]$buf[9]
            $pos = 10 + $tag
            $fs.Position = $pos; $n = $fs.Read($buf, 0, $buf.Length); $start = $pos; $pos = 0
        } else { $start = 0 }
        while ($pos -lt $n - 4 -and -not ($buf[$pos] -eq 0xFF -and ($buf[$pos + 1] -band 0xE0) -eq 0xE0)) { $pos++ }
        if ($pos -ge $n - 4) { return $null }
        $b1 = $buf[$pos + 1]; $b2 = $buf[$pos + 2]; $b3 = $buf[$pos + 3]
        $version = ($b1 -shr 3) -band 3                     # 3 = MPEG1, 2 = MPEG2, 0 = MPEG2.5
        $layer = ($b1 -shr 1) -band 3                       # 1 = Layer III
        if ($layer -ne 1) { return $null }
        $rates1 = 0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320
        $rates2 = 0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160
        $srates = @{ 3 = (44100, 48000, 32000); 2 = (22050, 24000, 16000); 0 = (11025, 12000, 8000) }
        $bitrate = $(if ($version -eq 3) { $rates1 } else { $rates2 })[($b2 -shr 4) -band 15] * 1000
        $sampleRate = $srates[$version][($b2 -shr 2) -band 3]
        $samplesPerFrame = $(if ($version -eq 3) { 1152 } else { 576 })
        $mono = (($b3 -shr 6) -band 3) -eq 3
        $sideInfo = $(if ($version -eq 3) { if ($mono) { 17 } else { 32 } } else { if ($mono) { 9 } else { 17 } })
        $x = $pos + 4 + $sideInfo
        $tagName = [Text.Encoding]::ASCII.GetString($buf, $x, 4)
        if (($tagName -eq 'Xing' -or $tagName -eq 'Info') -and ($buf[$x + 7] -band 1)) {
            $frames = ([int64]$buf[$x + 8] -shl 24) -bor ([int64]$buf[$x + 9] -shl 16) -bor ([int64]$buf[$x + 10] -shl 8) -bor [int64]$buf[$x + 11]
            return [int][math]::Round($frames * $samplesPerFrame / $sampleRate)
        }
        if ($bitrate -le 0) { return $null }
        return [int][math]::Round(($fs.Length - $start) * 8 / $bitrate)
    } finally { $fs.Dispose() }
}

function Invoke-Curl([string[]]$CurlArgs) {
    $out = & curl.exe -s -S -w "`n%{http_code}" @CurlArgs
    $lines = @($out)
    $code = $lines[-1]
    $body = ($lines[0..($lines.Count - 2)] -join "`n")
    if ($code -notmatch '^2') { throw "HTTP $code from $($CurlArgs[-1]): $body" }
    return $body | ConvertFrom-Json
}

if (-not (Test-Path -LiteralPath $Folder)) { throw "Folder not found: $Folder" }
$files = Get-ChildItem -LiteralPath $Folder -File | Where-Object { $contentTypes.ContainsKey($_.Extension.ToLower()) } | Sort-Object Name
if (-not $files) { throw "No audio files in $Folder" }

# Refuse duplicates unless -Force
$existing = Invoke-RestMethod "$api/audiobooks?q=$([uri]::EscapeDataString($Title))&size=100"
$dup = $existing.content | Where-Object { $_.title -eq $Title -and $_.author -eq $Author }
if ($dup -and -not $Force) { throw "Audiobook '$Title' by $Author already exists (id $($dup[0].id)). Use -Force to import another copy." }

$book = [ordered]@{ title = $Title; author = $Author }
if ($Narrator) { $book.narrator = $Narrator }
if ($Language) { $book.language = $Language }
if ($PublishedYear) { $book.publishedYear = $PublishedYear }
if ($Description) { $book.description = $Description }
$json = [Text.Encoding]::UTF8.GetBytes(($book | ConvertTo-Json))
$created = Invoke-RestMethod -Method Post -Uri "$api/audiobooks" -ContentType 'application/json; charset=utf-8' -Body $json
Write-Host "Created audiobook $($created.id): $Title"

if ($Cover) {
    $ext = [IO.Path]::GetExtension($Cover).ToLower()
    $ctype = @{ '.jpg' = 'image/jpeg'; '.jpeg' = 'image/jpeg'; '.png' = 'image/png'; '.webp' = 'image/webp' }[$ext]
    Invoke-Curl @('-X', 'PUT', '-F', "file=@$Cover;type=$ctype", "$api/audiobooks/$($created.id)/cover") | Out-Null
    Write-Host 'Uploaded cover'
}

$sw = [Diagnostics.Stopwatch]::StartNew()
$totalBytes = 0
$i = 0
foreach ($f in $files) {
    $i++
    $position = $i
    $chapterTitle = [IO.Path]::GetFileNameWithoutExtension($f.Name)
    if ($chapterTitle -match '^\s*(\d+)\s*[-_.]\s*(.+)$') { $position = [int]$Matches[1]; $chapterTitle = $Matches[2].Trim() }
    $ctype = $contentTypes[$f.Extension.ToLower()]
    $form = @('-X', 'POST', '-F', "file=@$($f.FullName);type=$ctype", '--form-string', "title=$chapterTitle", '--form-string', "position=$position")
    if ($f.Extension -ieq '.mp3') {
        $d = Get-Mp3Duration $f.FullName
        if ($null -ne $d -and $d -gt 0) { $form += @('--form-string', "durationSeconds=$d") }
    }
    $t0 = $sw.Elapsed.TotalSeconds
    $ch = Invoke-Curl ($form + "$api/audiobooks/$($created.id)/chapters")
    $totalBytes += $f.Length
    Write-Host ("  [{0,2}/{1}] {2,-55} {3,6:N1} MB  {4,5:N1}s  {5}" -f $i, $files.Count, $chapterTitle, ($f.Length / 1MB), ($sw.Elapsed.TotalSeconds - $t0), $(if ($ch.durationSeconds) { [TimeSpan]::FromSeconds($ch.durationSeconds).ToString() } else { '' }))
}

$final = Invoke-RestMethod "$api/audiobooks/$($created.id)"
Write-Host ("Done: {0} chapters, {1:N1} MB in {2:N1}s ({3:N1} MB/s). Total duration {4}." -f $final.chapters.Count, ($totalBytes / 1MB), $sw.Elapsed.TotalSeconds, ($totalBytes / 1MB / [math]::Max($sw.Elapsed.TotalSeconds, 0.001)), $(if ($final.totalDurationSeconds) { [TimeSpan]::FromSeconds($final.totalDurationSeconds).ToString() } else { 'unknown' }))
Write-Host "Audiobook id: $($created.id)"
Write-Host "Open: $api/audiobooks/$($created.id)"

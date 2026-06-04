$port = if ($env:PORT) { [int]$env:PORT } else { 8080 }
$root = $PSScriptRoot
$prefix = "http://localhost:$port/"

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add($prefix)
$listener.Start()
Write-Host "Prumo dev server running at $prefix"
Write-Host "Serving files from: $root"
Write-Host "Press Ctrl+C to stop."

$mimeTypes = @{
    ".html" = "text/html; charset=utf-8"
    ".css"  = "text/css"
    ".js"   = "application/javascript"
    ".json" = "application/json"
    ".png"  = "image/png"
    ".jpg"  = "image/jpeg"
    ".jpeg" = "image/jpeg"
    ".ico"  = "image/x-icon"
    ".svg"  = "image/svg+xml"
    ".woff" = "font/woff"
    ".woff2"= "font/woff2"
}

while ($listener.IsListening) {
    $ctx = $null
    try {
        $ctx  = $listener.GetContext()
        $req  = $ctx.Request
        $resp = $ctx.Response

        $urlPath = $req.Url.LocalPath
        if ($urlPath -eq "/" -or $urlPath -eq "") { $urlPath = "/index.html" }

        $relative = $urlPath.TrimStart("/").Replace("/", [IO.Path]::DirectorySeparatorChar)
        $filePath = [IO.Path]::GetFullPath([IO.Path]::Combine($root, $relative))

        # Security: ensure path stays within root
        if (-not $filePath.StartsWith($root)) {
            $resp.StatusCode = 403
            $resp.Close()
            continue
        }

        if ([IO.File]::Exists($filePath)) {
            $bytes = [IO.File]::ReadAllBytes($filePath)
            $ext   = [IO.Path]::GetExtension($filePath).ToLower()
            $resp.ContentType     = if ($mimeTypes[$ext]) { $mimeTypes[$ext] } else { "application/octet-stream" }
            $resp.StatusCode      = 200
            $resp.ContentLength64 = [long]$bytes.Length
            $resp.OutputStream.Write($bytes, 0, $bytes.Length)
            $resp.OutputStream.Flush()
        } else {
            $resp.StatusCode  = 404
            $resp.SendChunked = $false
            $body = [Text.Encoding]::UTF8.GetBytes("Not Found")
            $resp.ContentLength64 = [long]$body.Length
            $resp.ContentType = "text/plain"
            $resp.OutputStream.Write($body, 0, $body.Length)
            $resp.OutputStream.Flush()
        }
    } catch {
        Write-Host "Request error: $_"
        if ($ctx) {
            try { $ctx.Response.StatusCode = 500 } catch {}
        }
    } finally {
        if ($ctx) { try { $ctx.Response.OutputStream.Close() } catch {} }
    }
}

$listener.Stop()

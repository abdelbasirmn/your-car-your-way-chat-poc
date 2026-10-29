$ErrorActionPreference = 'Stop'

Push-Location (Join-Path $PSScriptRoot '..')
try {
    Push-Location frontend
    try {
        npm ci --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw 'Installation npm echouee' }

        npm run build
        if ($LASTEXITCODE -ne 0) { throw 'Build Angular echoue' }
    }
    finally {
        Pop-Location
    }

    $staticPath = 'backend/src/main/resources/static'
    if (Test-Path $staticPath) {
        Remove-Item -Recurse -Force $staticPath
    }
    New-Item -ItemType Directory -Force $staticPath | Out-Null
    Copy-Item -Recurse -Force 'frontend/dist/chat/browser/*' $staticPath

    Push-Location backend
    try {
        .\mvnw.cmd -B -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Build Maven echoue' }
    }
    finally {
        Pop-Location
    }
}
finally {
    Pop-Location
}

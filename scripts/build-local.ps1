$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
Push-Location frontend
npm ci --no-audit --no-fund
if ($LASTEXITCODE -ne 0) { throw 'Installation npm échouée' }
npm run build
if ($LASTEXITCODE -ne 0) { throw 'Build Angular échoué' }
Pop-Location
if (Test-Path backend/src/main/resources/static) { Remove-Item -Recurse -Force backend/src/main/resources/static }
New-Item -ItemType Directory -Force backend/src/main/resources/static | Out-Null
Copy-Item -Recurse -Force frontend/dist/chat/browser/* backend/src/main/resources/static/
Push-Location backend
.\mvnw.cmd -B -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Build Maven échoué' }
Pop-Location

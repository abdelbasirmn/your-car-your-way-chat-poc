#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
(cd frontend && npm ci --no-audit --no-fund && npm run build)
rm -rf backend/src/main/resources/static
mkdir -p backend/src/main/resources/static
cp -R frontend/dist/chat/browser/. backend/src/main/resources/static/
(cd backend && ./mvnw -B -DskipTests package)
printf '\nBuild terminé. Démarrer avec le profil local et une base PostgreSQL.\n'

#!/usr/bin/env bash
# Подтянуть свежий NCANode в вашу ветку custom и сохранить ваши правки.
set -euo pipefail

CUSTOM_BRANCH="${CUSTOM_BRANCH:-custom}"
UPSTREAM_REMOTE="${UPSTREAM_REMOTE:-upstream}"
UPSTREAM_BRANCH="${UPSTREAM_BRANCH:-master}"

cd "$(git rev-parse --show-toplevel)"

if ! git remote get-url "$UPSTREAM_REMOTE" >/dev/null 2>&1; then
  git remote add "$UPSTREAM_REMOTE" https://github.com/ncanode-kz/NCANode.git
fi

echo ">> Скачиваю обновления оригинала..."
git fetch "$UPSTREAM_REMOTE" --tags

echo ">> Переключаюсь на ветку $CUSTOM_BRANCH..."
git checkout "$CUSTOM_BRANCH"

echo ">> Вливаю оригинал ($UPSTREAM_REMOTE/$UPSTREAM_BRANCH) в $CUSTOM_BRANCH..."
if git merge --no-edit "$UPSTREAM_REMOTE/$UPSTREAM_BRANCH"; then
  echo "OK: синхронизация прошла без конфликтов."
  echo "Дальше: git push origin $CUSTOM_BRANCH"
else
  echo "Есть конфликты — нужно решить их вручную (или с агентом), потом:"
  echo "  git add ."
  echo "  git commit"
  echo "  git push origin $CUSTOM_BRANCH"
  exit 1
fi

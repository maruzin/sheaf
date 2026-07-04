#!/usr/bin/env bash
# One-shot: stage everything, commit, push. Usage: ./push.sh "optional message"
set -e
cd "$(dirname "$0")"
rm -f .git/*.lock .git/refs/heads/*.lock .git/objects/*.lock 2>/dev/null || true
git add -A
if git diff --cached --quiet; then
  echo "Nothing to commit — pushing current HEAD."
else
  git commit -m "${1:-sheaf: update}"
fi
git push
echo "Pushed. CI: https://github.com/maruzin/sheaf/actions"

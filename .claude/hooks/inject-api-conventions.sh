#!/usr/bin/env bash
# UserPromptSubmit hook: 사용자 메시지에 API 관련 키워드가 있으면
# API-CONVENTIONS.md 내용을 컨텍스트에 자동 주입한다.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONVENTIONS_FILE="$SCRIPT_DIR/../../API-CONVENTIONS.md"

if [[ ! -f "$CONVENTIONS_FILE" ]]; then
  exit 0
fi

PROMPT=$(jq -r '.prompt // ""')

if echo "$PROMPT" | grep -qiE 'controller|endpoint|\bapi\b|\bDTO\b|\bREST\b|swagger|getMapping|postMapping|patchMapping|deleteMapping|RequestMapping'; then
  jq -Rs '{hookSpecificOutput:{hookEventName:"UserPromptSubmit",additionalContext:("[Auto-injected API-CONVENTIONS.md — project API writing rules]\n\n"+.)}}' < "$CONVENTIONS_FILE"
fi

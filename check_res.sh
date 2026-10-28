#!/usr/bin/env bash
# 资源冲突自检：同名不同扩展名会导致 Duplicate resources 编译失败。
# Android 资源名不含扩展名，poster_placeholder.png 与 .jpg 会撞成同一个资源。
# 用法：bash check_res.sh
set -u
cd "$(dirname "$0")"
bad=0
for d in app/src/main/res/*/; do
  [ -d "$d" ] || continue
  declare -A seen=()
  for f in "$d"*; do
    [ -f "$f" ] || continue
    base="$(basename "$f")"
    name="${base%.*}"
    # values 目录按文件名区分，不参与资源名冲突
    case "$d" in *"/values/"*) continue;; esac
    if [ -n "${seen[$name]:-}" ]; then
      echo "✗ 冲突 [$name]: ${seen[$name]} 与 $base （在 $d）"
      bad=1
    else
      seen["$name"]="$base"
    fi
  done
  unset seen
done
if [ "$bad" -eq 0 ]; then echo "✓ 没有同名资源冲突"; fi
exit $bad

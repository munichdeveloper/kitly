#!/usr/bin/env bash
# Prints the path to the single Spring Boot executable jar in backend/target.
#
# This deliberately does NOT try to pattern-match Spring Boot Maven plugin's
# internal naming for the plain/non-executable jar it keeps alongside the
# repackaged one (that naming is an implementation detail that has changed
# between plugin versions - it currently keeps the plain jar as
# `<finalName>.jar.original`, which doesn't even match `*.jar`). Instead it
# looks at every `target/*.jar` file and only succeeds when there is exactly
# one - failing loudly instead of silently picking the wrong jar if that
# assumption is ever violated (e.g. a future plugin change adds a
# `-sources.jar` or a second jar).
set -euo pipefail

TARGET_DIR="${1:-target}"

mapfile -t jars < <(find "$TARGET_DIR" -maxdepth 1 -name '*.jar' -type f | sort)

if [ "${#jars[@]}" -ne 1 ]; then
  echo "::error::Expected exactly one jar in $TARGET_DIR, found ${#jars[@]}: ${jars[*]-none}" >&2
  exit 1
fi

echo "${jars[0]}"

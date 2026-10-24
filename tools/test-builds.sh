#!/usr/bin/env bash
#
# Checks that build.sh and build.ps1 make the same APK, byte for byte. Windows only: it
# needs Git Bash and PowerShell. Run it after changing either script.
#
#   tools/test-builds.sh
#
# Each case builds a copy of this checkout (tracked and new files, as they are on disk) in a
# temporary folder, signed with a throwaway key, so the real key and this folder are left
# alone. Signing is deterministic, so the same inputs give the same APK. Cases:
#   - build.sh, and build.ps1 under Windows PowerShell and PowerShell 7 ($PWSH, else pwsh
#     on PATH; skipped if neither is found)
#   - a folder with a space in its path
#   - started from another folder
#   - the password read from KEY-INFO.txt instead of HOMESTEAD_KEYPASS

set -euo pipefail
cd "$(dirname "$0")/.."

for t in java powershell.exe cygpath; do
  command -v "$t" >/dev/null || { echo "$t not found (this test runs in Git Bash on Windows)." >&2; exit 1; }
done
# keytool is not always on PATH next to java; ask java where its JDK is.
JDK=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.home = //p' | tr -d '\r')
KEYTOOL=$(command -v keytool || echo "$(cygpath -u "$JDK")/bin/keytool")
PWSH="${PWSH:-$(command -v pwsh || true)}"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
# Forward-slash Windows paths work for bash, the SDK tools and PowerShell alike.
w() { cygpath -m "$1"; }

# Keep the caller's real key settings out of every case.
unset HOMESTEAD_KEYSTORE HOMESTEAD_KEYPASS HOMESTEAD_KEYDIR HOMESTEAD_KEYALIAS
PASS=test-only-password
mkdir -p "$WORK/keys"
"$KEYTOOL" -genkeypair -keystore "$(w "$WORK/keys/homestead-release.keystore")" \
  -alias homestead -keyalg RSA -keysize 2048 -validity 1 -dname "CN=Homestead test" \
  -storepass "$PASS" -keypass "$PASS" -noprompt >/dev/null 2>&1
echo "Password: $PASS" > "$WORK/keys/KEY-INFO.txt"
KEYSTORE=$(w "$WORK/keys/homestead-release.keystore")

copy_tree() {
  mkdir -p "$1"
  git ls-files -z --cached --others --exclude-standard | tar --null -T - -cf - | tar -xf - -C "$1"
}
copy_tree "$WORK/a"
copy_tree "$WORK/with space"

ps51() { powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$1/build.ps1")"; }
ps7()  { "$PWSH" -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$1/build.ps1")"; }
sh_()  { bash "$1/build.sh"; }

REF=""
FAILED=0
N=0
# run "<name>" <tree> <keymode: env|keyinfo> <from: tree|elsewhere> <command> -- one build
run() {
  local name=$1 tree=$2 keymode=$3 from=$4 cmd=$5 log="$WORK/log.txt" hash
  rm -f "$tree/homestead.apk"
  if ( if [ "$keymode" = env ]; then
         export HOMESTEAD_KEYSTORE="$KEYSTORE" HOMESTEAD_KEYPASS="$PASS"
       else
         export HOMESTEAD_KEYDIR="$(w "$WORK/keys")"
       fi
       if [ "$from" = elsewhere ]; then cd "$WORK/keys"; else cd "$tree"; fi
       "$cmd" "$tree" ) >"$log" 2>&1 && [ -f "$tree/homestead.apk" ]; then
    hash=$(sha256sum "$tree/homestead.apk" | cut -c1-16)
    [ -n "$REF" ] || REF=$hash
    if [ "$hash" = "$REF" ]; then
      echo "PASS  $hash  $name"
    else
      echo "FAIL  $hash  $name  (differs from $REF)"; FAILED=$((FAILED + 1))
    fi
  else
    echo "FAIL  (no apk)          $name"; FAILED=$((FAILED + 1))
    sed 's/^/        /' "$log" | tail -15
  fi
  N=$((N + 1))
}

run "build.sh"                                   "$WORK/a"          env     tree      sh_
run "build.ps1, Windows PowerShell"              "$WORK/a"          env     tree      ps51
if [ -n "$PWSH" ]; then
  run "build.ps1, PowerShell 7"                  "$WORK/a"          env     tree      ps7
else
  echo "SKIP                    build.ps1, PowerShell 7 (no pwsh; set PWSH to its path)"
fi
run "build.sh, path with a space"                "$WORK/with space" env     tree      sh_
run "build.ps1, path with a space"               "$WORK/with space" env     tree      ps51
run "build.sh, started from another folder"      "$WORK/a"          env     elsewhere sh_
run "build.ps1, started from another folder"     "$WORK/a"          env     elsewhere ps51
run "build.sh, password from KEY-INFO.txt"       "$WORK/a"          keyinfo tree      sh_
run "build.ps1, password from KEY-INFO.txt"      "$WORK/a"          keyinfo tree      ps51

if [ "$FAILED" -eq 0 ]; then
  echo "All $N builds made the same APK."
else
  echo "$FAILED of $N builds failed or differ." >&2
  exit 1
fi

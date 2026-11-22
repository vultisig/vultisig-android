#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 1 ]]; then
    echo "Usage: $0 <output.aar>" >&2
    exit 2
fi

output="$1"
if [[ "$output" != /* ]]; then
    output="$PWD/$output"
fi

: "${JAVA_HOME:?Set JAVA_HOME to JDK 21}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
android_jar="$ANDROID_HOME/platforms/android-36/android.jar"
if [[ ! -f "$android_jar" || ! -f "$ANDROID_NDK_HOME/source.properties" ]]; then
    echo "Install Android platform 36 and NDK 28.2.13676358." >&2
    exit 1
fi

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
build_dir=$(mktemp -d)
trap 'rm -rf "$build_dir"' EXIT

export GOWORK=off
export GOTOOLCHAIN=go1.26.11
export CGO_LDFLAGS="-Wl,-z,max-page-size=16384"
export PATH="$build_dir:$JAVA_HOME/bin:$PATH"

mobile_version=v0.0.0-20250606033058-a2a15c67f36f
GOBIN="$build_dir" go install \
    "golang.org/x/mobile/cmd/gomobile@$mobile_version" \
    "golang.org/x/mobile/cmd/gobind@$mobile_version"
go mod download
go mod verify

# Use a separate copy for the 32-bit compatibility patch.
source_dir=$(go list -m -f '{{.Dir}}' github.com/vultisig/mobile-tss-lib)
cp -R -- "$source_dir" "$build_dir/upstream"
chmod -R u+w "$build_dir/upstream"
patch --silent -d "$build_dir/upstream/tss" -p0 < derivation-path-int64.patch
mkdir "$build_dir/module"
cp go.mod go.sum tools.go "$build_dir/module/"
cd -- "$build_dir/module"
go mod edit -replace=github.com/vultisig/mobile-tss-lib=../upstream
go test -mod=readonly github.com/vultisig/mobile-tss-lib/tss

gomobile bind -target=android -androidapi=21 -bootclasspath="$android_jar" \
    -trimpath -ldflags="-s -w" -o "$build_dir/mobile-tss-lib.aar" \
    github.com/vultisig/mobile-tss-lib/tss
mv -- "$build_dir/mobile-tss-lib.aar" "$output"

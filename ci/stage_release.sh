#!/usr/bin/env bash
set -euo pipefail

product="${1:?product is required}"
version="${2:?version is required}"
commit="${3:?commit is required}"
staging_root="${4:?staging directory is required}"

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]]; then
  echo "Version must be immutable SemVer: $version" >&2
  exit 1
fi
case "$product" in
  all|library|plugin) ;;
  *) echo "Product must be all, library, or plugin: $product" >&2; exit 1 ;;
esac
case "$staging_root" in
  ""|/|.) echo "Unsafe staging directory: $staging_root" >&2; exit 1 ;;
esac

rm -rf -- "$staging_root"
mkdir -p "$staging_root/repository" "$staging_root/evidence"
repository="$(cd "$staging_root/repository" && pwd)"

if [[ "$product" == "all" || "$product" == "library" ]]; then
  ./gradlew --no-daemon \
    :test-kit:publishMavenJavaPublicationToTestRepository \
    :test-kit:cyclonedxBom \
    -PreleaseVersion="$version" \
    -PtestRepository="$repository"
  mkdir -p "$staging_root/evidence/library"
  cp build/reports/bom/bom.json "$staging_root/evidence/library/jvm-test-kit-$version.cdx.json"
fi

if [[ "$product" == "all" || "$product" == "plugin" ]]; then
  ./gradlew --no-daemon \
    :service-plugin:publishAllPublicationsToTestRepository \
    -PpluginVersion="$version" \
    -PtestRepository="$repository"
fi

python3 ci/staging_manifest.py create "$staging_root" "$product" "$version" "$commit"

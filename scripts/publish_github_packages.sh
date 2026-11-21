#!/usr/bin/env bash
#
# Publishes every telephoto module to GitHub Packages from a local machine.
#
# Run this on macOS: the iOS targets (iosArm64, iosX64, iosSimulatorArm64) are only built and
# published correctly on an Apple host, and all KMP publications plus the root Gradle module
# metadata have to be uploaded together from the same machine.
#
# Usage: ./scripts/publish_github_packages.sh [version]
#   version defaults to VERSION_NAME in gradle.properties.
#
# Credentials are read from Gradle properties, e.g. in ~/.gradle/gradle.properties:
#   GitHubPackagesUsername=<github username>
#   GitHubPackagesPassword=<classic personal access token with write:packages>
# or from the ORG_GRADLE_PROJECT_GitHubPackagesUsername / ..Password environment variables.

set -euo pipefail

if [[ "$(uname)" != "Darwin" ]]; then
  echo "error: publish from macOS so that the iOS artifacts are included." >&2
  exit 1
fi

cd "$(dirname "$0")/.."

args=()
if [[ $# -gt 0 ]]; then
  args+=("-PVERSION_NAME=$1")
fi

# --no-parallel avoids concurrent uploads racing on maven-metadata.xml, which GitHub Packages
# rejects with 409 Conflict.
./gradlew publishAllPublicationsToGitHubPackagesRepository \
  "${args[@]+"${args[@]}"}" \
  --no-parallel --no-configuration-cache --stacktrace

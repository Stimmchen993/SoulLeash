#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BUILD_DIR="target"
DOCKER_USER_ARGS=(--user "$(id -u):$(id -g)")
if [[ ! -w "$BUILD_DIR" && ( -e "$BUILD_DIR" ) ]]; then
  DOCKER_USER_ARGS=()
fi

mkdir -p .build "$HOME/.m2"

MAVEN_ARGS=("-DskipTests" "-Dproject.build.directory=${BUILD_DIR}" "package")
if [[ $# -gt 0 ]]; then
  MAVEN_ARGS=("$@")
fi

if command -v mvn >/dev/null 2>&1; then
  exec mvn "${MAVEN_ARGS[@]}"
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Neither 'mvn' nor 'docker' is available. Install one to build."
  exit 1
fi

exec docker run --rm \
  "${DOCKER_USER_ARGS[@]}" \
  -e MAVEN_CONFIG=/var/maven/.m2 \
  -v "$ROOT_DIR:/workspace" \
  -v "$HOME/.m2:/var/maven/.m2" \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn "${MAVEN_ARGS[@]}"

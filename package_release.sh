#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_FILE="${ROOT_DIR}/app/build.gradle"
GRADLEW="${ROOT_DIR}/gradlew"
GRADLE_TASK="${1:-assembleRelease}"
APK_OUTPUT_DIR="${ROOT_DIR}/app/build/outputs/apk/release"
APK_RELEASE_DIR="${ROOT_DIR}/app/release"
GRADLE_PROPS_FILE="${ROOT_DIR}/gradle.properties"

if [[ ! -f "${BUILD_FILE}" ]]; then
  echo "未找到文件: ${BUILD_FILE}"
  exit 1
fi

if [[ ! -x "${GRADLEW}" ]]; then
  echo "未找到可执行 gradlew: ${GRADLEW}"
  exit 1
fi

read_prop_from_file() {
  local key="$1"
  if [[ -f "${GRADLE_PROPS_FILE}" ]]; then
    awk -F= -v key="${key}" '$1 == key {print substr($0, index($0, "=") + 1)}' "${GRADLE_PROPS_FILE}" | tail -n 1 | tr -d '\r'
  fi
}

resolve_prop() {
  local key="$1"
  local fallback="${2:-}"
  local env_value="${!key:-}"
  if [[ -n "${env_value}" ]]; then
    echo "${env_value}"
    return 0
  fi

  local file_value
  file_value="$(read_prop_from_file "${key}")"
  if [[ -n "${file_value}" ]]; then
    echo "${file_value}"
    return 0
  fi

  echo "${fallback}"
}

require_release_signing() {
  local store_file="$1"
  local store_password="$2"
  local key_alias="$3"
  local key_password="$4"

  if [[ -z "${store_file}" || ! -f "${store_file}" ]]; then
    echo "缺少证书文件: RELEASE_STORE_FILE=${store_file:-<空>}"
    exit 1
  fi
  if [[ -z "${store_password}" ]]; then
    echo "缺少证书密码: RELEASE_STORE_PASSWORD"
    exit 1
  fi
  if [[ -z "${key_alias}" ]]; then
    echo "缺少证书别名: RELEASE_KEY_ALIAS"
    exit 1
  fi
  if [[ -z "${key_password}" ]]; then
    echo "缺少 key 密码: RELEASE_KEY_PASSWORD"
    exit 1
  fi

  if ! command -v keytool >/dev/null 2>&1; then
    echo "未找到 keytool，无法校验证书。"
    exit 1
  fi

  if ! keytool -list -keystore "${store_file}" -storepass "${store_password}" >/dev/null 2>&1; then
    echo "证书文件或 store 密码无效: ${store_file}"
    exit 1
  fi

  if ! keytool -list -keystore "${store_file}" -storepass "${store_password}" -alias "${key_alias}" >/dev/null 2>&1; then
    echo "证书别名不存在: ${key_alias}"
    exit 1
  fi
}

RELEASE_STORE_FILE="$(resolve_prop RELEASE_STORE_FILE "${ROOT_DIR}/keystore/Untitled")"
RELEASE_STORE_PASSWORD="$(resolve_prop RELEASE_STORE_PASSWORD "")"
RELEASE_KEY_ALIAS="$(resolve_prop RELEASE_KEY_ALIAS "")"
RELEASE_KEY_PASSWORD="$(resolve_prop RELEASE_KEY_PASSWORD "${RELEASE_STORE_PASSWORD}")"

require_release_signing "${RELEASE_STORE_FILE}" "${RELEASE_STORE_PASSWORD}" "${RELEASE_KEY_ALIAS}" "${RELEASE_KEY_PASSWORD}"

export RELEASE_STORE_FILE RELEASE_STORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD

current_version_code="$(
  grep -E '^[[:space:]]*versionCode[[:space:]]+[0-9]+' "${BUILD_FILE}" \
    | head -n 1 \
    | sed -E 's/.*versionCode[[:space:]]+([0-9]+).*/\1/'
)"

if [[ -z "${current_version_code}" ]]; then
  echo "无法从 ${BUILD_FILE} 读取 versionCode"
  exit 1
fi

new_version_code=$((current_version_code + 1))
build_succeeded=0

set_version_code() {
  local target_version="$1"
  TARGET_VERSION="${target_version}" perl -i.bak -pe 'if(!$done && s/^(\s*versionCode\s+)\d+/$1$ENV{TARGET_VERSION}/){$done=1}' "${BUILD_FILE}"
  rm -f "${BUILD_FILE}.bak"

  local updated_version_code
  updated_version_code="$(
    grep -E '^[[:space:]]*versionCode[[:space:]]+[0-9]+' "${BUILD_FILE}" \
      | head -n 1 \
      | sed -E 's/.*versionCode[[:space:]]+([0-9]+).*/\1/'
  )"
  if [[ "${updated_version_code}" != "${target_version}" ]]; then
    echo "更新 versionCode 失败，期望 ${target_version}，实际 ${updated_version_code:-<空>}"
    exit 1
  fi
}

rollback_if_failed() {
  if [[ ${build_succeeded} -eq 0 ]]; then
    set +e
    set_version_code "${current_version_code}"
    echo "打包失败，versionCode 已回滚为 ${current_version_code}"
  fi
}

trap rollback_if_failed EXIT

echo "versionCode: ${current_version_code} -> ${new_version_code}"
set_version_code "${new_version_code}"

echo "开始执行打包任务: ${GRADLE_TASK}"
"${GRADLEW}" "${GRADLE_TASK}"

mkdir -p "${APK_RELEASE_DIR}"

apks=()
while IFS= read -r apk; do
  [[ -n "${apk}" ]] && apks+=("${apk}")
done < <(find "${APK_OUTPUT_DIR}" -maxdepth 1 -type f -name "*.apk" | sort)
if [[ ${#apks[@]} -eq 0 ]]; then
  echo "未找到 APK 文件: ${APK_OUTPUT_DIR}"
  exit 1
fi

for apk in "${apks[@]}"; do
  cp -f "${apk}" "${APK_RELEASE_DIR}/"
done

build_succeeded=1
trap - EXIT
echo "打包成功，versionCode 保持为 ${new_version_code}"
echo "APK 已输出到: ${APK_RELEASE_DIR}"

#!/usr/bin/env bash
set -euo pipefail

# GitHub의 일회용 합성 데이터 시험 기기만 대상으로 한다. 개인 기기·알림 덤프는 수집하지 않는다.
if [[ "${GITHUB_ACTIONS:-}" != true || "${CI:-}" != true ]]; then
  printf '%s\n' '이 진단은 GitHub Actions CI 전용입니다. 로컬 기기에서는 실행하지 않습니다.' >&2
  exit 2
fi

api_level="${1:?진단할 API 번호가 필요합니다.}"
phase="${2:?startup 또는 after 단계가 필요합니다.}"
[[ "$api_level" =~ ^[0-9]+$ ]] || exit 2
[[ "$phase" == startup || "$phase" == after ]] || exit 2
diagnostics_dir="app/build/ci-device-diagnostics/api-${api_level}/${phase}"
mkdir -p "$diagnostics_dir"

adb_path="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/platform-tools/adb"
if [[ ! -x "$adb_path" ]]; then
  printf '%s\n' 'ADB가 없어 기기 상태를 수집하지 못했습니다. 기존 실패 단계와 보고서를 확인하세요.' \
    | tee "$diagnostics_dir/unavailable.txt"
  exit 0
fi

timeout 10 "$adb_path" devices -l > "$diagnostics_dir/devices.txt" 2>&1 || true
mapfile -t emulator_serials < <(timeout 10 "$adb_path" devices | awk '$1 ~ /^emulator-[0-9]+$/ && $2 == "device" { print $1 }')
if [[ "${#emulator_serials[@]}" -eq 0 ]]; then
  printf '%s\n' '연결된 시험 에뮬레이터가 없습니다. 종료된 Managed Device의 결과는 UTP·계측 보고서에서 확인하세요.' \
    | tee "$diagnostics_dir/unavailable.txt"
fi

for serial in "${emulator_serials[@]}"; do
  device_dir="$diagnostics_dir/$serial"
  mkdir -p "$device_dir"
  device_adb() { timeout 10 "$adb_path" -s "$serial" "$@"; }
  {
    printf 'API: '
    device_adb shell getprop ro.build.version.sdk || true
    device_adb shell wm size || true
    device_adb shell wm density || true
    printf '\n기본 IME:\n'
    device_adb shell settings get secure default_input_method || true
    printf '\n하드웨어 키보드 사용 중 IME 표시:\n'
    device_adb shell settings get secure show_ime_with_hard_keyboard || true
    printf '\nWebView 제공자:\n'
    device_adb shell dumpsys webviewupdate || true
  } 2>&1 | tee "$device_dir/environment.txt"

  if [[ "$phase" == after ]]; then
    # 알림 본문을 포함하는 dumpsys notification 대신 실패·창·입력기 태그만 제한 수집한다.
    device_adb logcat -d -v threadtime -t 2000 \
      AndroidRuntime:E chromium:E WebViewFactory:V ActivityManager:I \
      ActivityTaskManager:I InputMethodManager:V '*:S' \
      > "$device_dir/logcat.txt" 2>&1 || true
    device_adb exec-out screencap -p > "$device_dir/screen.png" \
      2> "$device_dir/screencap-error.txt" || true
  fi
done

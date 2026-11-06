#!/usr/bin/env bash
set -euo pipefail

base_url="http://127.0.0.1:39090/api/v1/work-orders"
application_log="${RUNNER_TEMP:-/tmp}/maintenance-postgres-smoke.log"
response_file="${RUNNER_TEMP:-/tmp}/maintenance-postgres-response.json"

java -jar target/maintenance-sla-service-0.1.0-SNAPSHOT.jar --server.port=39090 >"${application_log}" 2>&1 &
application_pid=$!

cleanup() {
  kill "${application_pid}" 2>/dev/null || true
}
trap cleanup EXIT

for _ in $(seq 1 60); do
  status=$(curl --silent --output "${response_file}" --write-out '%{http_code}' "${base_url}/missing" || true)
  if [[ "${status}" == "401" ]]; then
    break
  fi
  if ! kill -0 "${application_pid}" 2>/dev/null; then
    cat "${application_log}"
    exit 1
  fi
  sleep 1
done

if [[ "${status:-}" != "401" ]]; then
  cat "${application_log}"
  echo "Application did not become ready" >&2
  exit 1
fi

request='{"title":"CI PostgreSQL incident","description":"PostgreSQL and Flyway smoke verification","assetId":"CI-CNC-01","priority":"CRITICAL","reportedBy":"reporter-1","idempotencyKey":"ci-postgres-smoke-1","createdAt":"2026-11-06T08:00:00Z"}'

assert_request() {
  local expected_status=$1
  local method=$2
  local url=$3
  local token=$4
  local body=${5:-}
  local status
  if [[ -n "${body}" ]]; then
    status=$(curl --silent --output "${response_file}" --write-out '%{http_code}' \
      --request "${method}" --header "Authorization: Bearer ${token}" \
      --header 'Content-Type: application/json' --data "${body}" "${url}")
  else
    status=$(curl --silent --output "${response_file}" --write-out '%{http_code}' \
      --request "${method}" --header "Authorization: Bearer ${token}" "${url}")
  fi
  if [[ "${status}" != "${expected_status}" ]]; then
    cat "${response_file}"
    echo "Expected HTTP ${expected_status}, got ${status} for ${method} ${url}" >&2
    exit 1
  fi
}

assert_request 201 POST "${base_url}" reporter-token "${request}"
work_order_id=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' "${response_file}")
assert_request 200 POST "${base_url}" reporter-token "${request}"
assert_request 403 POST "${base_url}/${work_order_id}/closure" engineer-token '{"expectedVersion":1,"occurredAt":"2026-11-06T08:01:00Z"}'
assert_request 200 POST "${base_url}/${work_order_id}/acknowledgement" coordinator-token '{"expectedVersion":1,"occurredAt":"2026-11-06T08:01:00Z"}'
assert_request 409 POST "${base_url}/${work_order_id}/start" engineer-token '{"expectedVersion":1,"occurredAt":"2026-11-06T08:02:00Z"}'
assert_request 200 POST "${base_url}/${work_order_id}/start" engineer-token '{"expectedVersion":2,"occurredAt":"2026-11-06T08:02:00Z"}'
assert_request 200 POST "${base_url}/${work_order_id}/resolution" engineer-token '{"expectedVersion":3,"occurredAt":"2026-11-06T08:05:00Z","summary":"Cooling fan replaced"}'
assert_request 200 POST "${base_url}/${work_order_id}/closure" supervisor-token '{"expectedVersion":4,"occurredAt":"2026-11-06T08:06:00Z"}'

final_status=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["status"])' "${response_file}")
final_version=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["version"])' "${response_file}")
if [[ "${final_status}" != "CLOSED" || "${final_version}" != "5" ]]; then
  cat "${response_file}"
  echo "Unexpected final work-order state" >&2
  exit 1
fi

echo "PostgreSQL smoke test passed for work order ${work_order_id}"

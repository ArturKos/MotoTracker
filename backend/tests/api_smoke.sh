#!/usr/bin/env bash
# Smoke test for the MotoTracker backend API (register / login / api_routes).
# Verifies HTTP status codes and upsert idempotency without needing DB access.
#
# Usage: BASE_URL=http://192.168.1.145/gpstrack ./api_smoke.sh
#        (defaults to http://localhost/gpstrack)
set -u

BASE_URL="${BASE_URL:-http://localhost/gpstrack}"
JAR="$(mktemp)"
EMAIL="smoke+$(date +%s)@example.com"
PASS="secret12345"
UUID="smoke-uuid-$(date +%s)"
fails=0

# check <label> <expected-code> <actual-code> [extra]
check() {
  if [ "$2" = "$3" ]; then echo "PASS  $1 ($3)"
  else echo "FAIL  $1 — expected $2 got $3  ${4:-}"; fails=$((fails+1)); fi
}

route_json() { # $1 = uuid, $2 = name (empty name => omit for the 400 case)
  if [ -z "$2" ]; then
    printf '{"id":"%s","dateEpochMs":1721560000000,"km":12.3,"durSec":1800,"avg":24.6,"max":88.0,"pathJson":"[[53.4,14.5],[53.41,14.52]]"}' "$1"
  else
    printf '{"id":"%s","name":"%s","dateEpochMs":1721560000000,"km":12.3,"durSec":1800,"avg":24.6,"max":88.0,"pathJson":"[[53.4,14.5],[53.41,14.52]]"}' "$1" "$2"
  fi
}

post_json() { # $1 url  $2 body  [cookie flag: use-jar|no-jar]
  local jarflag=(-c "$JAR" -b "$JAR"); [ "${3:-use-jar}" = "no-jar" ] && jarflag=()
  curl -s -o /tmp/smoke_body -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" "${jarflag[@]}" \
    --data "$2" "$1"
}

echo "== BASE_URL=$BASE_URL =="

# 1. register (new e-mail) -> 200, capture write_api_key from the response
code=$(post_json "$BASE_URL/register.php" "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")
check "register new" 200 "$code" "$(cat /tmp/smoke_body)"
WKEY=$(sed -n 's/.*"write_api_key":"\([a-f0-9]*\)".*/\1/p' /tmp/smoke_body)
[ -n "$WKEY" ] && echo "PASS  register returns write_api_key" || { echo "FAIL  register missing write_api_key"; fails=$((fails+1)); }

# 2. register duplicate -> 409
code=$(post_json "$BASE_URL/register.php" "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" no-jar)
check "register duplicate" 409 "$code"

# 3. register weak password -> 400
code=$(post_json "$BASE_URL/register.php" "{\"email\":\"weak+$(date +%s)@example.com\",\"password\":\"short\"}" no-jar)
check "register weak password" 400 "$code"

# 4. login by e-mail -> 200 (fresh jar)
: > "$JAR"
code=$(post_json "$BASE_URL/login.php" "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")
check "login by email" 200 "$code" "$(cat /tmp/smoke_body)"

# 5. api_routes without cookie -> 401
code=$(post_json "$BASE_URL/api_routes.php" "$(route_json "$UUID" "Poranna trasa")" no-jar)
check "api_routes no session" 401 "$code"

# 6. api_routes with cookie -> 200, capture route_id
code=$(post_json "$BASE_URL/api_routes.php" "$(route_json "$UUID" "Poranna trasa")")
check "api_routes authed" 200 "$code" "$(cat /tmp/smoke_body)"
id1=$(sed -n 's/.*"route_id":\([0-9]*\).*/\1/p' /tmp/smoke_body)

# 7. api_routes same client_uuid -> 200 and SAME route_id (upsert, no duplicate)
code=$(post_json "$BASE_URL/api_routes.php" "$(route_json "$UUID" "Poranna trasa (edit)")")
check "api_routes upsert status" 200 "$code"
id2=$(sed -n 's/.*"route_id":\([0-9]*\).*/\1/p' /tmp/smoke_body)
if [ -n "$id1" ] && [ "$id1" = "$id2" ]; then echo "PASS  upsert same route_id ($id1)"
else echo "FAIL  upsert route_id — first=$id1 second=$id2 (duplicate?)"; fails=$((fails+1)); fi

# 8. api_routes missing name -> 400
code=$(post_json "$BASE_URL/api_routes.php" "$(route_json "$UUID-x" "")")
check "api_routes missing name" 400 "$code"

# 9. api_routes with write_api_key Bearer token, NO cookie -> 200 (background sync auth)
code=$(curl -s -o /tmp/smoke_body -w "%{http_code}" -X POST \
  -H "Content-Type: application/json" -H "Authorization: Bearer $WKEY" \
  --data "$(route_json "$UUID-key" "Trasa przez klucz")" "$BASE_URL/api_routes.php")
check "api_routes via write_api_key (no cookie)" 200 "$code" "$(cat /tmp/smoke_body)"

# 10. api_routes with a bogus token, no cookie -> 401
code=$(curl -s -o /tmp/smoke_body -w "%{http_code}" -X POST \
  -H "Content-Type: application/json" -H "Authorization: Bearer deadbeefdeadbeefdeadbeefdeadbeef" \
  --data "$(route_json "$UUID-bad" "x")" "$BASE_URL/api_routes.php")
check "api_routes bogus token" 401 "$code"

rm -f "$JAR" /tmp/smoke_body
echo "== $([ $fails -eq 0 ] && echo 'ALL PASS' || echo "$fails FAILED") =="
exit $fails

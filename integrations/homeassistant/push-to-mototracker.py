#!/usr/bin/env python3
"""
Push Traccar positions (including interpolated ones) to MotoTracker backend.

Runs as a third stage in snap-loop.sh, after snap-to-road.py and
interpolate-routes.py. For each configured Traccar device, finds recent
positions that haven't been pushed yet and sends them via MotoTracker's
existing HTTP ingest endpoint. Marks each pushed row with
attributes.pushedToMotoTracker=true so we don't re-send.
"""

import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone

DB_HOST = os.environ.get("DB_HOST", "core-mariadb")
DB_USER = os.environ.get("DB_USER", "service")
DB_PASS = os.environ.get("DB_PASS", "")
DB_NAME = os.environ.get("DB_NAME", "traccar")

MOTOTRACKER_URL     = os.environ.get("MOTOTRACKER_URL", "http://192.168.1.145/gpstrack/gps_tracker_add_data_to_db.php")
MOTOTRACKER_API_KEY = os.environ.get("MOTOTRACKER_API_KEY", "")

# Comma-separated Traccar device IDs to push, e.g. "1,2,3,5,6"
DEVICE_IDS = [int(x) for x in os.environ.get("MOTOTRACKER_DEVICE_IDS", "").split(",") if x.strip()]

# How to map Traccar device id -> MotoTracker device code.
# Default convention: HA_<traccar_id>
def device_code_for(traccar_id):
    return f"HA_{traccar_id}"

LOOKBACK_MINUTES = int(os.environ.get("PUSH_LOOKBACK_MINUTES", "30"))
HTTP_TIMEOUT_S   = 5


def mysql_query(sql, fetch=True):
    cmd = [
        "mysql",
        f"-u{DB_USER}",
        f"-p{DB_PASS}",
        "-h", DB_HOST,
        DB_NAME,
        "-B", "-N",
        "-e", sql,
    ]
    full_cmd = ["docker", "exec", "-i", "addon_core_mariadb"] + cmd
    result = subprocess.run(full_cmd, capture_output=True, text=True, timeout=30)
    if result.returncode != 0:
        print(f"MySQL error: {result.stderr}", file=sys.stderr)
        return None
    if not fetch:
        return True
    rows = []
    for line in result.stdout.strip().splitlines():
        if line:
            rows.append(line.split("\t"))
    return rows


def mysql_escape(s):
    return str(s).replace("\\", "\\\\").replace("'", "\\'")


def get_pending(device_id):
    """Positions for device in last N min that haven't been pushed yet."""
    cutoff = (datetime.now(timezone.utc) - timedelta(minutes=LOOKBACK_MINUTES)).strftime("%Y-%m-%d %H:%M:%S")
    sql = f"""
        SELECT id, fixtime, latitude, longitude, speed,
               COALESCE(attributes, '{{}}')
        FROM tc_positions
        WHERE deviceid = {device_id}
          AND fixtime > '{cutoff}'
          AND valid = 1
          AND (attributes IS NULL OR attributes NOT LIKE '%"pushedToMotoTracker"%')
        ORDER BY fixtime
    """
    rows = mysql_query(sql)
    if rows is None:
        return []
    positions = []
    for row in rows:
        try:
            attrs = json.loads(row[5]) if row[5] and row[5] != "NULL" else {}
        except json.JSONDecodeError:
            attrs = {}
        positions.append({
            "id": int(row[0]),
            "fixtime": row[1],
            "lat": float(row[2]),
            "lon": float(row[3]),
            # Traccar speed is in knots; MotoTracker expects km/h
            "speed_kmh": float(row[4]) * 1.852 if row[4] and row[4] != "NULL" else 0.0,
            "attrs": attrs,
        })
    return positions


def mark_pushed(pos_id, attrs):
    attrs["pushedToMotoTracker"] = True
    new_json = json.dumps(attrs)
    sql = f"""
        UPDATE tc_positions
        SET attributes = '{mysql_escape(new_json)}'
        WHERE id = {pos_id}
    """
    return mysql_query(sql, fetch=False)


def push_position(device_code, pos):
    params = {
        "k":  MOTOTRACKER_API_KEY,
        "v":  device_code,
        "la": f"{pos['lat']:.7f}",
        "lo": f"{pos['lon']:.7f}",
        "s":  f"{pos['speed_kmh']:.1f}",
        "t":  "0",
        "h":  "0",
        "b":  "0",
        "ts": pos["fixtime"],
    }
    url = MOTOTRACKER_URL + "?" + urllib.parse.urlencode(params)
    try:
        with urllib.request.urlopen(url, timeout=HTTP_TIMEOUT_S) as resp:
            body = resp.read().decode().strip()
        return body == "OK"
    except Exception as e:
        print(f"push error for pos {pos['id']}: {e}", file=sys.stderr)
        return False


def main():
    if not DB_PASS:
        print("DB_PASS not set", file=sys.stderr); sys.exit(1)
    if not MOTOTRACKER_API_KEY:
        print("MOTOTRACKER_API_KEY not set", file=sys.stderr); sys.exit(1)
    if not DEVICE_IDS:
        print("MOTOTRACKER_DEVICE_IDS empty — nothing to push")
        return

    total_pushed = 0
    total_failed = 0
    for dev_id in DEVICE_IDS:
        code = device_code_for(dev_id)
        pending = get_pending(dev_id)
        if not pending:
            continue
        dev_pushed = 0
        dev_failed = 0
        for pos in pending:
            if push_position(code, pos):
                mark_pushed(pos["id"], pos["attrs"])
                dev_pushed += 1
            else:
                dev_failed += 1
        total_pushed += dev_pushed
        total_failed += dev_failed
        if dev_pushed or dev_failed:
            print(f"  dev {dev_id} ({code}): pushed={dev_pushed} failed={dev_failed}")

    print(f"Total pushed: {total_pushed}, failed: {total_failed}")


if __name__ == "__main__":
    main()

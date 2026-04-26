#!/usr/bin/env python3
"""Snap-to-road + route-interpolation worker for the MotoTracker standalone
instance. Reads rows with source='raw' from the `points` table, POSTs each
device's recent trail to OSRM /match, updates the rows to source='snapped'
with road-aligned lat/lon, then samples OSRM /route between consecutive
snapped points to fill long gaps with source='interpolated' rows.

Config via env:
    DB_HOST, DB_USER, DB_PASS, DB_NAME          # MariaDB creds (required)
    OSRM_URL=http://127.0.0.1:5001              # osrm-routed endpoint
    LOOKBACK_MINUTES=60                         # how far back to scan raw rows
    MATCH_BATCH_SIZE=100                        # max points per /match call
    RIDE_GAP_SECONDS=1800                       # gap that starts a new ride
    MIN_GAP_M=40                                # skip interp below this
    MAX_GAP_M=5000                              # skip interp above this
    POINT_SPACING_M=25                          # sample every N metres
    MAX_POINTS_PER_GAP=10                       # cap per gap
    MAX_ROUTE_RATIO=3.0                         # reject detours
    MAX_PAIR_SECONDS=900                        # skip pairs > this apart
    DRY_RUN=0                                   # 1 = no writes

Exit 0 on success. Non-zero on connection/DB errors.
"""

from __future__ import annotations

import math
import os
import sys
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Iterable

try:
    import pymysql
    import requests
except ImportError as e:
    sys.stderr.write(f"Missing dependency: {e}. Install with:\n")
    sys.stderr.write("  sudo apt install python3-pymysql python3-requests\n")
    sys.exit(2)


# ---------- config ----------

def env_int(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, default))
    except ValueError:
        return default

def env_float(name: str, default: float) -> float:
    try:
        return float(os.environ.get(name, default))
    except ValueError:
        return default

DB_HOST = os.environ.get("DB_HOST", "localhost")
DB_USER = os.environ["DB_USER"] if "DB_USER" in os.environ else "gps_db_dbuser"
DB_PASS = os.environ.get("DB_PASS", "")
DB_NAME = os.environ.get("DB_NAME", "gps_db_data")

OSRM_URL           = os.environ.get("OSRM_URL", "http://127.0.0.1:5001").rstrip("/")
LOOKBACK_MINUTES   = env_int("LOOKBACK_MINUTES", 60)
MATCH_BATCH_SIZE   = env_int("MATCH_BATCH_SIZE", 100)
RIDE_GAP_SECONDS   = env_int("RIDE_GAP_SECONDS", 1800)
MIN_GAP_M          = env_float("MIN_GAP_M", 40.0)
MAX_GAP_M          = env_float("MAX_GAP_M", 5000.0)
POINT_SPACING_M    = env_float("POINT_SPACING_M", 25.0)
MAX_POINTS_PER_GAP = env_int("MAX_POINTS_PER_GAP", 10)
MAX_ROUTE_RATIO    = env_float("MAX_ROUTE_RATIO", 3.0)
MAX_PAIR_SECONDS   = env_int("MAX_PAIR_SECONDS", 900)
DRY_RUN            = env_int("DRY_RUN", 0) == 1


# ---------- types ----------

@dataclass
class Point:
    id: int
    device_id: int
    lat: float
    lon: float
    speed: float | None
    timestamp: datetime


def log(msg: str) -> None:
    print(f"{datetime.utcnow().isoformat(timespec='seconds')} {msg}", file=sys.stderr)


# ---------- geo ----------

_EARTH_M = 6_371_000.0

def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    p1 = math.radians(lat1)
    p2 = math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * _EARTH_M * math.asin(math.sqrt(a))


def polyline_length_m(coords: list[list[float]]) -> float:
    total = 0.0
    for i in range(1, len(coords)):
        lon1, lat1 = coords[i - 1]
        lon2, lat2 = coords[i]
        total += haversine_m(lat1, lon1, lat2, lon2)
    return total


def sample_polyline(coords: list[list[float]], spacing_m: float, max_points: int) -> list[tuple[float, float, float]]:
    """Return list of (lat, lon, fraction_along_polyline) sampled every spacing_m.
    Excludes endpoints. fraction is 0..1."""
    if len(coords) < 2:
        return []
    total = polyline_length_m(coords)
    if total < spacing_m * 1.5:
        return []

    n = min(max_points, int(total // spacing_m) - 1)
    if n <= 0:
        return []
    step = total / (n + 1)

    out: list[tuple[float, float, float]] = []
    target = step
    walked = 0.0
    for i in range(1, len(coords)):
        lon1, lat1 = coords[i - 1]
        lon2, lat2 = coords[i]
        seg = haversine_m(lat1, lon1, lat2, lon2)
        while walked + seg >= target and len(out) < n:
            frac_in_seg = (target - walked) / seg if seg > 0 else 0
            lat = lat1 + (lat2 - lat1) * frac_in_seg
            lon = lon1 + (lon2 - lon1) * frac_in_seg
            out.append((lat, lon, target / total))
            target += step
        walked += seg
    return out


# ---------- OSRM ----------

def osrm_match(points: list[Point]) -> list[tuple[int, float, float] | None]:
    """Call /match and return per-input-point (idx_in_input, matched_lat, matched_lon)
    or None when OSRM dropped the point. Falls back to returning all Nones on error."""
    if len(points) < 2:
        return [None] * len(points)
    coords = ";".join(f"{p.lon:.6f},{p.lat:.6f}" for p in points)
    epoch_base = int(points[0].timestamp.timestamp())
    timestamps = ";".join(str(int(p.timestamp.timestamp()) - epoch_base + epoch_base) for p in points)
    radiuses = ";".join(["25"] * len(points))
    url = f"{OSRM_URL}/match/v1/driving/{coords}"
    params = {
        "geometries": "geojson",
        "overview": "false",
        "tidy": "true",
        "timestamps": timestamps,
        "radiuses": radiuses,
    }
    try:
        r = requests.get(url, params=params, timeout=15)
        r.raise_for_status()
        data = r.json()
    except Exception as e:
        log(f"osrm /match error for device={points[0].device_id} n={len(points)}: {e}")
        return [None] * len(points)
    if data.get("code") != "Ok":
        log(f"osrm /match non-Ok: {data.get('code')} msg={data.get('message')}")
        return [None] * len(points)

    # /match returns "tracepoints" aligned 1:1 with input; null means not matched.
    out: list[tuple[int, float, float] | None] = []
    for i, tp in enumerate(data.get("tracepoints", [])):
        if tp is None:
            out.append(None)
        else:
            lon, lat = tp["location"]
            out.append((i, lat, lon))
    while len(out) < len(points):
        out.append(None)
    return out


def osrm_route(a: Point, b: Point) -> list[list[float]] | None:
    """Return geojson LineString coordinates [[lon,lat], ...] for A→B, or None."""
    url = f"{OSRM_URL}/route/v1/driving/{a.lon:.6f},{a.lat:.6f};{b.lon:.6f},{b.lat:.6f}"
    params = {"geometries": "geojson", "overview": "full", "steps": "false"}
    try:
        r = requests.get(url, params=params, timeout=10)
        r.raise_for_status()
        data = r.json()
    except Exception as e:
        log(f"osrm /route error A={a.id} B={b.id}: {e}")
        return None
    if data.get("code") != "Ok" or not data.get("routes"):
        return None
    return data["routes"][0]["geometry"]["coordinates"]


# ---------- main work ----------

def fetch_raw_points(conn) -> dict[int, list[Point]]:
    cutoff = datetime.utcnow() - timedelta(minutes=LOOKBACK_MINUTES)
    sql = (
        "SELECT p.id, p.device_id, p.lat, p.lon, p.speed, p.timestamp "
        "FROM points p JOIN devices d ON d.id = p.device_id "
        "WHERE p.source = 'raw' AND d.active = 1 AND p.timestamp >= %s "
        "ORDER BY p.device_id, p.timestamp"
    )
    per_device: dict[int, list[Point]] = {}
    with conn.cursor() as cur:
        cur.execute(sql, (cutoff,))
        for row in cur.fetchall():
            pt = Point(row[0], row[1], float(row[2]), float(row[3]),
                       float(row[4]) if row[4] is not None else None, row[5])
            per_device.setdefault(pt.device_id, []).append(pt)
    return per_device


def split_into_rides(points: list[Point]) -> Iterable[list[Point]]:
    if not points:
        return
    cur: list[Point] = [points[0]]
    for p in points[1:]:
        gap = (p.timestamp - cur[-1].timestamp).total_seconds()
        if gap > RIDE_GAP_SECONDS:
            yield cur
            cur = [p]
        else:
            cur.append(p)
    if cur:
        yield cur


def batched(seq: list[Point], size: int) -> Iterable[list[Point]]:
    for i in range(0, len(seq), size):
        yield seq[i:i + size]


def snap_ride(conn, ride: list[Point]) -> list[Point]:
    """Run /match on the ride in batches. Update each raw row to source='snapped'
    with matched coords (or original coords if unmatched). Return the rows
    as in-memory Point objects reflecting new coords for the interpolation phase."""
    updated: list[Point] = []
    with conn.cursor() as cur:
        for batch in batched(ride, MATCH_BATCH_SIZE):
            results = osrm_match(batch)
            for p, match in zip(batch, results):
                if match is None:
                    new_lat, new_lon = p.lat, p.lon
                else:
                    _, new_lat, new_lon = match
                if not DRY_RUN:
                    cur.execute(
                        "UPDATE points SET lat=%s, lon=%s, source='snapped' WHERE id=%s",
                        (new_lat, new_lon, p.id),
                    )
                updated.append(Point(p.id, p.device_id, new_lat, new_lon, p.speed, p.timestamp))
    if not DRY_RUN:
        conn.commit()
    return updated


def interpolate_ride(conn, ride: list[Point]) -> int:
    """For each consecutive pair in the snapped ride, call /route, sample, insert
    interpolated rows. Returns count of inserted rows."""
    inserted = 0
    with conn.cursor() as cur:
        for a, b in zip(ride, ride[1:]):
            dt_s = (b.timestamp - a.timestamp).total_seconds()
            if dt_s <= 0 or dt_s > MAX_PAIR_SECONDS:
                continue
            straight = haversine_m(a.lat, a.lon, b.lat, b.lon)
            if straight < MIN_GAP_M or straight > MAX_GAP_M:
                continue
            coords = osrm_route(a, b)
            if not coords or len(coords) < 2:
                continue
            route_len = polyline_length_m(coords)
            if route_len == 0 or route_len / max(straight, 1.0) > MAX_ROUTE_RATIO:
                continue
            samples = sample_polyline(coords, POINT_SPACING_M, MAX_POINTS_PER_GAP)
            if not samples:
                continue
            for lat, lon, frac in samples:
                ts = a.timestamp + timedelta(seconds=dt_s * frac)
                if DRY_RUN:
                    inserted += 1
                    continue
                cur.execute(
                    "INSERT INTO points (device_id, lat, lon, speed, timestamp, source, parent_id) "
                    "VALUES (%s, %s, %s, %s, %s, 'interpolated', %s)",
                    (a.device_id, lat, lon, a.speed, ts, a.id),
                )
                inserted += 1
    if not DRY_RUN:
        conn.commit()
    return inserted


def main() -> int:
    try:
        conn = pymysql.connect(
            host=DB_HOST, user=DB_USER, password=DB_PASS, database=DB_NAME,
            autocommit=False, charset="utf8mb4",
        )
    except pymysql.err.OperationalError as e:
        log(f"DB connect error: {e}")
        return 1

    try:
        per_device = fetch_raw_points(conn)
        total_raw = sum(len(v) for v in per_device.values())
        log(f"raw rows in last {LOOKBACK_MINUTES}m: {total_raw} across {len(per_device)} device(s)")

        total_snapped = 0
        total_interp = 0
        for device_id, pts in per_device.items():
            for ride in split_into_rides(pts):
                if len(ride) < 2:
                    continue
                snapped = snap_ride(conn, ride)
                total_snapped += len(snapped)
                total_interp += interpolate_ride(conn, snapped)

        log(f"done — snapped={total_snapped} interpolated={total_interp} dry_run={int(DRY_RUN)}")
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())

# OSRM for MotoTracker standalone

Road-snap + route interpolation pipeline for the ubuntu instance. Mirrors the
Home Assistant setup on malinka but runs server-side against the MotoTracker
`points` table directly — no Traccar, no HA dependency.

## One-time graph prep

Ubuntu has 6+ GiB free RAM and 430 GB disk, so Poland fits comfortably.
On ubuntu (as a user with docker privileges):

```bash
cd ~/mototracker-osrm
mkdir -p data && cd data

# 1. Download the latest Poland extract (~900 MB)
wget https://download.geofabrik.de/europe/poland-latest.osm.pbf

# 2. Preprocess (20-30 min, peaks ~3 GB RAM)
docker run --rm -v "$PWD:/data" osrm/osrm-backend osrm-extract   -p /opt/car.lua    /data/poland-latest.osm.pbf
docker run --rm -v "$PWD:/data" osrm/osrm-backend osrm-partition             /data/poland-latest.osrm
docker run --rm -v "$PWD:/data" osrm/osrm-backend osrm-customize             /data/poland-latest.osrm
```

## Start the service

```bash
cd ~/mototracker-osrm && docker compose up -d
curl -s 'http://127.0.0.1:5001/nearest/v1/car/19.48,52.07' | head -c 200
```

## Re-run on new PBF

Geofabrik publishes fresh extracts daily. Schedule a weekly refresh:

```bash
wget -O data/poland-latest.osm.pbf.new https://download.geofabrik.de/europe/poland-latest.osm.pbf
mv data/poland-latest.osm.pbf.new data/poland-latest.osm.pbf
# re-run the three osrm-extract / partition / customize steps
docker compose restart osrm
```

## Next step

Once OSRM is up, the snap/interpolate worker at `../../tools/snap_interpolate.py`
reads raw `points` rows (where `source='raw'`), calls `/match` + `/route`, and
inserts interpolated rows with `source='interpolated'`. See that file for env
vars (DB creds, OSRM URL) and the migration needed to add the `source` column.

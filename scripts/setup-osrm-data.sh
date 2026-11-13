#!/usr/bin/env bash
# Build the OSRM road-network data for the Florida extract, once.
#
#   ./scripts/setup-osrm-data.sh
#
# Downloads florida-latest.osm.pbf from Geofabrik (~500 MB) into
# osrm-data/ and runs, inside the osrm-backend image, the three MLD
# preprocessing steps: extract, partition, customize. The result is
# florida-latest.osrm (+ .osrm.* side files), which the `osrm` compose
# service serves with `osrm-routed --algorithm mld`.
#
# Re-running is safe: it skips the download when the .osm.pbf is already
# present and rebuilds the .osrm files from it. Expect a few minutes and
# roughly 2 GB of disk in osrm-data/. Nothing here touches the app.
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p osrm-data
cd osrm-data

PBF="florida-latest.osm.pbf"
OSRM="florida-latest.osrm"

if [ ! -f "$PBF" ]; then
  echo "Downloading Florida extract from Geofabrik (~500 MB)..."
  curl -fL -o "$PBF" "https://download.geofabrik.de/north-america/us/florida-latest.osm.pbf"
else
  echo "Using existing $PBF"
fi

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: the Docker daemon is not running; start Docker and re-run." >&2
  exit 1
fi

# The image is pulled once; each step runs as a throwaway container with
# the data directory mounted read-write.
echo "Extracting road network (osrm-extract)..."
docker run --rm -v "$PWD:/data" osrm/osrm-backend:v5.27.1 \
  osrm-extract -p /opt/car.lua "/data/$PBF"

echo "Partitioning for MLD (osrm-partition)..."
docker run --rm -v "$PWD:/data" osrm/osrm-backend:v5.27.1 \
  osrm-partition "/data/$OSRM"

echo "Customizing for MLD (osrm-customize)..."
docker run --rm -v "$PWD:/data" osrm/osrm-backend:v5.27.1 \
  osrm-customize "/data/$OSRM"

echo "Done. $OSRM is ready — start the router with:"
echo "  docker compose --profile routing up --build"

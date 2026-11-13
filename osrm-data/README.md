# OSRM road-network data

This directory holds the pre-processed Florida road network that the
`osrm` compose service serves. It is **not** in the repo: the source
extract is ~500 MB and the processed files are ~2 GB.

Build it once, on a machine with Docker running:

```bash
./scripts/setup-osrm-data.sh
```

That downloads `florida-latest.osm.pbf` from Geofabrik and runs
`osrm-extract`, `osrm-partition` and `osrm-customize` (MLD profile) to
produce `florida-latest.osrm` and its side files. Then start the router
with the app:

```bash
docker compose --profile routing up --build
```

The router answers at `http://localhost:5000` (or `${OSRM_PORT}`).
Without this data — or without the `routing` profile — the app still
starts, routing endpoints answer 503, and the fleet map falls back to
straight lines.

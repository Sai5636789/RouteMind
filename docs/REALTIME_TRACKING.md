# Real-Time Tracking

How live vehicle positions move from the simulator to the fleet map.

## GPS simulator

A built in simulator moves each active vehicle along its assigned route
at 40 km/h, emitting a new position every 5 seconds. Positions are
simulated: no real vehicles, phones or GPS hardware are involved. The
simulator exists so the tracking pipeline can be exercised end to end on
any machine.

## Redis GEO

Each position update is written to a Redis GEO set keyed by vehicle. The
tracking service reads the latest position per vehicle from Redis, so the
map never waits on the database for live data. The tracking endpoints also
expose a nearby search: given a point and a radius, return the vehicles
currently inside it.

## WebSocket broadcast

The backend publishes position updates over STOMP on `/topic/vehicles`.
The Angular fleet map subscribes to that topic and moves the markers as
updates arrive. If the WebSocket connection drops, the map falls back to
polling the tracking snapshot endpoint on a short interval, so markers
keep moving with slightly more delay.

## What the map shows

* Vehicle markers at their latest simulated position, coloured by status
* The depot marker in Gainesville
* Route lines for assigned routes, drawn from OSRM road geometry when the
  routing profile is running, otherwise as straight lines between stops
* A breakdown marker when a vehicle reports an incident, until it is
  resolved

## Data flow

Simulator tick (5s) -> Redis GEO write -> WebSocket broadcast on
`/topic/vehicles` -> fleet map marker update. The database is written only
when a vehicle status or an order state changes, never on every tick.

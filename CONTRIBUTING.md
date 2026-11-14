# Contributing to RouteMind

RouteMind is an intelligent fleet tracking and dynamic delivery route optimization platform: Angular frontend, Java 21 Spring Boot backend, PostgreSQL with PostGIS, Redis GEO, self-hosted OSRM, and Google OR-Tools, all run locally with Docker Compose. No AWS, no cloud deployment, no paid APIs.

## How to contribute

1. Open or pick a GitHub issue first, so the work has a clear scope.
2. Create a feature branch from `main` and open a pull request to merge it back into `main`.
3. Keep commits feature-based. Never use day labels such as "Day 1" or "Day 2" in commits, files, or updates.
4. Write code to a senior-developer standard: production quality, no placeholders, no fake features, no default templates left in place.

## Verification before claiming something works

- Run the frontend production build and the frontend unit tests.
- Run the backend test suite where the environment allows it.
- Never claim a feature works until it has actually been verified. If the backend cannot boot in your environment, or a screenshot uses stub data, say so plainly in the pull request.
- Record real measured numbers for optimization results. Do not invent percentages or timings.

## Running locally

```bash
docker compose up --build
```

Open http://localhost:4200 and sign in with the seeded dispatcher account. See `README.md` for the full demo walkthrough, the sample dataset, and how to reset it with `docker compose down -v`.

## Secrets

Never commit secrets. Change the seeded dispatcher password and the JWT secret before any shared deployment; the backend refuses JWT secrets shorter than 32 characters.

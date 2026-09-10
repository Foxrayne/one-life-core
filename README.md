# One/Life Core

One/Life Core is a fork of [Project Zomboid Storm](https://github.com/guspuffygit/project-zomboid-storm), retaining Storm's internal package and API names for compatibility.

[![Maven Central](https://img.shields.io/maven-central/v/com.sentientsimulations/project-zomboid-storm)](https://central.sonatype.com/artifact/com.sentientsimulations/project-zomboid-storm)
[![License](https://img.shields.io/github/license/guspuffygit/project-zomboid-storm?logo=gnu)](https://www.gnu.org/licenses/)
[![Discord](https://img.shields.io/badge/Discord-One%2FLife-e60069?logo=discord&logoColor=white)](https://discord.gg/qccG89rvzT)

One/Life is a persistent Build 42 PvPvE community server at `172.240.18.253:16261`. It features a player economy, custom content, dangerous zones, factions, events and long-term progression. Despite the name, it is not a permadeath server.

Storm Mod Loader pairs server-side Java mods with Lua client mods to enable functionality beyond what Lua-only mods can do. Vanilla clients connect to a Java-modded server without the need to setup Java modding locally. Mods are distributed through the normal Steam Workshop.

Successor to the original abandoned [Storm](https://github.com/pzstorm/storm).

## Quickstart

See [Installation](docs/installation.md) for dedicated-server setup (Workshop or local-build) on Windows and Linux, and for the local-development workflow when iterating on Storm or a Storm-based mod.

## What Storm Does

Storm rewrites a chunk of the dedicated server's bytecode at load time to:

- **Patch vanilla bugs** that affect multiplayer (cross-player action cancels, zombie ID collisions, whisper case-sensitivity, …)
- **Lift hardcoded server limits** (configurable tick rate, parallel LOS pipeline, packet rate limit removed, raised zombie cull cap)
- **Surface event hooks to Java mods** (packet receipt, chat, ~190 Lua event bridges)
- **Extend the mod loader** with annotation-driven HTTP endpoints, server commands, and event handlers

See [What Storm Changes](docs/what-storm-changes.md) for the full list.

## Documentation

- [Installation](docs/installation.md) — dedicated server install (Workshop or local build, Windows + Linux) and local development workflow
- [What Storm Changes](docs/what-storm-changes.md) — performance, behavioral overrides, bug fixes, mod-loader extensions
- [Server Configuration](docs/server-configuration.md) — system properties and a production launcher example
- [HTTP API](docs/http-api.md) — runtime tuning endpoints and developer hot-reload (Lua / Java)
- [Prometheus Metrics](docs/metrics.md) — exposing metrics and adding new ones from mods
- [Chunk Streaming Observability](docs/chunk-streaming-observability.md) — attributing a chunk-stream stall to one of five causes, and proving a fix worked
- [Mod Author Guide](docs/mod-author-guide.md) — `ZomboidMod` entry point, annotation surfaces, Lua API, server commands

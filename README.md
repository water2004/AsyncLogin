# AsyncLogin

English | [简体中文](README_zh.md)

[![CI and Release](https://github.com/water2004/AsyncLogin/actions/workflows/ci-release.yml/badge.svg)](https://github.com/water2004/AsyncLogin/actions/workflows/ci-release.yml)

AsyncLogin is a server-side Fabric mod that moves player DAT, statistics,
advancements, and `usercache.json` file IO off Minecraft's server tick thread.
It prevents slow storage, JSON parsing, and data migration from stalling ticks
during player login and logout.

## Supported versions

| Minecraft | Fabric Loader | Java | Mod version |
| --- | --- | --- | --- |
| 26.1 | 0.17.3+ | 25+ | `1.0.0+mc26.1` |
| 26.2 | 0.17.3+ | 25+ | `1.0.0+mc26.2` |

Minecraft 26.1 and 26.2 have independent Mixins and adapter layers. Install
only the JAR matching the server version. Fabric API is not required.

## How it works

All player-related file operations use one unbounded, single-worker FIFO queue
named `AsyncLogin IO Thread`:

1. The first player DAT read, decompression, parsing, and DataFix while the
   spawn location is prepared.
2. After duplicate-login, ban, whitelist, and capacity checks pass, statistics,
   advancements, and the second player DAT are loaded in vanilla order. Legacy
   name-based statistics migration, JSON parsing, and both DataFix operations
   also run on the worker.
3. Every vanilla-requested `usercache.json` save.
4. Temporary-file creation, compressed writing, and safe replacement for
   player DAT saves.
5. Directory creation and file writes for player statistics and advancements.

The server thread only submits transactions and polls completed futures during
later ticks. It never calls `Future.join()`, `Future.get()`, a blocking queue
`put()`, `CallerRuns`, or a synchronous fallback. Because reads and writes use
the same FIFO, a reconnecting player's new read is ordered after saves already
submitted by their disconnect.

Player-to-NBT snapshotting remains on the server thread to avoid racing a live
`Player`. Statistics and advancement save snapshots are captured there for the
same reason, after which immutable JSON trees are handed to the IO queue.
`usercache.json` is also serialized at the original call site, so cache updates
that occur while a task is queued cannot alter an earlier save transaction.

Mojang already loads spawn-area entity files on its own storage executor, but
vanilla calls `waitForEntities` before spawning the player and blocks the server
thread until the result is ready. AsyncLogin retains Mojang's executor and
polls completed loads for the 7×7 spawn area once per tick. An unready entity
area delays only that login instead of blocking the tick; world entity IO is
not moved into the player-file FIFO.

## Vanilla compatibility

AsyncLogin preserves vanilla's state transitions, operation order, and file
formats:

- The two DAT reads remain separate transactions and are neither reused nor
  merged.
- Corrupt-file backup, `.dat_old` fallback, and player DataFix behavior remain
  unchanged.
- Legacy statistics migration, statistics/advancement DataFix, advancement
  application order, and error handling are preserved.
- NBT, statistics, advancements, and usercache JSON formats are unchanged.
- Every vanilla save request creates its own call-time snapshot and FIFO task;
  writes are not coalesced.
- Read failures return to the corresponding vanilla configuration-error or
  invalid-player-data path on the server thread.
- A pending UUID reservation preserves duplicate-login behavior during the
  asynchronous wait.
- Normal shutdown stops accepting new transactions and drains the queue before
  Minecraft storage is closed.

As with any asynchronous writer, filesystem visibility is delayed until the
queued transaction completes, and an abrupt process crash can lose work that
has not yet drained. Apart from those unavoidable timing differences, the goal
is to change only where file work executes.

## Installation

1. Install Fabric Loader for the corresponding Minecraft version.
2. Download the matching AsyncLogin JAR.
3. Put the JAR in the server's `mods` directory and start the server.

AsyncLogin is server-side only; clients do not need to install it.

## Building

Java 25 is required. The Gradle toolchain can provision the required JDK.

```powershell
.\gradlew.bat clean build
```

Artifacts are written to:

- `minecraft-26.1/build/libs/AsyncLogin-1.0.0+mc26.1.jar`
- `minecraft-26.2/build/libs/AsyncLogin-1.0.0+mc26.2.jar`

## CI and releases

Pushes to `main`, pull requests targeting `main`, and manual workflow runs
perform a complete Java 25 build and run the core tests. The two non-sources
JARs are retained as GitHub Actions artifacts for 14 days.

To publish a release, create the matching version tag on the intended `main`
commit and push it:

```powershell
git tag "v1.0.0+mc26.1"
git push origin "v1.0.0+mc26.1"
```

or:

```powershell
git tag "v1.0.0+mc26.2"
git push origin "v1.0.0+mc26.2"
```

CI verifies the tag format, confirms that the tagged commit belongs to `main`,
and checks that the version matches the corresponding mod metadata. It then
creates a GitHub Release containing only the JAR for the Minecraft version in
the tag. Release notes are generated from [`RELEASE.md`](RELEASE.md).

## Project structure

```text
AsyncLogin/
├─ core/             # Minecraft-independent single-thread IO queue
├─ minecraft-26.1/   # Independent Minecraft 26.1 adapter and Mixins
└─ minecraft-26.2/   # Independent Minecraft 26.2 adapter and Mixins
```

## License

[MIT](LICENSE.txt)

# x7t Name Checker - Summary

A Minecraft Fabric mod for checking player name history and availability.

## Features

### Core Features
- **Name History Lookup** - View complete name history with timestamps for any player
- **Availability Check** - Check if a Minecraft name is available to claim
- **UUID Display** - Shows player UUIDs with click-to-copy functionality

### Watchlist System
- Watch names and get notified when they become available
- Background auto-check every 60 seconds
- Sound notifications for available names

### Bedrock Support
- Look up Xbox/Bedrock players via Gamertag
- Shows XUID and Floodgate UUID
- Name history tracking for Bedrock players

### Offline Mode & Caching
- Local caching of all player lookups
- Cache persists between game sessions
- Works offline with cached data

## Commands

| Command | Description |
|---------|-------------|
| `/namecheck <name>` | View player name history |
| `/namecheck available <name>` | Check if name is available |
| `/namecheck bedrock <gamertag>` | Look up Bedrock player |
| `/namecheck watch <name>` | Add name to watchlist |
| `/namecheck unwatch <name>` | Remove from watchlist |
| `/namecheck watchlist` | View all watched names |
| `/namecheck watchlist clear` | Clear entire watchlist |
| `/namecheck cache` | View cache info |
| `/namecheck cache clear` | Clear cache |

## Version History

| Version | Date | Highlights |
|---------|------|------------|
| 1.0.4 | 2026-01-19 | Current version |
| 1.0.3 | 2026-01-17 | Watchlist, Caching, Bedrock history |
| 1.0.2 | 2026-01-16 | Bedrock support |
| 1.0.1 | 2026-01-15 | Availability check, clickable text |
| 1.0.0 | 2026-01-14 | Initial release |

## Compatibility

- Minecraft 1.21.4 - 1.21.11
- Fabric Loader 0.16.9+

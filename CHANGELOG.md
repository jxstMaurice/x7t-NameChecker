# Changelog

All notable changes to x7t Name Checker will be documented in this file.

## [1.0.3] - 2026-01-17

### Added
- **Watchlist System** - Watch names and get notified when they become available
  - `/namecheck watch <name>` - Add name to watchlist
  - `/namecheck unwatch <name>` - Remove from watchlist
  - `/namecheck watchlist` - View all watched names
  - `/namecheck watchlist clear` - Clear entire watchlist
  - Sound notification when a watched name becomes available
  - Auto-checks every 60 seconds in background
  
- **Offline Mode & Caching**
  - All player lookups are now cached locally
  - Cache persists between game sessions
  - Shows cached data when API is unavailable
  - `/namecheck cache` - View cache info
  - `/namecheck cache clear` - Clear cache
  
- **Bedrock Name History** - Now shows name history for Bedrock players (if tracked)

- **Extended Version Support** - Now supports Minecraft 1.21.4 - 1.21.11

### Improved
- Better error handling for Bedrock API (no more "HTTP 503" errors)
- Tab-completion for Bedrock players (recognizes `.` and `*` prefixes)
- Proper Floodgate UUID calculation

---

## [1.0.2] - 2026-01-16

### Added
- **Bedrock Support**
  - `/namecheck bedrock <gamertag>` - Look up Xbox/Bedrock players
  - Shows Gamertag, XUID, and Floodgate UUID
  - Tab-completion for Bedrock players on server

### Improved
- Updated help menu with all commands
- Added GitHub link to info display

---

## [1.0.1] - 2026-01-15

### Added
- `/namecheck available <name>` - Check if a name is available to claim
- Clickable text to copy names and UUIDs
- Hover tooltips with additional information

### Fixed
- Cooldown system now works per-player

---

## [1.0.0] - 2026-01-14

### Initial Release
- `/namecheck <name>` - View player name history
- Name history with timestamps
- UUID display
- Tab-completion for online players
- Rate limiting (3 second cooldown)
- Links to Modrinth and Discord

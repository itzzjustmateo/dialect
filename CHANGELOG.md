# Changelog

## [0.2.0-ALPHA] - 2026-06-28

### Added
- VaultUnlocked integration hook (Vault2 API via reflection)
- FastStats metrics and error tracking
- Language selection GUI (`/language`) — WIP, disabled by default
- `language_selection` config toggle for per-player language choice
- Release workflow: Modrinth project description auto-update from README.md
- Optimized server configs (`server.properties`, `spigot.yml`, Paper YAMLs, `start.sh`)
- shieldcn.dev badges replacing shields.io

### Changed
- Messages redesigned with modern color palette (`#478FC6`, `#C2C7D3`, `#D1988C`, `#CE5F4E`)
- Config comments cleaned up for consistency
- README polished with Dependencies section and Installation guide
- CHANGELOG squashed to single block per release
- GitHub labels reorganized (added `ai`, `translation`, `integration`, etc.)
- Version bumped to `0.2.0-ALPHA`

### Fixed
- Command registration for Paper plugins — switched to `Bukkit.getCommandMap()` with reflection
- Caffeine SSMSA runtime error — excluded from Shadow `minimize()`
- Style violations: missing braces and column limit overflow across 17 files
- `.gitignore` updated, IDE artifacts cleaned from tracking
- All Discord webhooks removed from repository

## [0.1.0-ALPHA] - 2026-06-28

### Added
- Initial release of LazyDialect
- Multi-provider AI support: OpenRouter, OpenAI, Anthropic, Gemini, HuggingFace
- DeepL fallback translation
- Language detection, translation, moderation, and slang validation
- Redis/Dragonfly distributed caching
- PaperMC auto-updater (`/lazydialect utils papermc update`)
- Sound effects, particle effects, and actionbar messages
- Chat formatting with PlaceholderAPI, LuckPerms, LPC, LPCX, Vault, WorldGuard, and Geyser
- Auto-update checker (Modrinth + GitHub fallback)
- Fully configurable via `config.yml` and `messages.yml`
- VirusTotal scanning on every build
- GitHub Actions CI, CodeRabbit PR review, and Dependabot

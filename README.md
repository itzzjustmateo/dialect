# Dialect

AI-powered language enforcement, detection, translation, and moderation layer for Minecraft Paper servers.

## Features

- **Language Detection** — Automatically detects the language of chat messages using AI
- **Translation** — Translates non-default language messages with AI or DeepL fallback
- **Moderation** — Enforce language rules via whitelist/blacklist with configurable actions
- **Multi-Provider AI** — Supports OpenRouter, OpenAI, Anthropic, Gemini, and HuggingFace
- **Slang Validation** — Detects and validates slang usage in context
- **Redis/Dragonfly** — Distributed caching for multi-server networks
- **Chat Formatting** — Integrates with PlaceholderAPI, LuckPerms, LPC, LPCX
- **Actionbar & Effects** — Visual and audio feedback for players
- **PaperMC Updater** — `/lazydialect utils papermc update` downloads latest Paper build

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/lazydialect help` | `lazydialect.admin` | Show command help |
| `/lazydialect reload` | `lazydialect.admin.reload` | Reload configuration |
| `/lazydialect status` | `lazydialect.admin.status` | View plugin status |
| `/lazydialect detect <text>` | `lazydialect.admin.detect` | Detect language of text |
| `/lazydialect translate <lang> <text>` | `lazydialect.admin.translate` | Translate text |
| `/lazydialect cache clear` | `lazydialect.admin.cache` | Clear cached data |
| `/lazydialect utils papermc update` | `lazydialect.admin` | Download latest Paper build |
| `/language [code]` | `lazydialect.command.language` | Set preferred language |

## Configuration

Configure AI providers in `config.yml`:

```yaml
ai:
  provider: "openrouter"   # openrouter, openai, anthropic, gemini, huggingface
  api_key: ""              # Your API key
  model: ""                # Leave empty for provider default
```

### AI Provider Defaults

| Provider | Default Endpoint | Default Model |
|----------|-----------------|---------------|
| openrouter | `https://openrouter.ai/api/v1` | `meta-llama/llama-3-8b-instruct:free` |
| openai | `https://api.openai.com/v1` | `gpt-4o-mini` |
| anthropic | `https://api.anthropic.com/v1` | `claude-3-haiku-20240307` |
| gemini | `https://generativelanguage.googleapis.com/v1beta` | `gemini-2.0-flash` |
| huggingface | `https://api-inference.huggingface.co` | `mistralai/Mistral-7B-Instruct` |

## Requirements

- Java 21+
- Paper 1.21.11+
- An API key for at least one AI provider or DeepL

## Building

```bash
./gradlew build              # Clean build
./gradlew deployPlugin       # Build + copy to server plugins
```

## Publishing to Modrinth

Releases are automatically published to Modrinth when a tag is pushed. To enable this:

1. Create a project on [Modrinth](https://modrinth.com) (if you haven't already)
2. Generate a Modrinth API token:
   - Go to https://modrinth.com/settings/pats
   - Click **New Token**
   - Give it a name (e.g., `dialect-release`)
   - Select the **Upload Versions** permission
   - Scope it to your project
   - Copy the generated token
3. Set the token and project ID in your GitHub repository:
   ```bash
   gh secret set MODRINTH_TOKEN           # Paste the token
   gh variable set MODRINTH_ID             # Your Modrinth project ID
   ```

The Modrinth version body is automatically populated from this README.

## License

MIT

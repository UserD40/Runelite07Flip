# Agent List Design — 07Flip RuneLite Plugin

**Date:** 2026-03-18
**Status:** Approved

---

## Overview

Five custom Claude Code agents stored in `.claude/agents/` for the 07Flip RuneLite Plugin Hub project. Each agent has a precise `description` field so the orchestrator auto-routes to it based on user intent — no slash commands required.

---

## Agents

### 1. `deploy.md`

**Purpose:** Fully autonomous end-to-end deployment. Pushes to the plugin repo and opens a Plugin Hub PR with no confirmation prompts.

**Auto-routing triggers:** "deploy", "push my changes", "ship it", "open a PR", "submit to plugin hub", "release"

**Behaviour — exact sequence:**
1. `git add` staged files → `git commit` → `git push` to `UserD40/Runelite07Flip` using `GITHUB_TOKEN` from `.env`
2. Capture full 40-character commit hash via `git rev-parse HEAD`
3. Fetch current upstream `runelite/plugin-hub` master SHA via GitHub API
4. Create a fresh timestamped branch (`update-07flip-<epoch>`) on `UserD40/plugin-hub` from that upstream SHA
5. Update `plugins/07flip` — always preserving all three lines: `repository=`, `commit=`, `warning=`
6. Open PR against `runelite/plugin-hub:master`
7. Poll mergeability — report `Mergeable: True | State: blocked` (normal) or surface conflict with explicit fix instructions

**Tools needed:** Bash, Read

---

### 2. `build.md`

**Purpose:** Runs the Gradle build, diagnoses failures, edits source to fix them, and re-verifies.

**Auto-routing triggers:** "build", "compile", "does it build", "gradle error", "won't compile", "run the build", "check if it compiles"

**Behaviour:**
1. Run `./gradlew build`
2. On failure: parse compiler output, locate offending file(s) and line(s), apply fix via Edit tool
3. Re-run build to verify — repeat up to 3 iterations
4. After 3 failed fix attempts: stop, surface the remaining error to the user with a clear explanation rather than continuing to guess

**Tools needed:** Bash, Read, Edit

---

### 3. `runelite-api.md`

**Purpose:** Diagnoses RuneLite API misuse, finds correct modern replacements, and proactively scans modified files before build or deploy.

**Auto-routing triggers:** "why isn't this working", "deprecated API", "wrong widget", "component ID", "scan my code for API issues", "check for RuneLite issues", "broken event"

**Behaviour:**
- **Diagnosis mode:** Given a broken snippet or error, identifies the API misuse and provides the correct RuneLite equivalent with explanation (e.g. `WidgetInfo.CHATBOX_FULL_INPUT` → `ComponentID`, correct `ClientThread` vs `SwingUtilities.invokeLater` usage, proper event type)
- **Scan mode:** Reads recently modified or staged `.java` files and flags RuneLite API misuse proactively
- Carries project-specific knowledge of deprecations already encountered (e.g. `WidgetInfo` terminal deprecation in favour of `ComponentID`)

**Tools needed:** Bash, Read, Grep, Glob

---

### 4. `feature-scaffold.md`

**Purpose:** Generates new feature boilerplate matching project patterns, and reviews finished implementations for conformance.

**Auto-routing triggers:** "add a new tab", "new feature", "scaffold", "create a panel for", "does this follow patterns", "review my implementation", "check my new code"

**Behaviour:**
- **Scaffold mode:** Given a feature name and data shape, generates:
  - Model class in `src/main/java/com/o7flip/model/`
  - UI panel class in `src/main/java/com/o7flip/ui/`
  - API client method in `O7FlipApiClient.java`
  - Tab wiring in `O7FlipPanel.java`
  - All output uses: Allman braces, tabs, `ColorScheme`, `FontManager`, `SwingUtilities.invokeLater`, BSD 2-Clause header
- **Review mode:** Reads a completed implementation and checks it conforms to all project patterns, reporting deviations with line references

**Tools needed:** Read, Edit, Write, Glob, Grep

---

### 5. `plugin-hub-compliance.md`

**Purpose:** Checks code against the exact Plugin Hub rules that cause PR rejection.

**Auto-routing triggers:** "ready to submit", "will this pass review", "compliance check", "checkstyle", "is this ok to PR", "plugin hub rules", "will it be rejected"

**Behaviour — checks performed:**
- Allman-style braces (opening brace on its own line, not end of line)
- Tabs, not spaces
- BSD 2-Clause copyright header on every new `.java` file
- No wildcard imports
- No use of `reflection`, `JNI`, `Runtime.exec()`, `ProcessBuilder`
- No new dependencies added to `build.gradle` (OkHttpClient and Gson are transitive — must not be re-declared)
- No player data sent to external servers
- `LinkBrowser.browse()` used instead of `Desktop.getDesktop().browse()`

Reports pass/fail per file with line-level findings.

**Tools needed:** Read, Grep, Glob

---

## File Layout

```
.claude/
└── agents/
    ├── deploy.md
    ├── build.md
    ├── runelite-api.md
    ├── feature-scaffold.md
    └── plugin-hub-compliance.md
```

---

## Auto-Routing Mechanism

Each agent's frontmatter `description` field contains the routing signal. The orchestrator reads all agent descriptions at the start of each conversation and selects the appropriate agent when user intent matches. No explicit `/agent-name` invocation needed.

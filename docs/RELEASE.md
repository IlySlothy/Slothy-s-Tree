# Release checklist (Slothy's Tree)

Run this after every version bump. **Always post to Discord last** (only `@mod updates` role — never `@here`).

## One command (recommended)

```powershell
cd "C:\Users\newbr\OneDrive\Desktop\New folder (5)"
powershell -File tools\publish-release.ps1 -Version 1.0.6
```

Skips steps that already succeeded with `-SkipBuild`, `-SkipGitHub`, `-SkipModrinth`, `-SkipDiscord`.

## Manual steps

1. Bump `mod_version` in `gradle.properties` (+ subproject `build.gradle` versions).
2. Update `src/main/resources/assets/slothyhub/changelog.json` and `release-notes-vX.Y.Z.md`.
3. `.\gradlew.bat copyReleaseAssets`
4. Commit + push `main`, then push tags: `vX.Y.Z-mc1.21.8`, `vX.Y.Z-mc1.21.11`, `vX.Y.Z-mc1.20-1.21.1`
5. `powershell -File tools\create-github-releases.ps1 -BaseVer X.Y.Z -NotesFile release-notes-vX.Y.Z.md` (needs `gh auth login`)
6. `.\gradlew.bat publishModrinth` (optional; needs Modrinth token)
7. **`powershell -File tools\post-release-discord.ps1 -Version X.Y.Z-mc1.21.8 -NotesFile release-notes-vX.Y.Z.md`** — required

Discord config: `.gradle/secrets/discord.properties` (`channelId`, `botToken`, `modUpdatesRoleId`).

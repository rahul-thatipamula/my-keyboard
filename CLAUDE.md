# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

Offline Android keyboard (IME) in Kotlin. See [README.md](README.md) for architecture, build and run instructions.

- Package: `com.caxone.my_keyboard`
- Build: `./gradlew assembleDebug` · Install: `./gradlew installDebug` · Tests: `./gradlew test`
- Never commit `local.properties`, `build/`, or `.gradle/` (already gitignored).
- The manifest requests only `VIBRATE`. Do not add `INTERNET` or any other permission without asking.

## Workflow: every change goes through an issue

Every piece of work — feature, bug fix, refactor, docs — follows this exact flow. Do not commit directly to `main`.

### 1. Create a GitHub issue

```bash
gh issue create --title "<short imperative title>" --body "<what and why>"
```

Note the issue number `N` from the output.

### 2. Create a branch for the issue

Branch from an up-to-date `main`. Name it `<type>/<N>-<kebab-slug>` where type is `feat`, `fix`, `refactor`, `docs`, or `chore`.

```bash
git checkout main && git pull
git checkout -b fix/N-short-description
```

### 3. Solve the issue

- Make the change. Keep it scoped to the issue — unrelated cleanups get their own issue.
- Run `./gradlew test` (and `./gradlew assembleDebug` if you touched anything under `app/src/main`) and make sure both pass before committing.

### 4. Commit and push

Commit message: imperative summary line, blank line, short body, and a `Closes #N` line so the issue auto-closes on merge.

```bash
git add -A
git commit -m "Fix autocorrect firing on capitalised names

Skip correction when the typed word is capitalised mid-sentence.

Closes #N"
git push -u origin fix/N-short-description
```

### 4a. Group commits by feature

When a branch delivers several distinct features, make one commit per feature (in dependency order) rather than one big commit, so `git log` reads as a changelog. Each commit should build on its own. Reference the issue with `Part of #N` in each, and `Closes #N` in the last one.

### 5. Merge into main

Open a PR and merge it.

- **Single-feature branch** → squash, so `main` gets one commit per issue.
- **Multi-feature branch** (grouped commits from 4a) → merge commit, so the grouped history is preserved on `main`.

```bash
gh pr create --fill --base main
gh pr merge --squash --delete-branch    # single feature
gh pr merge --merge  --delete-branch    # grouped features
```

`--delete-branch` removes the remote branch and switches you back to `main`.

### 6. Delete the issue branch (local and remote)

`gh pr merge --delete-branch` normally handles both. Verify, and clean up anything left over:

```bash
git checkout main && git pull
git branch -d fix/N-short-description                 # local
git push origin --delete fix/N-short-description 2>/dev/null || true   # remote, if still present
git fetch --prune
```

Confirm the issue closed: `gh issue view N`.

## Conventions

- Kotlin, 4-space indent, match the style of surrounding code.
- Bump `versionCode` / `versionName` in `app/build.gradle.kts` whenever a branch adds user-visible features.
- Prediction weights live as plain constants in `prediction/Predictor.kt`; tune there, don't scatter magic numbers.
- The personal language model (`prediction/UserModel.kt`) must stay on-device only.

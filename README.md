# Micro.blog Android Writer

Writing-first Android client for Micro.blog users who want local Markdown drafting, visible categories, inline image insertion, AI review, and direct publishing.

## MVP features in this repository

- Local-first Markdown drafts saved as `.md` files with YAML front matter
- Compose screen with categories always visible
- Insert `<!--more-->` from a prominent action
- Inline image Markdown insertion flow with alt text field
- Explicit AI review step (user-provided API key + configurable prompt template)
- Publish action wired to Micro.blog Micropub API (Bearer token)
- Drafts list with search + delete
- Settings for AI and theme behavior
- Light/dark support via Material 3 DayNight theme

## Local build

```bash
./gradlew assembleDebug
```

## Authentication and API integration

`AiReviewClient` is still modular and replaceable. `MicroblogApi` now uses Micropub with Bearer auth:

- `app/src/main/java/com/example/microblogwriter/ai/AiReviewClient.kt`

To publish/upload, set these in **Settings**:

- Micropub base URL (defaults to `https://micro.blog`)
- Access token (Bearer)
- Optional media endpoint override (if omitted, app calls `q=config` to discover it)

## GitHub Actions APK build

On every push to `main`, CI builds APK artifacts and uploads them.

If release signing secrets are absent, workflow still uploads debug APK and documents this fallback in logs.

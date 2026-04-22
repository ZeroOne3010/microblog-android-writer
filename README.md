# Micro.blog Android Writer

Writing-first Android client for Micro.blog users who want local Markdown drafting, visible categories, inline image insertion, AI review, and direct publishing.

## MVP features in this repository

- Local-first Markdown drafts saved as `.md` files with YAML front matter
- Compose screen with categories always visible
- Insert `<!--more-->` from a prominent action
- Inline image Markdown insertion flow with alt text field
- Explicit AI review step (user-provided API key + configurable prompt template)
- Publish action scaffolded for Micro.blog API integration
- Drafts list with search + delete
- Settings for AI and theme behavior
- Light/dark support via Material 3 DayNight theme

## Local build

```bash
./gradlew assembleDebug
```

## Authentication and API integration

`MicroblogApi` and `AiReviewClient` are modular placeholders in this MVP scaffold. Replace them with production providers:

- `app/src/main/java/com/example/microblogwriter/network/MicroblogApi.kt`
- `app/src/main/java/com/example/microblogwriter/ai/AiReviewClient.kt`

## GitHub Actions APK build

On every push to `main`, CI builds APK artifacts and uploads them.

If release signing secrets are absent, workflow still uploads debug APK and documents this fallback in logs.

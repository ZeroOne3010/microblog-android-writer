# Yet Another Blog Writer (Android)

Yet Another Blog Writer is an Android app for Micro.blog writers who prefer to draft on their phone without giving up a clean writing flow.

The app is built around a simple idea: write in Markdown first, keep drafts locally, and publish when your post is ready. You can add categories as you write, insert images inline, and drop in a `<!--more-->` break without digging through menus. If you like an extra review pass before publishing, the app can run an optional AI review step using your own API key and prompt.

Publishing is done through Micro.blog's Micropub support. Configure your Micropub base URL and access token in Settings, and the app handles publishing from there.

## What the app feels like to use

- A focused compose experience designed for long-form Markdown writing
- Local draft storage so your writing starts on-device
- Quick controls for common blog formatting actions
- Built-in publishing flow for Micro.blog
- Optional AI-assisted review before you hit publish

## Build locally

```bash
./gradlew assembleDebug
```

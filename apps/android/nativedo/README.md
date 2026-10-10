# NativeDo Android module

This is the active linux.do app. It builds against the full Telegram subtree at
`../telegram`; its Gradle project is included by that tree's settings.

- [Setup, build and checks](../../../README.md)
- [Design index](../../../docs/README.md)
- [Discourse contracts](../../../contracts/discourse/README.md)

Production sources live in `src/main`; debug-only probes and topic jump/render
entries in `src/debug`; the fake-site UI harness in `src/test`. This README is an
entry point, not a feature-status table or a history of device passes.

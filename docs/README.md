# Design knowledge

Small, maintained answers to the questions a contributor needs to understand.
These pages describe the **active `:nativedo` build**, unless explicitly marked legacy.
They are not a feature checklist, task backlog or verification diary.

## Read by question

- **Where does a change belong?** [Code layout](code-layout.md)
- **How can Telegram UI run without a Telegram account?** [Upstream hosting](upstream-hosting.md)
- **Why do reading and editing use different representations?** [Content and composer](content-composer.md)
- **Which state belongs to the reader, server or viewport?** [Reading and state](reading-state.md)
- **What does a test actually prove?** [Testing](testing.md)
- **What does linux.do accept on the wire?** [Discourse contracts](../contracts/discourse/README.md)
- **How is the old partial mirror maintained?** [Legacy vendoring](../VENDOR.md)

Build commands live in [README](../README.md). Contributor rules live in
[AGENTS](../AGENTS.md). Tests and fixtures are executable examples; source links
in each theme lead to the implementation, rather than a duplicated file inventory.

## How this stays small

Use the LLM-wiki idea as a **maintenance workflow**, not a new storage system:
small design pages are rewritten, linked, merged or deleted as the app changes.
The [maintenance rules](../AGENTS.md#maintaining-documentation) own the design-to-
implementation workflow; [testing](testing.md#where-evidence-goes) owns run evidence.

This approach borrows the synthesis/linking idea from
[Karpathy's LLM wiki](https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f)
and the public wiki-maintenance conventions in
[Ryan Dahl's notes](https://gist.github.com/ry/c56dfa7b1b90eeff2d8d0127e45ae3bb/66be421686c257f25d3bb7f806867c07b4769dea).
Celld's design wiki is private; these references do not establish its internal
document structure. No parallel wiki, raw archive or experiment diary is required.

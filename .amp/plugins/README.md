# Jev Router plugin

`.amp/plugins/jev-router/index.ts` adds these things to Amp — a **directory plugin**
(the official shape for a plugin with supporting files; it is also what makes bundling
a skill possible later, which a single-file plugin cannot do):

- the **Jev Router: Send** command (the manual test entry) in the command palette,
- the **`jev_dispatch_task`** tool, which a thread calls when it wants to hand a
  self-contained task to another model,
- the **`jev_orchestrate_task`** tool, which runs a task through the serial
  **inspect → plan → execute → review** pipeline,
- the **`jev_parallel_inspect`** tool, which fans out independent read-only
  evidence questions to sibling child threads on the fast model,
- a conservative, advisory `agent.start` triage hook,
- a `tool.call` permission gate that uses deterministic holds plus a targeted
  Jev safety classification for ordinary shell commands,
- a fail-open `tool.result` compressor that deterministically shrinks oversized
  tool output and asks Jev at most one structured relevance question,
- a bounded `agent.end` auto-review hook that starts one read-only review (or
  returns one bounded `continue`) after a successful, code-changing turn,
- four cost/quality **profiles** that map the pipeline stages to verified models
  without ever lowering the sensitive/destructive safety floor,
- three model-pinned modes, **Jev Flash / Jev Sol / Jev Astra**, registered with
  `amp.registerAgentMode` (see [Verifying that routing took effect](#verifying-that-routing-took-effect)).

Jev (TypeSafe's `jev-latest`) only classifies: it answers one each of the Choice,
Score, and Noul question types and never writes the task response. Every place
this plugin calls Jev follows the same rules — one narrow question (or a small
set of independent ones) per call, a confidence floor before acting, and code
owns the decision and any text. Turning a classification into a model choice, a
character budget, or a yes/no gate is local and deterministic. The plugin never
changes the current thread's model (the Amp Plugin API has no `setModel`). It
pins the subagents that Amp spawns from a thread, and uses child threads only for
explicitly separated pipeline stages, dispatched work, parallel reads, or the
read-only auto review.

## Related references

These external projects and documents informed the design and dogfooding plan
for this plugin:

- [Astra Flash Orchestrator](https://github.com/ethanplusai/astra-flash-orchestrator) — reference for routing work between fast and deep-reasoning agents.
- [Astra-Ares](https://github.com/miuuyy/Astra-Ares) — Jev-driven reasoning-effort selection in a patched Codex runtime.
- [Ares: Adaptive Reasoning Effort Selection for Efficient LLM Agents](https://arxiv.org/html/2603.07915v1) — paper on selecting the minimum sufficient reasoning effort per agent step.
- [TypeSafe skills](https://github.com/typesafe-ai/skills) — skill guidance used while designing the Jev integration.
- [TypeSafe use-case map](https://docs.typesafe.ai/concepts/use-case-map) — examples of structured judgments, routing, reranking, and fan-out.
- [TypeSafe confidence guidance](https://docs.typesafe.ai/confidence) — confidence thresholds and risk-dependent escalation.
- [TypeSafe speculative fan-out](https://docs.typesafe.ai/patterns/fan-out) — basis for `jev_parallel_inspect`.
- [TypeSafe reranking cookbook](https://docs.typesafe.ai/cookbooks/rerank_typesafe) — basis for relevance judgments over bounded evidence.
- [jevify](https://github.com/ryana/jevify) — related Jev integration reference.
- [Thoughts on a TypeSafe coding agent](https://docs.google.com/document/u/0/d/1G61Uub0FifUnmmrPzFQojZ3KpczYKmXGpgEXDJ2l_Zg/mobilebasic?pli=1) — concepts behind tool routing, context selection, subagents, and security-aware routing.

The Amp Plugin API remains the execution boundary. These references do not
imply that the plugin can switch the model of the current thread, intercept
every generation step, or control provider KV-cache reuse.

## Verified models

| Tier | Model | Used for |
| --- | --- | --- |
| deep reasoning | `openai/gpt-6-astra` | planning architecture/cross-cutting/long-context/high-complexity work, destructive tasks |
| coding | `openai/gpt-5.6-sol` | focused planning, sensitive/destructive execution, review |
| fast | `deepseek/deepseek-v4.1-flash` | inspection, and execution of approved low-risk plans |

All three are listed by `amp plugins show-agent-options`; keep the table in sync
with that command before adding a model to `VERIFIED_MODELS`. Each is also
registered as a first-class, model-pinned mode (`jev-flash`, `jev-sol`,
`jev-astra`) built from `amp.createAgent({ model })`.

### How the model is pinned (and what that can prove)

Every dispatched agent, every pipeline stage, and every registered mode is
created with an explicit `createAgent({ model })` call. The plugin never uses
`getBuiltinAgent`, never sets `extends`, and never lets an agent inherit a
built-in mode's model; the agent `name` and `display.label` include the model
tag, so a thread header or log line says which model was *requested* (they are
labels, never the `model` field).

The Amp Plugin API exposes the resolved agent definition on the handle it
returns (`agent.definition`, i.e. `CustomAgentDefinition.model`). The plugin
reads it back and reports it next to the requested model, so a result shows both:

- `requested model` — the pin the plugin passed to `createAgent({ model })`,
- `agent definition <id>` — the model Amp resolved into the agent, when exposed,

and flags `⚠ MISMATCH` when the two differ. `modeFor` / `decision.mode` is **not**
model evidence: it is an advisory built-in-mode hint that the plugin never passes
to `createAgent`, and results label it as such (`advisory built-in mode: … (not
the model)`).

## The four-stage pipeline (`jev_orchestrate_task`)

Inputs: `task` (required, self-contained), `instructions` (optional, appended to
the inspect and plan stages), and `confirm` (required for destructive tasks).

The stages run **strictly serially** and share the current local workspace. Each
stage is a separate `amp.createAgent({ name, model, instructions, tools, display })`
+ `agent.run(...)`; the previous stage is finished before the next one starts, so
only one agent is ever editing. Every stage passes an explicit `model` — no
`getBuiltinAgent`, no `extends`, no inherited mode model — and names both the
stage and the model tag in its `name`/`display` (for example
`Jev Plan · Astra`).

| Stage | Model | Tools | Job |
| --- | --- | --- | --- |
| inspect | Flash | `Read`, `finder`, `shell_command`, `web_search`, `read_web_page`, `read_thread` | Read/search the workspace, run read-only commands, and return a compact evidence package: relevant files, key symbols, constraints, unknowns. |
| plan | Sol or Astra (Jev-decided) | same read-only set | Turn the task + inspection report into an ordered, file-level plan. Never edits. |
| execute | Flash (Sol for sensitive/destructive) | `all` | Follow the approved plan, edit files, run the plan's verification, and report changes + verification. |
| review | Sol, or Astra when the planner used Sol | same read-only set | Judge the actual diff against the task contract and the executor's report, re-running read-only checks where useful, findings-first. Never edits, never loops. |

Every tool name in the table is a current built-in tool name from
`amp plugins show-agent-options`. An explicit array replaces Amp's default tool
list, so the read-only stages list each tool they need and deliberately omit the
edit tools (`create_file`, `edit_file`, `apply_patch`), `Task`, and the
`plugin__*` / `mcp__*` wildcards.

Read-only stages **do** get `shell_command`. A dogfood run showed why: the first
inspect stage had no shell tool and could only report "I could not run any of the
commands", while the evidence this repo needs is mostly command output
(`bun test`, `git diff`, `vendor-guard.sh`). The Plugin API cannot restrict a tool
to read-only, so the guarantee is structural where it can be (no editing tool, so
only `execute` can write a file) and instructional where it cannot be (each
read-only stage is told it may run commands for evidence but never one that
changes the workspace, and never to commit).

### Deterministic stage routing

- **inspect** is always Flash; it is bounded, read-only evidence gathering.
- **plan** is Astra when Jev says the route is planning, complexity ≥ 2, the task
  needs long context, or the task is destructive; otherwise Sol.
- **execute** is Flash for normal-risk work, Sol for sensitive or destructive
  work. Destructive work never runs on Flash.
- **review** is Sol by default, escalates to Astra when the planner already used
  Sol (so reviewer ≠ planner), and is always different from the executor.

### Risk and confirmation boundaries

Risk is read from the task text (`detectRisk`), not from Jev, so the guard holds
even when the classifier is unavailable. Risk only ever raises the tier.

- A task that looks **destructive** without `confirm: true` returns immediately
  with `status: blocked`; **no stage runs and no thread is created**.
- **Sensitive** and confirmed **destructive** work is executed by Sol, not Flash.
- Inspect, plan, and review never receive a writing tool.

### What the result contains

The tool returns a compact report with:

- a **classification** line stating where the decision came from — `TypeSafe Jev
  classification`, `caller-supplied decision (Jev not consulted)`, `local
  fallback — Jev could not classify (<reason>)`, or `not classified`;
- a **Profile** line naming the cost/quality profile that chose the stage models;
- for every stage: the **requested model**, the **agent definition** model Amp
  resolved (or `agent model not exposed by the Plugin API`), a `⚠ MISMATCH`
  marker when the two differ, the **thread ID**, and that stage's trimmed report;
- the final review status (`pass` / `fail` / `unknown`). The verdict is read only
  from the reviewer's `## Verdict` line, so prose elsewhere in the report (a PASS
  whose justification says "nothing failed") cannot flip it. A missing verdict
  section, or an echoed `PASS | CHANGES_REQUIRED | UNCERTAIN` template, is
  reported as `unknown` rather than guessed.

The `stage` and `requestedModel` are also returned as structured fields on each
`StageReport`, so a caller can read them without parsing the text. The same
provenance is echoed by `jev_dispatch_task` (`requested model: …; Amp resolved
agent definition: …`). A Jev API failure is **never** reported as a Jev success:
the pipeline continues on `fallbackDecision` but the classification line says
`local fallback`. If a stage fails, the result names the failing stage and later
stages are not started (no retry loop). Nothing prints the API key or the raw
child transcripts; error details pass through `redactSecrets`.

### Token savings between stages

Stages never see the previous child's full transcript. Each handoff is the
original task plus the previous stage's text, capped (`clip`) at 24 k characters,
and the plugin caps the workspace diff it forwards to review at 12 k characters.
The tool result echoes each stage's report at 1.5 k characters. Everything a
later stage needs must live in the compact report, which is why the inspect
contract asks for file/symbol citations instead of pasted code.

## Oversized tool output (`tool.result`)

The `tool.result` hook runs after a tool finishes and may replace the output the
model sees. It must never break a turn, and it deliberately does **not** start an
agent (the hook is not the place for one). The policy is code, and Jev only
answers a structured relevance question:

1. **Short or non-string output is untouched** — nothing over `8 000` characters
   is even considered, and `status: 'error'` results are returned unchanged, so an
   error message is never truncated away.
2. **A pure plan classifies the output** (`diff`, `logs`, `search`, `file`,
   `generic`) and decides whether it is oversized. This is unit-tested without a
   network or an Amp runtime.
3. **A deterministic compressor owns the result.** It keeps, in priority order,
   every line that looks like an error/failure, then diff/search structure, then
   the head and tail (clipping a single oversized line instead of letting it
   bypass the budget). Nothing it emits is generated text; it only removes
   surrounding noise. A marker records how much was elided/kept.
4. **Jev is optional and bounded.** For an oversized output with
   `TYPESAFE_API_KEY` set and `JEV_ROUTER_TOOL_RESULT` on, Jev answers one Choice
   (`essential` / `mixed` / `noise`) plus one Score and nothing else. The answer
   can only move the character budget inside `[6 000, 24 000]`: `essential` (or a
   low-confidence answer) keeps more, `noise` keeps less. A low-confidence answer
   changes nothing.

Everything fails open. If the Jev call times out (4 s), errors, or returns an
invalid answer, the deterministic compressor still runs with its default budget;
if anything in the handler throws, the output passes through unmodified. The
whole thing is off with `JEV_ROUTER_TOOL_RESULT=0` (deterministic truncation
stays on).

**What it does not do.** It cannot summarise, and it cannot preserve the full
original anywhere — the result the model sees is the compressed text. It is a
token bound, not a lossless transform, so it keeps error lines and structure and
accepts that very large, near-uniform outputs lose their middle. It also cannot
know that a given "error-looking" line is unimportant; the safe direction is to
keep it.

## Automatic read-only review (`agent.end`)

`agent.end` fires when a turn finishes. The Plugin API's `agent.end` result is a
single `continue` (or nothing), so this hook is deliberately small and can never
turn the thread into a loop:

- It looks at the finished turn's messages and reviews only a turn that
  **succeeded** (`status: 'done'`) and **changed code** (an editor tool call).
  Child/subagent threads are skipped (recursion guard), and each turn is reviewed
  at most once.
- `JEV_ROUTER_AUTO_REVIEW` selects the mode:
  - **`1`/`thread` (default)** — start one **non-blocking read-only** review thread.
    It uses `createAgent(...).createThread({ show: false })`, which the API runs
    independently of the handler, so the handler waits only on that bounded API
    call and never on the review's work. The review thread is read-only (no
    editing tool) and has a parent, so it is never reviewed itself.
    **Its model comes from `autoReviewModel`, not from a hardcoded fast model:**
    the intent is cross-model review — a model that did not produce the turn. The
    Plugin API exposes the parent's resolved model only for custom agents
    (`thread.agent().definition.model`), never for built-in modes, so:
    - `JEV_ROUTER_AUTO_REVIEW_MODEL` (`flash`/`sol`/`astra`, or a full model id)
      wins whenever it names a verified model, including back to `flash`;
    - a parent model the API exposes escalates the reviewer off it, via the
      pipeline's own `reviewModel` rule (fast parent → `sol`, `sol` parent →
      `astra`, `astra` parent → `sol`), so the reviewer is never the model that
      produced the work;
    - otherwise (an ordinary user thread on a built-in mode) the reviewer is the
      senior coding model, `sol` — **never the fast model by default**.
  - **`continue`** — return one bounded `continue` (`maxContinuations: 1`) asking
    the current thread for a single read-only pass. This runs on the current
    thread's own model (the API cannot change it), so the review is visible in
    the thread but not cross-model; it is opt-in.
  - **`0`/`off`** — disabled.
- `JEV_ROUTER_AUTO_REVIEW_MAX` (default `1`) caps reviews per thread, and the
  handler books the review before starting it, so a duplicate event cannot review
  the same turn twice. Any error fails open.

If you prefer no automatic extra work, set `JEV_ROUTER_AUTO_REVIEW=0`; the plugin
then only reviews when you ask it to.

## Structured evidence and conditional guidance

The inspect stage is asked for a **JSON evidence package** with fixed fields
(`files`, `symbols`, `constraints`, `unknowns`, `checks`, `risks`). The handoff
parses, validates, and truncates it (max 24 items, 300 characters each) before the
plan stage sees it, so a later stage gets explicit fields instead of a wall of
prose. A malformed or missing package degrades to a clipped raw excerpt, never to
a silent empty handoff.

Inspect and plan also receive **only the relevant project guidance**: the plugin
lists the repository's `AGENTS.md`/`CLAUDE.md` files through the plugin shell
(ripgrep, then git, then find), ranks them against the task's path hints, keeps at
most 3, and summarises each to headings plus a couple of lines — dropping
secret-looking lines and capping the total at 1 200 characters. Nothing large is
injected. If the shell is unavailable the fallback is a **path hint**: the stage is
told which guidance files matter and reads them itself.

## Parallel read-only inspect (`jev_parallel_inspect`)

`jev_parallel_inspect` runs several **independent** read-only evidence questions
concurrently, each in its own child thread on the fast model with the same
read-only tool set as the pipeline's inspect stage. It is for questions that do
not depend on one another (per `docs.typesafe.ai`'s fan-out guidance); dependent
questions should start a second call once the earlier answer is known.

- concurrency is capped (`maxConcurrency`, default 3, max 5) and the question count
  is capped at 6; duplicate and empty questions are dropped;
- each question is isolated: one failure is reported with its error and never
  fails the others, and the summary marks each result `ok` or `FAILED`;
- the result is structured in code (`{ id, question, ok, threadID, report|error }`)
  and rendered as one block per question.

It only reads. Write/execute work stays serial, and this tool never runs two
writers against the shared workspace.

## Cost/quality profiles

A profile maps the four pipeline stages to **verified models only**. It is a
preference, never a permission: deterministic code enforces a safety floor
afterwards, so no profile can put sensitive or destructive execution on the fast
model, plan/review destructive work cheaply, or let the reviewer equal the
executor. The default is `balanced`, which is exactly the original risk-driven
behaviour.

| Profile | inspect | plan | execute | review |
| --- | --- | --- | --- | --- |
| `economy` | Flash | Sol (capped; risk raises it) | risk-driven | Sol (capped; risk raises it) |
| `balanced` (default) | Flash | Jev-decided Sol/Astra | Flash, Sol on risk | Sol, Astra when the planner used Sol |
| `quality` | Flash | Astra | Sol or above | Astra |
| `sensitive` | Flash | Astra | Sol or above | Astra |

Select it with the `profile` argument on `jev_orchestrate_task` or with
`JEV_ROUTER_PROFILE`. An unknown value falls back to `balanced`. The risk checks
run last, so even `economy` plans and reviews destructive work on Astra and runs
it on Sol.

## Subagent pinning (the routing that actually pays)

Every agent this plugin creates — the three registered modes, all four pipeline
stages, the review observer, and each parallel-inspect child — pins two roles, and
they are deliberately pinned differently:

- **`subagents` → the fast model** (`SUBAGENT_PIN`: Flash, effort `high`). That
  covers the subagents Amp spawns from those threads: `Task` workers, `finder`,
  thread reader, and `librarian`.
- **`oracle` → Astra** (`oraclePin()`), *not* the fast model. The oracle is a
  judgement role, not a retrieval one: retrieval answers with a compact result, so
  the cheap model belongs there, while a second opinion is only worth taking from a
  stronger model. Astra is also what Amp's own modes pin the oracle to. Only the
  model is pinned; the oracle keeps its own reasoning effort.
  `JEV_ROUTER_ORACLE_MODEL=flash|sol|astra` overrides it.

This is deliberately **not** how the plugin routes the thread itself. The Plugin API
exposes no way to change the model of a thread that is already running, and routing a
parent thread to a cheap model and back is a net loss: the senior model has to
re-process everything the cheap model read. A subagent is the opposite trade — it
answers with a compact result that lands in the parent's context as a tool result, so
the parent never re-loads what the subagent read. Reading and searching is the bulk of
an agent's tool turns, so that is where the cheap model belongs.

Amp falls back to its own routing when the pinned model is unavailable, so the pin is
an optimisation and never a correctness dependency. `JEV_ROUTER_SUBAGENT_PIN=0`
leaves Amp's routing alone for **both** roles.

## The `tool.call` gate

`tool.call` is a *request* event in the Plugin API, so a plugin can decide whether a
tool runs before it runs. The gate uses it as the permission query the routing cannot
be: at the moment the agent is about to act, Jev decides whether that action may run
without asking the user first.

What is checked, in order, and what each step costs:

1. **Readers are never gated.** `Read`, `finder`, `web_search`, `read_thread`,
   `librarian`, `oracle`, `view_media` and the rest are allowed with no latency and no
   Jev call.
2. **Mutating calls** (`shell_command`, `edit_file`, `create_file`, `apply_patch`) are
   described in one line — the command plus the paths `filesModifiedByToolCall`
   reports.
3. **Deterministic holds, free.** A command matching the destructive/sensitive
   patterns (`rm -rf`, `drop table`, `force-push`, …) or touching a sensitive path
   (`.env`, `.git/`, `*.pem`, `credentials.`, `secrets.`) is held immediately, with no
   API key and no Jev call. The path check reads the command text as well as the
   reported file list, because `cat .git/config` is the common way an agent reaches
   repository internals.
4. **Jev is asked only for an ordinary shell command.** File edits are skipped: they
   are already visible in the diff and a Jev request per edit would cost more than it
   saves. One request, 5 s budget, questions `SAFETY_QUESTIONS` (a `choice` for
   permission plus a `score` for risk).
5. **A hold needs a confident answer.** A non-safe permission below the 0.6 confidence
   floor is ignored — see the note below.

Order matters: the deterministic checks run first, so Jev can only ever *add* a hold,
never lift one. An infrastructure failure therefore **fails open** (the session keeps
working; the deterministic checks already ran) while an explicit risk signal fails
closed. Any plugin bug in the handler also fails open, because a gate must never break
a turn.

**Known judgement call.** Live Jev answers: `bun test …` → `safe`, risk 0.02,
confidence 0.97; `git push origin main` → `needs_confirmation`, risk 0.94, confidence
0.50. Because 0.50 is under the 0.6 floor, the gate **allows** a plain `git push`.
Holding on a 20%-confidence `needs_confirmation` (observed for an ordinary multi-line
`bun -e` script) nagged far too much to be usable, so the floor is one threshold with
one meaning — "trustworthy enough to act on" — shared with the routing path. If you
want pushes held, that is a one-line change to the floor, not a redesign.

Also note the path check is a text heuristic, so a command that merely *mentions*
`.env` or `.git/` in a string literal is held. That is the safe direction, and the hold
message names the token so the user can confirm in one step.

## The `jev_dispatch_task` tool (unchanged compatibility entry)

Inputs: `task` (required), `mode` (`run`, the default, waits for the routed agent
and returns its answer; `thread` starts a visible child thread and returns its
ID), `confirm`, and `instructions`.

The tool keeps the main thread informed instead of failing silently:

- missing or malformed classification, HTTP error, or timeout → the tool returns
  the reason and tells the caller to handle the task in that thread,
- Jev confidence below 0.6 → no dispatch; the tool returns the confidence and
  suggests splitting the task,
- a task classified `destructive` without `confirm: true` → no thread; the tool
  returns the routing decision and asks the caller to get user confirmation,
- every dispatch carries the classification and the routing reason in the agent
  prompt and in the tool result, and the result states `requested model: …; Amp
  resolved agent definition: …` (with `⚠ MISMATCH` when they differ),
- a Jev failure is reported as `Jev Router did not dispatch`, never as a routed
  success; the dispatch tool never silently substitutes `fallbackDecision` (only
  the orchestrator does, and it marks the result `local fallback`).

## Automatic triage on `agent.start` (advisory only)

The hook no longer starts child threads. Auto-dispatch is gone, because a hook
that spawns an editor while the main agent is still working creates two writers
on the same workspace. Instead, on an obviously complex root prompt the hook
injects one **short, hidden** (`display: false`) instruction asking the *current*
thread to coordinate the work with `jev_orchestrate_task`. The main agent then
blocks inside that tool while the pipeline runs, so there is still exactly one
editor at a time.

The hook returns before touching Jev unless all of the following hold:

- `JEV_ROUTER_AUTO_TRIAGE` is not disabled (it defaults to on),
- the thread has no parent thread,
- this plugin has not already triaged the thread,
- the prompt matches a narrow local complexity signal (`looksComplex`).

It then classifies once with a shortened timeout. If Jev cannot classify (no key,
HTTP error, timeout, or low confidence) it falls back to `fallbackDecision`,
which is derived from the same local risk/complexity signals, so the injection
still works without a key. It injects only for candidates (`triagePlan` is not
`none`); ordinary prompts never call Jev and are left untouched. The injected
text is prefixed with the classification provenance
(`Jev Router classification: TypeSafe Jev classification` or `… local fallback —
Jev could not classify (<reason>)`), so a fallback is visible even though the hook is
hidden (`display: false`).

Recursion and duplicate-trigger protection:

- any thread with a parent (`ctx.thread.parentThreadID()`) is skipped, so
  dispatched children, subagents, and Task workers are never triaged again,
- thread IDs created by this plugin are also tracked in memory,
- a thread is triaged at most once per plugin process, including when
  classification fails,
- unexpected errors in the hook are logged and never break a turn.

Sensitive and destructive tasks are never dispatched; the injected instruction
tells the main agent to confirm destructive steps first, and
`jev_orchestrate_task` enforces that itself.

## Use

1. Export a key in the environment used by Amp (never commit it):
   `export TYPESAFE_API_KEY='…'` (optional for the hook, required for the
   `jev_dispatch_task` tool and the manual `Jev Router: Send` command).
2. Reload plugins from the Amp command palette with `plugins: reload`.
3. Either let a complex prompt trigger the hook, call
   **`jev_orchestrate_task`** yourself for a self-contained task (optionally with
   `profile` and `paths`), run **`jev_parallel_inspect`** for independent
   read-only questions, run `Jev Router: Send` to classify + dispatch manually, or
   call `jev_dispatch_task` directly.
4. To check the model pin independently of the pipeline, start a thread in one of
   the pinned modes (`amp -m jev-flash`, `-m jev-sol`, `-m jev-astra`) and compare
   its Cost & Usage entry — see
   [Verifying that routing took effect](#verifying-that-routing-took-effect).

Environment variables:

| Variable | Default | Effect |
| --- | --- | --- |
| `TYPESAFE_API_KEY` | — | Bearer token for the TypeSafe endpoint. |
| `JEV_ROUTER_AUTO_TRIAGE` | `1` | Set to `0` to disable the `agent.start` hook entirely. |
| `JEV_ROUTER_GATE` | `1` | Set to `0` to stop the `tool.call` gate from consulting Jev. The deterministic holds still apply. |
| `JEV_ROUTER_SUBAGENT_PIN` | `1` | Set to `0` to stop pinning subagents (fast model) and the oracle (Astra), leaving both roles on Amp's routing. |
| `JEV_ROUTER_ORACLE_MODEL` | `astra` | Model for the pinned oracle (`flash`, `sol`, `astra`, or a full model id). Only applies while `JEV_ROUTER_SUBAGENT_PIN` is on. |
| `JEV_ROUTER_TOOL_RESULT` | `1` | Set to `0` to stop `tool.result` from asking Jev's relevance question. The deterministic truncation still runs. |
| `JEV_ROUTER_AUTO_REVIEW` | `thread` | `thread` (default) starts one read-only review thread after a successful code-changing turn; `continue` returns one bounded review turn instead; `0`/`off` disables the hook. |
| `JEV_ROUTER_AUTO_REVIEW_MAX` | `1` | Maximum auto reviews per thread (clamped to 0–5). |
| `JEV_ROUTER_AUTO_REVIEW_MODEL` | unset → senior model | Pins the review observer's model (`flash`, `sol`, `astra`, or a full model id). Unset: escalates off a parent model the API exposes, else `sol`. The fast model is only ever used here when you ask for it. |
| `JEV_ROUTER_PROFILE` | `balanced` | Cost/quality profile (`economy`, `balanced`, `quality`, `sensitive`) for the pipeline's stage models. |

The old `JEV_ROUTER_AUTO_DISPATCH` switch is gone with the auto-dispatch
behaviour. The direct TypeSafe endpoint is
`https://api.typesafe.ai/v1/systemone` and the request uses
`Authorization: Bearer $TYPESAFE_API_KEY`. No other credential is read or
written by the plugin.

## Local checks

Amp loads `.amp/plugins/jev-router/index.ts` directly and the plugin has **no
runtime dependencies**; the `package.json`/`tsconfig.json` next to it exist only
for local tooling (types and a typecheck). They do not change how Amp loads the
plugin — verified with `amp plugins list` (reports
`✓ .amp/plugins/jev-router/index.ts active`) and `amp plugins exec`.

Layout:

```
.amp/plugins/
├── jev-router/
│   ├── index.ts          # the plugin (entry file)
│   └── tests/            # dev-only; a directory without index.ts is not a plugin
├── package.json          # dev tooling only
├── tsconfig.json         # dev tooling only
└── bun.lock              # dev tooling only
```

```bash
bun --check .amp/plugins/jev-router/index.ts
amp plugins list   # loads the plugin; lists the command, three tools, and events: agent.start, tool.call, tool.result, agent.end

# Tooling (first time only); installs typescript, @types/bun, @ampcode/plugin
cd .amp/plugins && bun install

# Unit tests (fake amp + disabled network; no real API key or model call)
bun test ./.amp/plugins/jev-router/tests/

# Typecheck the plugin (entry + tests) against the published @ampcode/plugin types
cd .amp/plugins && bun run typecheck
```

The types come from the `@ampcode/plugin` nightly that matched the Amp build at
the time; after updating Amp, refresh them with
`cd .amp/plugins && bun update @ampcode/plugin` so the gate keeps describing the
runtime you actually run. CI runs the same three commands in the `plugin` job.

Scope and limits of the typecheck, stated plainly:

- `tsconfig.json` covers **the entry and both test files**, with `strict: true`, so
  the whole plugin is typechecked. The test harness types its fakes at the seam
  (`createAgent` is cast once, where the fake implements only what the pipeline
  touches) instead of loosening the plugin's own types.
- It is a real gate, and it has paid for itself: it caught two genuine defects the
  fake-based tests could not. (1) `helpers.filesModifiedByToolCall` answers with **file URIs, not
  paths**, while the gate's sensitive-path patterns are written against local
  paths — the gate now converts them with the documented
  `helpers.filePathFromURI`. (2) `agent.start` is a *request* event, so its
  "do nothing" answer is the empty result `{}`, not `undefined`; the hook and the
  tests were updated accordingly. The test fake also grew a faithful
  `filePathFromURI`, so the conversion is covered rather than assumed.

The tests cover stage order, model selection, per-stage tool isolation, stage
failure short-circuiting, the destructive confirmation gate, hidden hook
injection without `createAgent`, plus the
observability contract: every stage's `requestedModel`/`agentModel`, the
`⚠ MISMATCH` flag, the `local fallback` marker (including that a fallback is never
reported as a Jev success), API-key redaction, that the display label never
overwrites the requested model, that the read-only tool names are current
built-ins, that the source never calls `getBuiltinAgent` or sets `extends:`, and
that the `// @amp-agent-mode` directives match the runtime mode registration.

The suite is deliberately behaviour-first: tests that only proved an export
existed, or that duplicated another test's contract, were removed, so every
remaining test pins something that can actually break. (When pruning, prefer the
end-to-end handler assertion over the helper-level restatement of it.)

`jev-router-extras.test.ts` adds the pure coverage for the newer pieces:
`tool.result` classification/compression bounds (errors preserved, a single
oversized line clipped, deterministic output), the relevance-budget rule and its
failure-open handler path, the evidence package parser/truncation, guidance
ranking/summarising and the shell fallback, the auto-review gate (dedupe, per
thread cap, continuation mode, recursion guard) and `turnHasCodeChanges`, the
review-model choice (senior by default, escalated off a parent model the API
exposes, pinnable) and the oracle pin (Astra, never the fast model by default),
`runWithConcurrency`/`parallelInspect` isolation, and the profile mapping
(including that no profile can lower the sensitive/destructive floor).

`parseJevResponse`, `modeFor`, `detectRisk`, `looksComplex`, `routeModel`,
`triagePlan`, `triageHint`, `describeDecision`, `inspectModel`, `planModel`,
`executeModel`, `reviewModel`, `parseReviewVerdict`, `fallbackDecision`,
`orchestrateHint`, `stageInstructions`, `stageMessage`, `clip`,
`classificationLabel`, `describeModelPin`, `readAgentModel`, `stageDisplay`,
`redactSecrets`, `MODEL_TAG`, `AGENT_MODES`, `READ_ONLY_STAGE_TOOLS`, `SUBAGENT_PIN`,
`classifyAction`, `parseSafetyResponse`, `gateAction`, and `LowConfidenceError` are
all exported pure helpers; anything that reaches the network or a child thread is
behind `classify`, `classifyAction`, `dispatch`, `orchestrate`, and the plugin
registration. `gateAction` is pure so the gate's policy is testable without a
network call or an Amp runtime; only `gateToolCall` touches both.

The newer pure helpers are exported too: `detectToolResultKind`,
`planToolResultCompression`, `compressToolResultText`, `adjustBudgetWithRelevance`,
`parseRelevanceResponse`, `buildRelevanceState`, `parseEvidencePackage`,
`formatEvidencePackage`, `extractJsonObject`, `pathHintsFromTask`,
`guidanceScore`, `rankGuidanceFiles`, `selectGuidanceFiles`, `summarizeGuidance`,
`guidancePathHint`, `guidanceDir`, `parseAutoReviewMode`, `autoReviewMax`,
`planAutoReview`, `turnHasCodeChanges`, `autoReviewMessage`, `runWithConcurrency`,
`formatParallelInspect`, `modelTier`, `isCostProfile`, `parseProfile`,
`profileFromEnv`, `resolveStageModels`, and `profileModels`. The functions that
touch the network or spawn a child — `classifyRelevance`, `parallelInspect`,
`collectGuidance`, and `handleToolResult`/`handleAgentEnd` via the event
registration — are kept separate from their pure policy.

## Verifying that routing took effect

The plugin can prove what it *requested* and what Amp *resolved into the agent
definition*; it cannot prove the model the provider actually served. Cost &
Usage is the only surface that shows the served model, and it can attribute a
child thread to its parent (or to the parent's model) rather than to the child's
own pin. That is why a run can look entirely `gpt-5.6-sol` even though the
inspect stage asked for `deepseek/deepseek-v4.1-flash`. Treat that as a platform
attribution/limitation to confirm in the UI, not as proof that the plugin ignored
its own pin.

Minimal, executable checks — cheapest first:

1. **Tool result.** Run `jev_orchestrate_task` on any small task and read the
   result. Each stage line shows `requested <model>` and, when the Plugin API
   exposes it, `agent definition <model>`. Different stages must show different
   models (inspect/execute = Flash, plan/execute-on-risk = Sol/Astra, review =
   Sol/Astra). A `⚠ MISMATCH` line means Amp resolved a different definition model
   than requested.

2. **Thread headers.** Each stage thread's header shows `Jev <Stage> · <Tier>`
   (for example `Jev Plan · Astra`), and each dispatched thread shows
   `Jev <Tier>`. Header labels are the plugin's request, so this only confirms the
   plugin asked for the right model.

3. **Independent mode pin (strongest plugin-side check).** The three registered
   modes are the same pins exposed as normal user threads:

   ```bash
   amp -m jev-flash -x "Reply with exactly OK"   # deepseek/deepseek-v4.1-flash
   amp -m jev-sol   -x "Reply with exactly OK"   # openai/gpt-5.6-sol
   amp -m jev-astra -x "Reply with exactly OK"   # openai/gpt-6-astra
   ```

   A mode thread is created the way a user thread is, not as a plugin child, so
   its entry in Cost & Usage is directly comparable. If `jev-flash` also bills as
   `gpt-5.6-sol`, the platform is not honouring the plugin's model pin at all; if
   it bills as `deepseek-v4.1-flash`, pinning works and any all-`sol` pipeline
   reading is child-thread attribution.

4. **Confirm in the UI.** Open Cost & Usage for each child thread of a pipeline
   run and compare it with the `requested model` the tool result printed. Record
   the mismatch (or the match) — do not assume either way.

## Token savings: realistic bounds

Be honest about where the savings are:

- **Subagent pinning and the stage handoffs are the real wins.** Subagents answer
  with a compact result instead of the parent re-reading, and each pipeline stage
  sees the task plus a capped prior report (24 k characters between stages, 12 k
  of diff to review, 1.5 k echoed per stage), never the previous transcript.
- **`tool.result` is a bound, not an optimisation.** It only fires above 8 000
  characters and only removes surrounding noise around preserved errors and
  structure, so it can save a lot on a giant diff or test log and nothing at all
  on ordinary output. It adds one Jev call (plus up to a 4 s wait) for a large
  output when a key is set; turn it off with `JEV_ROUTER_TOOL_RESULT=0`.
- **The auto review spends tokens, it does not save them.** A background review
  thread and a `continue` are extra model work whose value is a second look at a
  change. The default reviewer is now the **senior** model rather than the fast
  one (see the section above), so a review costs more per run than it used to —
  the trade is a real cross-model second opinion instead of the cheap model
  checking expensive work. `JEV_ROUTER_AUTO_REVIEW_MODEL=flash` restores the cheap
  observer, and `JEV_ROUTER_AUTO_REVIEW=0` removes the review entirely. It stays
  bounded either way: one per turn, one per thread.
- **Profiles trade cost for quality, they do not create savings by themselves.**
  `economy` lowers the planned/reviewed tier where risk allows; `quality`/`sensitive`
  raise it. Pick deliberately.
- **The Jev calls themselves are cheap judgments**, but `agent.start` still costs
  one per complex prompt and the gate costs one per ordinary shell command; both
  have switches.

None of this is a promise of a specific number — measure Cost & Usage on your own
threads.

## Limitations

- **The plugin cannot route the thread it is running in.** The Plugin API has no
  call to change the model of an already-running thread, so Jev never picks the
  model for the current turn. It routes subagents (`SUBAGENT_PIN`), the pipeline
  stages, and dispatched children — and it gates actions (`tool.call`) instead of
  pretending to re-route them.
- **The gate's deterministic half is a text heuristic.** A command that merely
  mentions `.env`, `.git/`, or a key file in a string literal is held, and an
  obfuscated destructive command the patterns do not name gets through. Jev is the
  second line, not a replacement, and it only sees ordinary shell commands.
- **A hold needs a confident Jev answer.** Below the 0.6 floor a non-safe verdict
  is ignored, so `git push origin main` (observed: `needs_confirmation`, risk 0.94,
  confidence 0.50) is allowed. See the note under
  [The `tool.call` gate](#the-toolcall-gate).

- The **served provider model is not observable from the Plugin API.** The
  plugin reports `requestedModel` and, when exposed, `agent.definition.model`; it
  deliberately does not claim the served model. Confirm the served model in Cost
  & Usage (see above). If Cost & Usage attributes child threads to the parent,
  the plugin cannot correct that.
- The Amp Plugin API cannot restrict a tool to read-only. The read-only stages
  therefore get an explicit allow-list (`Read`, `finder`, `shell_command`,
  `web_search`, `read_web_page`, `read_thread`) with no editing tool, plus stage
  instructions that forbid any command which changes the workspace and forbid
  committing. A stage that ignores its instructions could still write through the
  shell; the plugin's structural guarantee is that only `execute` has an editing
  tool and that review reports problems instead of fixing them.
- `parseReviewVerdict` only trusts an explicit verdict line. A reviewer that
  writes no `## Verdict` section yields `unknown`, so a lazy review reads as
  "no verdict" rather than as a pass or a fail.
- Stage models are chosen from `VERIFIED_MODELS` only; there is no per-stage
  override argument yet.
- The pipeline is one-shot: a failing review is reported, not fixed, and there is
  no automatic re-run.
- `agent.start` hook classification still costs one Jev call on complex prompts;
  set `JEV_ROUTER_AUTO_TRIAGE=0` to remove it.
- The `jev-flash`/`jev-sol`/`jev-astra` modes appear in mode pickers next to
  Amp's built-in modes. They exist to make the pin checkable; remove the
  registration (and the `// @amp-agent-mode` directives) if the extra modes are
  not wanted.
- **`tool.result` cannot summarise or preserve the original.** The model sees the
  compressed text; the plugin keeps no copy. Its error-line heuristic errs toward
  keeping lines that merely look like errors, and one enormous single error line
  is kept whole rather than sliced — the safe direction, but it means the bound is
  a budget, not a hard ceiling, when errors alone exceed it.
- **`agent.end` uses only the API's documented results.** The handler cannot set
  the current thread's model, so a review on the parent's own model is only
  possible through the `continue` mode that spends it; the background mode always
  creates a child thread, with its model coming from `autoReviewModel`
  (`JEV_ROUTER_AUTO_REVIEW_MODEL`, else escalated off an exposed parent model, else
  the senior coding model). The handler waits on the bounded thread-creation call,
  not on the review's work, and any failure is swallowed.
- **The guidance selector is a path-prefix heuristic.** It ranks a small set of
  `AGENTS.md`/`CLAUDE.md` files by the task's path hints and summarises them; it
  can miss a relevant file or pick a marginally relevant one, and it only ever
  injects a capped summary, never a full file.
- **Profiles are per-pipeline.** There is still no per-stage model override
  argument, and `JEV_ROUTER_PROFILE` applies to `jev_orchestrate_task` (the
  parallel inspect tool is always Flash).

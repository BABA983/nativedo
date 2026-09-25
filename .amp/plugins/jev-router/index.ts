// Static discovery metadata for the model-pinned modes registered below. Keep each directive in
// sync with `AGENT_MODES`; Amp warns when a runtime `registerAgentMode` does not match its
// directive. Format is documented under "Example Plugin: Custom Agent Mode" in
// `amp plugins show-docs`.
// @amp-agent-mode {"key":"jev-flash","label":"Jev Flash"}
// @amp-agent-mode {"key":"jev-sol","label":"Jev Sol"}
// @amp-agent-mode {"key":"jev-astra","label":"Jev Astra"}
import type {
	AgentEndResult,
	AgentRunResult,
	AgentStartResult,
	AgentToolSelection,
	BuiltinAgentMode,
	CreateAgentConfig,
	PluginAPI,
	PluginEventContext,
	PluginEventMap,
	ThreadMessage,
	ToolCall,
	ToolCallResult,
	ToolResultResult,
	ThreadID,
} from '@ampcode/plugin'

export const description =
	'Classifies tasks and gates risky tool calls with TypeSafe Jev, pins cheap subagents, and runs a serial inspect/plan/execute/review pipeline on verified Amp models.'

const TYPESAFE_ENDPOINT = 'https://api.typesafe.ai/v1/systemone'
const MIN_CONFIDENCE = 0.6
const FLASH_MIN_CONFIDENCE = 0.8
const AUTO_DISPATCH_MIN_CONFIDENCE = 0.75
const AUTO_TRIAGE_TIMEOUT_MS = 8_000
/** Wall-clock budget for one Jev call made from the `tool.call` gate. It runs inline, in front of
 * the tool the agent is about to use, so it has to be short. */
const GATE_TIMEOUT_MS = 5_000
const DEFAULT_DISPATCH_TIMEOUT_MS = 5 * 60_000
/** Per-stage wall-clock budgets for the four-stage pipeline. */
export const STAGE_TIMEOUTS = {
	inspect: 5 * 60_000,
	plan: 10 * 60_000,
	execute: 30 * 60_000,
	review: 10 * 60_000,
} as const
/** Cap on how much of one stage's report is echoed in the tool result. */
const STAGE_SUMMARY_CHARS = 1_500
/** Cap on how much of one stage's report is forwarded to the next stage. */
const STAGE_HANDOFF_CHARS = 24_000
/** Cap on how much of the captured workspace diff is forwarded to review. */
const DIFF_HANDOFF_CHARS = 12_000

// ---------------------------------------------------------------------------
// tool.result compression budgets (deterministic code owns these; Jev only
// nudges the budget inside [min, max], it never writes text)
// ---------------------------------------------------------------------------
/** Outputs at or below this size are passed through untouched, with no Jev call. */
export const TOOL_RESULT_SAFE_CHARS = 8_000
/** Aggressive budget, used when Jev is confident the output is mostly noise. */
export const TOOL_RESULT_MIN_CHARS = 6_000
/** Neutral budget. */
export const TOOL_RESULT_DEFAULT_CHARS = 14_000
/** Generous budget, used when Jev says the output is essential (or is unsure). */
export const TOOL_RESULT_MAX_CHARS = 24_000
/** Wall-clock budget for the one optional Jev relevance call in `tool.result`. */
const TOOL_RESULT_JEV_TIMEOUT_MS = 4_000
/** Cap on how much of one output is sent to Jev as state. */
const TOOL_RESULT_JEV_STATE_CHARS = 8_000

// ---------------------------------------------------------------------------
// Conditional project guidance (AGENTS.md / CLAUDE.md)
// ---------------------------------------------------------------------------
export const MAX_GUIDANCE_FILES = 3
export const MAX_GUIDANCE_CHARS = 1_200
const MAX_GUIDANCE_FILE_CHARS = 600
/** Known guidance paths in this repo, used as a path-hint fallback when shell is unavailable. */
export const KNOWN_GUIDANCE_PATHS = ['AGENTS.md', 'CLAUDE.md', 'apps/android/AGENTS.md'] as const

// ---------------------------------------------------------------------------
// Auto review (agent.end) — bounded, non-blocking, read-only
// ---------------------------------------------------------------------------
export const AUTO_REVIEW_DEFAULT_MAX = 1
const AUTO_REVIEW_TIMEOUT_MS = 10_000

// ---------------------------------------------------------------------------
// Parallel read-only exploration
// ---------------------------------------------------------------------------
export const PARALLEL_INSPECT_DEFAULT_CONCURRENCY = 3
export const PARALLEL_INSPECT_MAX_CONCURRENCY = 5
export const PARALLEL_INSPECT_MAX_QUESTIONS = 6
const PARALLEL_INSPECT_TIMEOUT_MS = 5 * 60_000
const ROUTES = ['coding', 'research', 'planning', 'writing', 'general'] as const
type Route = (typeof ROUTES)[number]

/**
 * Models verified for Jev Router dispatch.
 *
 * Keep this list in sync with `amp plugins show-agent-options --json`; do not route to a model
 * that is not listed there. All three advertise `tools: true`.
 */
export const VERIFIED_MODELS = {
	flash: 'deepseek/deepseek-v4.1-flash',
	sol: 'openai/gpt-5.6-sol',
	astra: 'openai/gpt-6-astra',
} as const
export type RoutedModel = (typeof VERIFIED_MODELS)[keyof typeof VERIFIED_MODELS]

/**
 * Short, UI-safe tag for each verified model. It appears in agent names and display labels so a
 * thread header or log line states which model the plugin *requested*. It is never assigned to
 * the `model` field itself.
 */
export const MODEL_TAG: Record<RoutedModel, string> = {
	[VERIFIED_MODELS.flash]: 'flash',
	[VERIFIED_MODELS.sol]: 'sol',
	[VERIFIED_MODELS.astra]: 'astra',
}

/** Human-facing name for each verified model, used in display labels. */
const MODEL_LABEL: Record<RoutedModel, string> = {
	[VERIFIED_MODELS.flash]: 'Flash',
	[VERIFIED_MODELS.sol]: 'Sol',
	[VERIFIED_MODELS.astra]: 'Astra',
}

const MODEL_COLOR: Record<RoutedModel, string> = {
	[VERIFIED_MODELS.flash]: '#60a5fa',
	[VERIFIED_MODELS.sol]: '#14b8a6',
	[VERIFIED_MODELS.astra]: '#8b5cf6',
}

/** Reasoning efforts supported by the pinned provider models in this Amp build. */
export const MODEL_REASONING_EFFORT: Record<RoutedModel, 'none' | 'medium' | 'high'> = {
	[VERIFIED_MODELS.flash]: 'high',
	[VERIFIED_MODELS.sol]: 'medium',
	[VERIFIED_MODELS.astra]: 'medium',
}

/**
 * Model pin for the subagents an agent spawns: Amp's `Task` workers, `finder`, thread reader, and
 * `librarian`, plus the `oracle` (`CreateAgentConfig.subagents` / `.oracle`).
 *
 * This is the routing that actually pays. Reading and searching is the bulk of an agent's tool
 * turns, and a subagent answers with a compact result that lands in the parent's context as a tool
 * result — so the parent never re-loads what the subagent read. That is the opposite of routing the
 * parent thread itself, where the senior model has to re-process everything the cheap model saw.
 *
 * Amp falls back to its own routing when the pinned model is unavailable, so this is an
 * optimisation and never a correctness dependency. Set `JEV_ROUTER_SUBAGENT_PIN=0` to leave Amp's
 * routing alone.
 */
export const SUBAGENT_PIN = {
	model: VERIFIED_MODELS.flash,
	effort: MODEL_REASONING_EFFORT[VERIFIED_MODELS.flash],
} as const

/**
 * Model pin for the `oracle` a plugin thread consults — deliberately **not** the fast model.
 *
 * The oracle is a judgement role, not a retrieval one: search and reading answer with a compact
 * result, so the cheap model belongs there, while a second opinion is only worth taking from a
 * stronger model. Astra is also what Amp's own modes default the oracle to, so this matches the
 * product rather than fighting it. Only the model is pinned; the oracle keeps its own reasoning
 * effort. `JEV_ROUTER_ORACLE_MODEL` overrides the model, and `JEV_ROUTER_SUBAGENT_PIN=0` leaves
 * both the subagents and the oracle on Amp's routing.
 */
export function oraclePin(raw: string | undefined = env()?.JEV_ROUTER_ORACLE_MODEL): { model: RoutedModel } {
	return { model: parseRoutedModel(raw) ?? VERIFIED_MODELS.astra }
}

/** The `subagents` pin for one plugin agent, or `undefined` to leave Amp's routing alone. */
export function subagentPin(): { model: RoutedModel; effort: 'none' | 'medium' | 'high' } | undefined {
	return envFlag('JEV_ROUTER_SUBAGENT_PIN', true) ? { ...SUBAGENT_PIN } : undefined
}

/** The `oracle` pin for one plugin agent, or `undefined` to leave Amp's routing alone. */
export function resolveOraclePin(): { model: RoutedModel } | undefined {
	return envFlag('JEV_ROUTER_SUBAGENT_PIN', true) ? oraclePin() : undefined
}

/**
 * The verified models, also exposed as first-class model-pinned Amp modes via
 * `amp.registerAgentMode`. A mode thread is created the way a user thread is, so its served
 * model is visible in Amp's Cost & Usage — the only reliable way to confirm that Amp honours a
 * plugin's model pin. Keep each `key`/`label` in sync with the matching `// @amp-agent-mode`
 * directive at the top of this file.
 *
 * These modes are a verification affordance, not how the pipeline runs: pipeline stages create
 * their own agents with `createAgent({ model })` at run time.
 */
export const AGENT_MODES = [
	{
		key: 'jev-flash',
		label: 'Jev Flash',
		model: VERIFIED_MODELS.flash,
		description:
			'Runs on the fast verified model (deepseek/deepseek-v4.1-flash) for bounded, low-risk work. Use to check that Amp serves a plugin-pinned fast model.',
	},
	{
		key: 'jev-sol',
		label: 'Jev Sol',
		model: VERIFIED_MODELS.sol,
		description:
			'Runs on the coding verified model (openai/gpt-5.6-sol) for focused coding and review, including sensitive or destructive work that must not run on the fast model.',
	},
	{
		key: 'jev-astra',
		label: 'Jev Astra',
		model: VERIFIED_MODELS.astra,
		description:
			'Runs on the deep-reasoning verified model (openai/gpt-6-astra) for architecture, cross-cutting, and long-context work.',
	},
] as const

/** How risky the task looks before routing. */
export type TaskRisk = 'normal' | 'sensitive' | 'destructive'

type JevAnswer = {
	type: 'choice' | 'score' | 'noul'
	choice?: string
	score?: number
	noul?: number
	confidence?: number
}

export type RouteDecision = {
	route: Route
	complexity: number
	needsLongContext: boolean
	needsTools: boolean
	confidence: number
	risk: TaskRisk
	model: RoutedModel
	reason: string
	mode: BuiltinAgentMode
}

/** Jev's structured Choice/Score/Noul questions. */
const QUESTIONS = {
	route: {
		type: 'choice',
		instructions: 'What kind of task is this?',
		criteria: {
			coding: 'Implement, debug, test, or review software or configuration.',
			research: 'Investigate facts, compare sources, or synthesize external information.',
			planning: 'Design an approach, architecture, or sequence of work.',
			writing: 'Draft, edit, translate, or summarize prose or documentation.',
			general: 'Anything that does not fit the other categories.',
		},
	},
	complexity: {
		type: 'score',
		instructions: 'How complex is this task for an agent to complete?',
		criteria: ['Simple and focused', 'Several steps or files', 'Complex or cross-cutting'],
	},
	needs_long_context: {
		type: 'noul',
		instructions: 'Will completing this task require unusually large context?',
		criteria: {
			true: 'Many files, long documents, or extensive history are important.',
			false: 'The task can be completed with a normal-sized context.',
		},
	},
	needs_tools: {
		type: 'noul',
		instructions: 'Will the agent need tools such as shell, browser, or file editing?',
		criteria: {
			true: 'The task requires interacting with a repository or external systems.',
			false: 'The task is answerable without tools.',
		},
	},
} as const

const DESTRUCTIVE_PATTERNS: readonly RegExp[] = [
	/\brm\s+-rf\b/,
	/\bdelete\b|\bdeleting\b|\bdeletion\b/,
	/\bdrop\s+(table|database|schema|column)\b/,
	/\btruncate\b/,
	/\bwipe\b|\berase\b|\bpurge\b/,
	/\bforce[- ]?push\b|\bpush\s+--force\b/,
	/\breset\s+--hard\b/,
	/\boverwrite\b/,
	/\bdestroy\b|\bterminate\b|\brevoke\b/,
	/\bmigrat(e|ing|ion)\b/,
	/\bproduction\b|\bprod\s+deploy\b|\bdeploy\s+to\s+prod\b/,
]

const SENSITIVE_PATTERNS: readonly RegExp[] = [
	/\bpassword\b|\bpassphrase\b/,
	/\bapi[- ]?key\b|\bsecret\b|\bcredential\b|\bbearer\s+token\b/,
	/\boauth\b|\bsso\b|\bsession\s+cookie\b/,
	/\bprivate\s+key\b|\bdecrypt\b|\bencryption\b/,
	/\bpayment\b|\bbilling\b|\bcredit\s+card\b|\bpii\b/,
	/\bmedical\b|\bhealth\s+record\b|\blegal\b|\bfinancial\b/,
	/\bsecurity\s+(audit|review|vulnerabilit)/,
	/\buser\s+data\b|\bpersonal\s+data\b/,
]

/**
 * Signals that a prompt is large enough to be worth classifying before the turn starts.
 * Deliberately narrow: the auto-triage hook runs on every submitted prompt.
 */
const COMPLEXITY_SIGNALS: readonly RegExp[] = [
	/\barchitect|\barchitecture\b/,
	/\brefactor/,
	/\bend[- ]to[- ]end\b/,
	/\bmulti[- ]?(file|module|service|repo|step|package)/,
	/\broot[- ]cause\b|\bdiagnos/,
	/\binvestigat/,
	/\bdesign\s+(an|a|the)\s+(approach|architecture|plan|api)\b/,
	/\bplan(n?ing)?\s+(the|a|an|and|how)\b|\broadmap\b|\bstrategy\b/,
	/\bcoordinat|\bdelegat|\bsub-?task/,
	/\bseveral\s+(files|modules|packages)\b/,
	/\bacross\s+the\s+(repo|codebase|project|system)\b/,
]

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === 'object' && value !== null
}

function answer(value: unknown, key: string): JevAnswer {
	if (!isRecord(value) || (value.type !== 'choice' && value.type !== 'score' && value.type !== 'noul')) {
		throw new Error(`Jev returned an invalid answer for ${key}.`)
	}
	return value as unknown as JevAnswer
}

function probability(value: unknown, key: string): number {
	if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 1) {
		throw new Error(`Jev returned an invalid probability for ${key}.`)
	}
	return value
}

function confidence(value: unknown, key: string): number {
	return probability(value, key)
}

/** Raised when Jev classifies a task but is not confident enough to act on it. */
export class LowConfidenceError extends Error {
	readonly confidence: number

	constructor(confidence: number) {
		super(`Jev confidence is too low (${confidence.toFixed(2)}; minimum is ${MIN_CONFIDENCE}).`)
		this.name = 'LowConfidenceError'
		this.confidence = confidence
	}
}

/**
 * Deterministic risk classification. Risk is read from the task text instead of Jev so that the
 * destructive/sensitive guard holds even when the classifier is unavailable or over-confident.
 */
export function detectRisk(task: string): TaskRisk {
	const text = task.toLowerCase()
	if (DESTRUCTIVE_PATTERNS.some((pattern) => pattern.test(text))) return 'destructive'
	if (SENSITIVE_PATTERNS.some((pattern) => pattern.test(text))) return 'sensitive'
	return 'normal'
}

/** Cheap local prefilter for the `agent.start` hook; not a substitute for Jev. */
export function looksComplex(task: string): boolean {
	if (task.trim().length < 24) return false
	return COMPLEXITY_SIGNALS.some((pattern) => pattern.test(task))
}

/** Keep forwarded text compact so later stages never receive a full transcript. */
export function clip(text: string, max: number): string {
	const trimmed = text.trim()
	if (trimmed.length <= max) return trimmed
	return `${trimmed.slice(0, max)}\n…[truncated ${trimmed.length - max} characters]`
}

export type ModelRoutingInput = {
	route: Route
	complexity: number
	needsLongContext: boolean
	needsTools: boolean
	confidence: number
	risk: TaskRisk
}

export type ModelRouting = {
	model: RoutedModel
	reason: string
}

/**
 * Deterministic model routing. The fast model is only reachable for simple, low-risk work that
 * Jev classified confidently; risk never lowers the tier, it only raises it.
 */
export function routeModel(input: ModelRoutingInput): ModelRouting {
	if (input.route === 'planning') {
		return { model: VERIFIED_MODELS.astra, reason: 'planning work needs the deep-reasoning model' }
	}
	if (input.complexity >= 2) {
		return {
			model: VERIFIED_MODELS.astra,
			reason: `complexity ${input.complexity.toFixed(1)} needs the deep-reasoning model`,
		}
	}
	if (input.needsLongContext) {
		return { model: VERIFIED_MODELS.astra, reason: 'long-context work needs the deep-reasoning model' }
	}
	if (input.risk === 'destructive') {
		return { model: VERIFIED_MODELS.astra, reason: 'destructive work needs the deep-reasoning model' }
	}
	if (input.risk === 'sensitive') {
		return { model: VERIFIED_MODELS.sol, reason: 'sensitive work stays on the verified coding model' }
	}
	if (input.confidence < FLASH_MIN_CONFIDENCE) {
		return {
			model: VERIFIED_MODELS.sol,
			reason: `confidence ${(input.confidence * 100).toFixed(0)}% is below the fast-model bar`,
		}
	}
	if (input.route === 'coding' || input.needsTools || input.route === 'general' || input.route === 'writing') {
		return {
			model: VERIFIED_MODELS.flash,
			reason: `bounded execution work at ${(input.confidence * 100).toFixed(0)}% confidence may use the fast model`,
		}
	}
	return {
		model: VERIFIED_MODELS.sol,
		reason: 'this task is not a bounded execution route',
	}
}

export function modeFor(input: Pick<RouteDecision, 'route' | 'complexity' | 'needsLongContext' | 'needsTools'>): BuiltinAgentMode {
	if (input.complexity >= 2 || input.needsLongContext) return 'ultra'
	if (input.complexity >= 1 || input.needsTools || input.route === 'coding' || input.route === 'research') return 'high'
	if (input.route === 'planning' || input.route === 'writing') return 'medium'
	return 'low'
}

export function parseJevResponse(value: unknown, task = ''): RouteDecision {
	if (!isRecord(value) || !isRecord(value.answers)) {
		throw new Error('Jev returned an invalid response: answers are missing.')
	}

	const route = answer(value.answers.route, 'route')
	const complexity = answer(value.answers.complexity, 'complexity')
	const longContext = answer(value.answers.needs_long_context, 'needs_long_context')
	const tools = answer(value.answers.needs_tools, 'needs_tools')

	if (route.type !== 'choice' || !ROUTES.includes(route.choice as Route)) {
		throw new Error('Jev returned an invalid route choice.')
	}
	if (complexity.type !== 'score' || typeof complexity.score !== 'number' || !Number.isFinite(complexity.score)) {
		throw new Error('Jev returned an invalid complexity score.')
	}
	if (complexity.score < 0 || complexity.score > 2) {
		throw new Error('Jev returned a complexity score outside the configured rubric.')
	}
	if (longContext.type !== 'noul' || typeof longContext.noul !== 'number') {
		throw new Error('Jev returned an invalid long-context answer.')
	}
	if (tools.type !== 'noul' || typeof tools.noul !== 'number') {
		throw new Error('Jev returned an invalid tools answer.')
	}

	const longContextProbability = probability(longContext.noul, 'needs_long_context')
	const toolsProbability = probability(tools.noul, 'needs_tools')
	const routeConfidence = confidence(route.confidence, 'route.confidence')
	const complexityConfidence = confidence(complexity.confidence, 'complexity.confidence')
	const overallConfidence = Math.min(
		routeConfidence,
		complexityConfidence,
		Math.max(longContextProbability, 1 - longContextProbability),
		Math.max(toolsProbability, 1 - toolsProbability),
	)

	if (overallConfidence < MIN_CONFIDENCE) {
		throw new LowConfidenceError(overallConfidence)
	}

	const decision: Pick<RouteDecision, 'route' | 'complexity' | 'needsLongContext' | 'needsTools'> = {
		route: route.choice as Route,
		complexity: complexity.score,
		needsLongContext: longContextProbability >= 0.6,
		needsTools: toolsProbability >= 0.6,
	}
	const risk = detectRisk(task)
	const routing = routeModel({ ...decision, confidence: overallConfidence, risk })

	return {
		...decision,
		confidence: overallConfidence,
		risk,
		model: routing.model,
		reason: routing.reason,
		mode: modeFor(decision),
	}
}

/**
 * Deterministic decision used when Jev cannot run (no key, HTTP error, timeout, low confidence).
 * It is derived from the same local risk and complexity signals the guard already trusts, so the
 * pipeline can still be coordinated without the classifier.
 */
export function fallbackDecision(task: string): RouteDecision {
	const risk = detectRisk(task)
	const complex = looksComplex(task)
	const route: Route = complex || risk !== 'normal' ? 'planning' : 'coding'
	const decision = { route, complexity: complex ? 2 : 0, needsLongContext: false, needsTools: true }
	const routing = routeModel({ ...decision, confidence: MIN_CONFIDENCE, risk })
	return {
		...decision,
		confidence: MIN_CONFIDENCE,
		risk,
		model: routing.model,
		reason: `local fallback (${routing.reason})`,
		mode: modeFor(decision),
	}
}

export type TriagePlan = 'none' | 'hint' | 'dispatch'

/**
 * What the `agent.start` hook should do with a decision. Only obviously complex or
 * planning-shaped, low-risk, confident tasks are ever candidates; everything else is
 * left alone or gets a visible hint.
 */
export function triagePlan(decision: RouteDecision): TriagePlan {
	const candidate = decision.route === 'planning' || decision.complexity >= 2
	if (!candidate || decision.confidence < MIN_CONFIDENCE) return 'none'
	if (decision.risk === 'normal' && decision.confidence >= AUTO_DISPATCH_MIN_CONFIDENCE) return 'dispatch'
	return 'hint'
}

export function describeDecision(decision: RouteDecision): string {
	return (
		`route: ${decision.route}; complexity: ${decision.complexity.toFixed(1)}; ` +
		`long context: ${decision.needsLongContext ? 'yes' : 'no'}; tools: ${decision.needsTools ? 'yes' : 'no'}; ` +
		`risk: ${decision.risk}; confidence: ${(decision.confidence * 100).toFixed(0)}%; ` +
		`requested model: ${decision.model} (${decision.reason}); ` +
		`advisory built-in mode: ${decision.mode} (not the model)`
	)
}

/** Human-readable, main-thread-visible hint for a task that was not auto-dispatched. */
export function triageHint(decision: RouteDecision): string {
	const riskNote =
		decision.risk === 'normal'
			? ''
			: ` Risk is ${decision.risk}, so Jev Router will not auto-dispatch it.`
	return (
		`Jev Router: this looks like planning-shaped work (${describeDecision(decision)}).${riskNote} ` +
		'Plan before editing, or delegate it with the `jev_dispatch_task` tool.'
	)
}

// ---------------------------------------------------------------------------
// Four-stage pipeline: inspect → plan → execute → review
// ---------------------------------------------------------------------------

export const ORCHESTRATE_STAGES = ['inspect', 'plan', 'execute', 'review'] as const
export type OrchestrateStage = (typeof ORCHESTRATE_STAGES)[number]

/**
 * Built-in tool names granted to the read-only stages, mirrored from
 * `amp plugins show-agent-options`. `shell_command` is included for read-only evidence gathering;
 * the editing tools are deliberately absent so only `execute` can change the workspace.
 */
export const READ_ONLY_STAGE_TOOLS = [
	'Read',
	'finder',
	'shell_command',
	'web_search',
	'read_web_page',
	'read_thread',
] as const

/**
 * Tool sets per stage. Inspect, plan, and review are read-only in the sense that matters: they
 * never receive an editing tool, so they cannot write a file the way `execute` can. They do get
 * `shell_command`, because in a repo like this one the evidence a stage needs is mostly command
 * output (`bun test`, `git diff`, `vendor-guard.sh`); a first dogfood run showed an inspect stage
 * with no shell tool reporting "I could not run any of the commands". The Plugin API cannot
 * restrict a tool to read-only, so the no-writes rule for those stages lives in their
 * instructions, which forbid any command that changes the workspace.
 *
 * Only execute gets `all`, and it is the single writer in the pipeline, which keeps the four
 * stages from editing concurrently.
 *
 * Every name here is a built-in tool name from `amp plugins show-agent-options` (verified
 * 2026-09-22). An explicit array replaces Amp's default tool list, so the read-only stages list
 * each reader they need and deliberately omit the edit tools (`create_file`, `edit_file`,
 * `apply_patch`), `Task`, and the `plugin__*` / `mcp__*` wildcards.
 */
export const STAGE_TOOLS: Record<OrchestrateStage, AgentToolSelection> = {
	inspect: [...READ_ONLY_STAGE_TOOLS],
	plan: [...READ_ONLY_STAGE_TOOLS],
	review: [...READ_ONLY_STAGE_TOOLS],
	execute: 'all',
}

const STAGE_META = {
	inspect: { title: 'Inspect', color: '#60a5fa' },
	plan: { title: 'Plan', color: '#8b5cf6' },
	execute: { title: 'Execute', color: '#14b8a6' },
	review: { title: 'Review', color: '#f59e0b' },
} as const

/**
 * Display for one stage thread. The label names the requested model so the thread header shows
 * which model was pinned, instead of an ambiguous "Jev Plan" that says nothing about the model.
 */
export function stageDisplay(stage: OrchestrateStage, model: RoutedModel): { label: string; color: string } {
	return { label: `Jev ${STAGE_META[stage].title} · ${MODEL_LABEL[model]}`, color: STAGE_META[stage].color }
}

export type StageModelChoice = { model: RoutedModel; reason: string }

/** Inspect is bounded, read-only evidence gathering: the fast model is enough. */
export function inspectModel(): StageModelChoice {
	return {
		model: VERIFIED_MODELS.flash,
		reason: 'inspection is bounded, read-only evidence gathering',
	}
}

/**
 * Jev's structured decision picks the planner: architecture, cross-cutting, long-context, or
 * otherwise high-complexity work goes to the deep-reasoning model, and focused work stays on the
 * coding model.
 */
export function planModel(decision: Pick<RouteDecision, 'route' | 'complexity' | 'needsLongContext' | 'risk'>): StageModelChoice {
	if (
		decision.route === 'planning' ||
		decision.complexity >= 2 ||
		decision.needsLongContext ||
		decision.risk === 'destructive'
	) {
		return {
			model: VERIFIED_MODELS.astra,
			reason: 'architecture, cross-cutting, long-context, or high-complexity planning needs the deep-reasoning model',
		}
	}
	return { model: VERIFIED_MODELS.sol, reason: 'focused planning stays on the verified coding model' }
}

/**
 * The flash executor runs approved, low-risk plans. Sensitive and destructive work must not run
 * on the fast model; the coding model executes it instead (and destructive work only after the
 * caller confirmed it).
 */
export function executeModel(decision: Pick<RouteDecision, 'risk'>): StageModelChoice {
	if (decision.risk === 'sensitive') {
		return { model: VERIFIED_MODELS.sol, reason: 'sensitive work must not run on the fast model' }
	}
	if (decision.risk === 'destructive') {
		return { model: VERIFIED_MODELS.sol, reason: 'destructive work runs on the coding model after confirmation' }
	}
	return { model: VERIFIED_MODELS.flash, reason: 'an approved plan is bounded execution work for the fast model' }
}

/**
 * Review must use a senior model that did not execute the change. It defaults to the coding
 * model, escalates to the deep-reasoning model when the planner already used the coding model,
 * and never matches the executor.
 */
export function reviewModel(input: { executor: RoutedModel; planner: RoutedModel; risk: TaskRisk }): StageModelChoice {
	let model: RoutedModel = input.planner === VERIFIED_MODELS.sol ? VERIFIED_MODELS.astra : VERIFIED_MODELS.sol
	let reason =
		input.planner === VERIFIED_MODELS.sol
			? 'review escalates because the planner already used the coding model'
			: 'review runs on the senior coding model'
	if (model === input.executor) {
		model = VERIFIED_MODELS.astra
		reason = 'review escalates so the reviewer differs from the executor'
	}
	if (input.risk === 'destructive' && model !== VERIFIED_MODELS.astra) {
		model = VERIFIED_MODELS.astra
		reason = 'destructive work is reviewed on the deep-reasoning model'
	}
	return { model, reason }
}

export type ReviewStatus = 'pass' | 'fail' | 'unknown'

/** The verdict tokens the reviewer is asked to write, in the format the stage message mandates. */
const VERDICT_TOKEN = /\bchanges[_ ]required\b|\buncertain\b|\bunknown\b|\bpass(?:ed)?\b|\bfail(?:ed|ure|s)?\b/i

/**
 * Separators between the verdict and its one-sentence justification ("PASS — nothing failed").
 * `|` is deliberately not one: a line that pipes several options together is an echoed template,
 * not a chosen verdict, and must read as ambiguous rather than as the first option.
 */
const VERDICT_JUSTIFICATION = /[—–:;.]|\s-\s/

/**
 * The line that carries the verdict, or undefined when the report has no verdict section. The
 * verdict is normally the first non-empty line under `## Verdict`; `## Verdict: PASS` also counts.
 */
function verdictLine(report: string): string | undefined {
	const lines = report.split('\n')
	const start = lines.findIndex((line) => /^\s{0,3}#{0,6}\s*verdict\b/i.test(line))
	if (start < 0) return undefined
	const inline = (lines[start] ?? '').replace(/^\s{0,3}#{0,6}\s*verdict\b[^\w]*/i, '').trim()
	if (inline) return inline
	for (const line of lines.slice(start + 1)) {
		if (/^\s{0,3}#{1,6}\s/.test(line)) return undefined // the next section started: no verdict line
		if (line.trim()) return line.trim()
	}
	return undefined
}

/**
 * Read the review verdict from the reviewer's findings-first report.
 *
 * Only the verdict line is consulted. Scanning the whole report made prose decide the verdict: a
 * real review that said "nothing failed, so there is no failing test name to report" was parsed as
 * `fail`. Equally, a reviewer who echoes the mandated template ("PASS | CHANGES_REQUIRED |
 * UNCERTAIN — …") has not chosen a verdict, so that is `unknown` rather than a guess. A report
 * with no verdict section is `unknown` too.
 */
export function parseReviewVerdict(report: string): ReviewStatus {
	const line = verdictLine(report)
	if (line === undefined) return 'unknown'
	const head = line.split(VERDICT_JUSTIFICATION)[0] ?? ''
	const tokens = new Set((head.match(new RegExp(VERDICT_TOKEN.source, 'gi')) ?? []).map((t) => t.toLowerCase()))
	if (tokens.size !== 1) return 'unknown'
	const token = tokens.values().next().value as string
	if (token.startsWith('changes') || token.startsWith('fail')) return 'fail'
	if (token.startsWith('uncertain') || token.startsWith('unknown')) return 'unknown'
	return 'pass'
}

const INSPECT_INSTRUCTIONS = [
	'You are the INSPECT stage of the Jev Router pipeline: inspect → plan → execute → review.',
	'You run in the user\'s live workspace, which the other stages share.',
	'You have no editing tool: never create, edit, move, or remove a file.',
	'You may run shell commands to gather evidence (tests, type checks, greps, `git status`, `git diff`, `git log`), but never run a command that changes the workspace, and never commit.',
	'Produce a compact evidence package for the planner and executor, who will not see this thread\'s transcript or any full file body.',
].join(' ')

const PLAN_INSTRUCTIONS = [
	'You are the PLAN stage of the Jev Router pipeline: inspect → plan → execute → review.',
	'You run in the user\'s live workspace and may read it, but you must not edit files or run commands that change the workspace.',
	'You may run read-only shell commands to check a fact; you have no editing tool and must not commit.',
	'Turn the task and the inspection report into a concrete, ordered plan the executor will follow exactly.',
	'Do not write the code changes themselves.',
].join(' ')

const EXECUTE_INSTRUCTIONS = [
	'You are the EXECUTE stage of the Jev Router pipeline: inspect → plan → execute → review.',
	'You run in the user\'s live shared workspace and you are the only stage allowed to modify it.',
	'Implement the approved plan exactly; do not widen the scope on your own.',
	'Run the plan\'s verification and report precisely what you changed and what you verified.',
	'If a step cannot be done safely or the plan is wrong, stop and report it instead of guessing.',
].join(' ')

const EXECUTE_RISK_NOTE = [
	'This task is sensitive or destructive: treat credential-touching, irreversible, or production-affecting steps as confirmation-required.',
	'Report those steps instead of performing them.',
].join(' ')

const REVIEW_INSTRUCTIONS = [
	'You are the REVIEW stage of the Jev Router pipeline: inspect → plan → execute → review.',
	'You did not write the changes under review; judge them independently against the original task and the executor\'s report.',
	'You have no editing tool: do not modify files.',
	'You may re-run read-only checks yourself (tests, `git diff`, `git status`) to confirm the executor\'s claims, but never run a command that changes the workspace and never commit.',
	'If you find problems, report them and stop — do not fix them and do not loop.',
	'Lead with findings.',
].join(' ')

/** Instructions (system side) for one stage. Static per stage except for the execute risk note. */
export function stageInstructions(stage: OrchestrateStage, decision: RouteDecision): string {
	switch (stage) {
		case 'inspect':
			return INSPECT_INSTRUCTIONS
		case 'plan':
			return PLAN_INSTRUCTIONS
		case 'execute':
			return decision.risk === 'normal' ? EXECUTE_INSTRUCTIONS : `${EXECUTE_INSTRUCTIONS} ${EXECUTE_RISK_NOTE}`
		case 'review':
			return REVIEW_INSTRUCTIONS
	}
}

export type StageMessageContext = {
	task: string
	decision: RouteDecision
	instructions?: string
	inspectReport?: string
	plan?: string
	execReport?: string
	diff?: string
	/**
	 * Compact project guidance (AGENTS.md excerpts or path hints) selected for this task. Only
	 * ever added to the read-only inspect/plan messages; it is a summary, never a full file.
	 */
	guidance?: string
}

/** A short, clearly-labelled guidance block; empty when no guidance was selected. */
function guidanceBlock(guidance?: string): string {
	const text = guidance?.trim()
	if (!text) return ''
	return `\n\nProject guidance selected for this task (summaries and paths only; read the listed files for the full text):\n${text}`
}

/** The user message (variable payload) for one stage. Carries only the task and prior reports. */
export function stageMessage(stage: OrchestrateStage, ctx: StageMessageContext): string {
	const extra = ctx.instructions?.trim() ? `\n\nAdditional instructions from the caller:\n${ctx.instructions.trim()}` : ''
	switch (stage) {
		case 'inspect':
			return (
				`Task to inspect:\n${ctx.task}\n\n` +
				'Return a compact, structured evidence package. Emit one JSON object inside a ```json fence with exactly these keys, each value an array of short strings (cite paths and line numbers; never paste file bodies):\n' +
				'{"files": [], "symbols": [], "constraints": [], "unknowns": [], "checks": [], "risks": []}\n' +
				'If you cannot produce JSON, fall back to these headings, one line per item:\n' +
				'## Relevant files\n- path — why it matters\n' +
				'## Key symbols / evidence\n- symbol — file:line — what it does\n' +
				'## Constraints\n- ...\n' +
				'## Unknowns\n- ...\n' +
				'Do not propose a plan and do not paste file bodies.' +
				guidanceBlock(ctx.guidance) +
				extra
			)
		case 'plan':
			return (
				`Original task:\n${ctx.task}\n\n` +
				`Inspection report (compact; may be truncated):\n${formatEvidencePackage(parseEvidencePackage(clip(ctx.inspectReport ?? '', STAGE_HANDOFF_CHARS)))}\n\n` +
				'Return an implementation plan with exactly these sections:\n' +
				'## Goal\n' +
				'## Steps\n1. exact file or area — action — expected result\n' +
				'## Verification\n- command or check that proves success\n' +
				'## Risks / open questions\n- ...\n' +
				'Name concrete files and symbols. Do not modify files.' +
				guidanceBlock(ctx.guidance) +
				extra
			)
		case 'execute':
			return (
				`Original task:\n${ctx.task}\n\n` +
				`Approved plan (follow it exactly; do not widen scope):\n${clip(ctx.plan ?? '', STAGE_HANDOFF_CHARS)}\n\n` +
				'When done, report with exactly these sections:\n' +
				'## Changes\n- file — what changed\n' +
				'## Verification\n- command — result\n' +
				'## Unresolved\n- ...'
			)
		case 'review':
			return (
				`Original task:\n${ctx.task}\n\n` +
				`Executor report:\n${clip(ctx.execReport ?? '', STAGE_HANDOFF_CHARS)}\n\n` +
				`Actual workspace diff captured by the plugin (may be truncated):\n${ctx.diff?.trim() ? clip(ctx.diff, DIFF_HANDOFF_CHARS) : '(no diff captured)'}\n\n` +
				'Review the changes independently and respond findings-first with exactly these sections:\n' +
				'## Findings\n- severity — file:line — problem — why it matters\n' +
				'## Verdict\n' +
				'Write exactly one of PASS, CHANGES_REQUIRED, or UNCERTAIN as the first word on its own line, then a one-sentence justification. ' +
				'Do not repeat the three options back, and do not put the word "fail" in a PASS justification.\n' +
				'Do not modify files. Report problems instead of fixing them.'
			)
	}
}

/** Short, hidden `agent.start` instruction telling the main agent to use the pipeline tool. */
export function orchestrateHint(decision: RouteDecision): string {
	const riskNote =
		decision.risk === 'normal' ? '' : ` It looks ${decision.risk}; confirm destructive steps with the user first.`
	return (
		`Jev Router: this prompt looks like multi-step or planning-shaped work (${describeDecision(decision)}). ` +
		'Coordinate it with the `jev_orchestrate_task` tool, passing the task plus the context it needs. ' +
		'That tool runs inspect → plan → execute → review serially so only one agent edits at a time; do not start ' +
		`child threads yourself.${riskNote}`
	)
}

type Env = Record<string, string | undefined>

function env(): Env | undefined {
	return (globalThis as { process?: { env?: Env } }).process?.env
}

function envFlag(name: string, fallback: boolean): boolean {
	const raw = env()?.[name]?.trim().toLowerCase()
	if (!raw) return fallback
	return raw !== '0' && raw !== 'false' && raw !== 'no' && raw !== 'off'
}

function errorMessage(error: unknown): string {
	return error instanceof Error ? error.message : String(error)
}

/**
 * Strip anything that could carry the TypeSafe credential out of text that will be shown in a
 * transcript, returned from a tool, or logged. `classify` never puts the key in an error message,
 * but a raw env value or a stray `Bearer …` must never reach a user-visible surface.
 */
export function redactSecrets(text: string): string {
	const key = env()?.TYPESAFE_API_KEY?.trim()
	const withoutKey = key ? text.split(key).join('[redacted]') : text
	return withoutKey.replace(/Bearer\s+\S+/gi, 'Bearer [redacted]')
}

/**
 * One TypeSafe Jev call, shared by the routing questions and the gate questions so the timeout,
 * HTTP-error, and abort handling exists once. The key is only ever sent in the Authorization
 * header and never appears in an error message.
 */
async function postJev(questions: unknown, state: string, options: { timeoutMs?: number } = {}): Promise<unknown> {
	const apiKey = env()?.TYPESAFE_API_KEY
	if (!apiKey) throw new Error('TYPESAFE_API_KEY is not set. Export it before using Jev Router.')

	const controller = new AbortController()
	const timeoutMs = options.timeoutMs ?? 15_000
	const timeout = setTimeout(() => controller.abort(), timeoutMs)
	try {
		const response = await fetch(TYPESAFE_ENDPOINT, {
			method: 'POST',
			headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
			body: JSON.stringify({ model: 'jev-latest', state, questions }),
			signal: controller.signal,
		})
		if (!response.ok) throw new Error(`TypeSafe API returned HTTP ${response.status}.`)
		return await response.json()
	} catch (error) {
		if (error instanceof Error && error.name === 'AbortError') {
			throw new Error(`TypeSafe request timed out after ${Math.round(timeoutMs / 1000)} seconds.`)
		}
		throw error
	} finally {
		clearTimeout(timeout)
	}
}

export async function classify(task: string, options: { timeoutMs?: number } = {}): Promise<RouteDecision> {
	return parseJevResponse(await postJev(QUESTIONS, task, options), task)
}

/**
 * Jev's structured questions for the `tool.call` gate.
 *
 * The Plugin API exposes no way to switch the model of a thread that is already running, so the
 * only decision Jev makes here is whether an action may run without asking the user first — the
 * founder's "should any command be run" permission query, made programmable. Routing still happens
 * where it can: on the subagents a thread spawns (see `SUBAGENT_PIN`).
 */
const SAFETY_QUESTIONS = {
	permission: {
		type: 'choice',
		instructions: 'May this action run without asking the user first?',
		criteria: {
			safe: 'Ordinary, reversible work inside the repository the user is already working on.',
			needs_confirmation: 'Wide-reaching, hard to reverse, or beyond what the user asked for.',
			unsafe: 'Could destroy data, expose credentials, or affect systems the user did not mention.',
		},
	},
	risk: {
		type: 'score',
		instructions: 'How much irreversible or out-of-scope damage could this action do?',
		criteria: ['None', 'Some, recoverable', 'Severe or unrecoverable'],
	},
} as const

const PERMISSIONS = ['safe', 'needs_confirmation', 'unsafe'] as const
export type ActionPermission = (typeof PERMISSIONS)[number]

export type ActionDecision = {
	permission: ActionPermission
	/** 0–2, on the same rubric as the routing complexity score. */
	risk: number
	confidence: number
}

/** Validate Jev's gate answers with the same strictness as the routing answers. */
export function parseSafetyResponse(value: unknown): ActionDecision {
	if (!isRecord(value) || !isRecord(value.answers)) {
		throw new Error('Jev returned an invalid response: answers are missing.')
	}
	const permission = answer(value.answers.permission, 'permission')
	const risk = answer(value.answers.risk, 'risk')
	if (permission.type !== 'choice' || !PERMISSIONS.includes(permission.choice as ActionPermission)) {
		throw new Error('Jev returned an invalid permission choice.')
	}
	if (risk.type !== 'score' || typeof risk.score !== 'number' || !Number.isFinite(risk.score)) {
		throw new Error('Jev returned an invalid risk score.')
	}
	if (risk.score < 0 || risk.score > 2) {
		throw new Error('Jev returned a risk score outside the configured rubric.')
	}
	const permissionConfidence = confidence(permission.confidence, 'permission.confidence')
	const riskConfidence = confidence(risk.confidence, 'risk.confidence')
	return {
		permission: permission.choice as ActionPermission,
		risk: risk.score,
		confidence: Math.min(permissionConfidence, riskConfidence),
	}
}

/** Ask Jev whether one intended action may run without asking the user first. */
export async function classifyAction(action: string, options: { timeoutMs?: number } = {}): Promise<ActionDecision> {
	return parseSafetyResponse(await postJev(SAFETY_QUESTIONS, action, options))
}

/** An agent handle narrowed to what the pipeline needs. Lets tests inject a fake. */
export type OrchestratorAgent = {
	/** Amp's resolved agent definition, when the runtime exposes it. */
	readonly definition?: { readonly kind?: string; readonly model?: unknown }
	run(
		message: string,
		options?: { timeoutMs?: number; parentThreadID?: ThreadID },
	): Promise<AgentRunResult>
}

export type PipelineDeps = {
	createAgent: (config: CreateAgentConfig) => OrchestratorAgent
	classify?: (task: string) => Promise<RouteDecision>
	captureDiff?: () => Promise<string>
	parentThreadID?: ThreadID
}

export type OrchestrateOptions = {
	task: string
	instructions?: string
	confirm?: boolean
	/** Pre-computed Jev decision; when omitted the pipeline classifies the task itself. */
	decision?: RouteDecision
	/** Cost/quality profile for stage models. Defaults to `JEV_ROUTER_PROFILE` then `balanced`. */
	profile?: CostProfile
	/** Pre-selected project guidance (AGENTS.md summaries/paths) added to inspect and plan. */
	guidance?: string
	deps: PipelineDeps
}

/**
 * Read the model Amp resolved into an agent's definition, when the Plugin API exposes it. For a
 * custom agent created with an explicit `model` this is the resolved `agent-definition.model`.
 * It is not a guarantee of the served provider model — Cost & Usage is the only place that shows
 * that — but a mismatch against the requested model is a real signal the pin was not honoured.
 */
export function readAgentModel(agent: { definition?: { kind?: string; model?: unknown } }): string | undefined {
	const definition = agent?.definition
	if (!definition || typeof definition !== 'object') return undefined
	const model = (definition as { model?: unknown }).model
	return typeof model === 'string' && model.trim().length > 0 ? model.trim() : undefined
}

/** Where a routing decision came from, so a result never presents a fallback as a Jev success. */
export type ClassificationSource = 'jev' | 'fallback' | 'caller' | 'unclassified'

export type ClassificationInfo = {
	source: ClassificationSource
	/** Why Jev could not classify, when `source` is `fallback`. Credentials are always redacted. */
	detail?: string
}

/** Human-readable provenance for a decision, used in tool results. */
export function classificationLabel(info: ClassificationInfo): string {
	switch (info.source) {
		case 'jev':
			return 'TypeSafe Jev classification'
		case 'caller':
			return 'caller-supplied decision (Jev not consulted)'
		case 'fallback':
			// "unavailable" would be wrong when Jev answered but was not confident enough; the detail
			// carries which of the two happened.
			return `local fallback — Jev could not classify (${info.detail ?? 'reason unknown'})`
		case 'unclassified':
			return 'not classified (the pipeline stopped before classification)'
	}
}

export type StageReport = {
	stage: OrchestrateStage
	/** Model the plugin pinned for this stage with `createAgent({ model })`. */
	requestedModel: RoutedModel
	/** Model Amp resolved into the agent definition, when the runtime exposes it. */
	agentModel?: string
	/** True when Amp's resolved agent model differs from the requested model. */
	modelMismatch: boolean
	reason: string
	threadID?: ThreadID
	report: string
}

export type OrchestrateStatus = 'completed' | 'blocked' | 'failed'

export type OrchestrateResult = {
	ok: boolean
	status: OrchestrateStatus
	reviewStatus?: ReviewStatus
	failedStage?: OrchestrateStage
	stages: StageReport[]
	/** Cost/quality profile that selected the stage models. */
	profile?: CostProfile
	/** Provenance of the routing decision, so a fallback is never reported as Jev success. */
	classification: ClassificationInfo
	message: string
}

/** Raised inside the pipeline when one stage fails, so the driver can name it and stop. */
export class StageError extends Error {
	readonly stage: OrchestrateStage
	override readonly cause?: unknown

	constructor(stage: OrchestrateStage, cause: unknown) {
		super(`The ${stage} stage failed: ${redactSecrets(errorMessage(cause))}`)
		this.name = 'StageError'
		this.stage = stage
		this.cause = cause
	}
}

function summarizeStage(stage: StageReport): string {
	const resolved =
		stage.agentModel === undefined
			? 'agent model not exposed by the Plugin API'
			: `agent definition ${stage.agentModel}${stage.modelMismatch ? ' ⚠ MISMATCH with the request' : ''}`
	return (
		`### ${stage.stage} — requested ${stage.requestedModel} · ${resolved}` +
		`${stage.threadID ? ` · thread ${stage.threadID}` : ''}\n${clip(stage.report, STAGE_SUMMARY_CHARS)}`
	)
}

function formatOrchestrateResult(result: OrchestrateResult): string {
	const header =
		result.status === 'completed'
			? `Jev Router pipeline completed. Review: ${result.reviewStatus ?? 'unknown'}.`
			: result.status === 'blocked'
				? 'Jev Router pipeline did not start.'
				: `Jev Router pipeline stopped at the ${result.failedStage ?? 'unknown'} stage.`
	const parts = [header, `Classification: ${classificationLabel(result.classification)}.`]
	if (result.profile) parts.push(`Profile: ${result.profile}.`)
	if (result.status === 'completed' || result.status === 'failed') {
		parts.push(result.stages.map(summarizeStage).join('\n\n'))
	}
	parts.push(
		'Each stage names the model the plugin requested and, when the Plugin API exposes it, the ' +
			'agent definition Amp resolved. The served provider model is only visible in Amp\'s Cost & Usage.',
	)
	parts.push(result.message)
	return parts.filter(Boolean).join('\n\n')
}

/**
 * Run the four-stage pipeline strictly serially. Each stage gets the task plus a compact report
 * from the previous stage — never the full sub-thread transcript — and they share the current
 * local workspace. A stage failure names the stage and stops the rest.
 */
export async function orchestrate(options: OrchestrateOptions): Promise<OrchestrateResult> {
	const task = options.task.trim()
	const stages: StageReport[] = []
	if (!task) {
		return {
			ok: false,
			status: 'failed',
			stages,
			classification: { source: 'unclassified' },
			message: 'Jev Router orchestration needs a non-empty task.',
		}
	}

	const risk = options.decision?.risk ?? detectRisk(task)
	if (risk === 'destructive' && options.confirm !== true) {
		return {
			ok: false,
			status: 'blocked',
			stages,
			profile: options.profile ?? profileFromEnv(),
			classification: options.decision ? { source: 'caller' } : { source: 'unclassified' },
			message:
				'This task looks destructive and no confirmation was given, so no stage ran. ' +
				'Get the user to confirm, then call `jev_orchestrate_task` again with `confirm: true`.',
		}
	}

	let decision = options.decision
	let classification: ClassificationInfo = options.decision
		? { source: 'caller' }
		: { source: 'unclassified' }
	if (!decision) {
		try {
			decision = await (options.deps.classify ?? classify)(task)
			classification = { source: 'jev' }
		} catch (error) {
			classification = { source: 'fallback', detail: redactSecrets(errorMessage(error)) }
			decision = fallbackDecision(task)
		}
	}

	const profile = options.profile ?? profileFromEnv()
	const stageModels = resolveStageModels(profile, decision)
	const inspect: StageModelChoice = { model: stageModels.models.inspect, reason: stageModels.reasons.inspect }
	const planner: StageModelChoice = { model: stageModels.models.plan, reason: stageModels.reasons.plan }
	const executor: StageModelChoice = { model: stageModels.models.execute, reason: stageModels.reasons.execute }
	const reviewer: StageModelChoice = { model: stageModels.models.review, reason: stageModels.reasons.review }

	const runStage = async (
		stage: OrchestrateStage,
		choice: StageModelChoice,
		instructions: string,
		message: string,
	): Promise<StageReport> => {
		try {
			const agent = options.deps.createAgent({
				name: `jev-orchestrate-${stage}-${MODEL_TAG[choice.model]}`,
				model: choice.model,
					reasoningEffort: MODEL_REASONING_EFFORT[choice.model],
				instructions,
				tools: STAGE_TOOLS[stage],
				subagents: subagentPin(),
				oracle: resolveOraclePin(),
				display: stageDisplay(stage, choice.model),
			})
			const result = await agent.run(message, {
				timeoutMs: STAGE_TIMEOUTS[stage],
				parentThreadID: options.deps.parentThreadID,
			})
			const agentModel = readAgentModel(agent)
			return {
				stage,
				requestedModel: choice.model,
				agentModel,
				modelMismatch: agentModel !== undefined && agentModel !== choice.model,
				reason: choice.reason,
				threadID: result.threadID,
				report: result.text,
			}
		} catch (error) {
			throw new StageError(stage, error)
		}
	}

	let inspectReport: string
	let plan: string
	let execReport: string
	let reviewReport: string
	try {
		const inspectStage = await runStage(
			'inspect',
			inspect,
			stageInstructions('inspect', decision),
			stageMessage('inspect', { task, instructions: options.instructions, decision, guidance: options.guidance }),
		)
		stages.push(inspectStage)
		inspectReport = inspectStage.report

		const planStage = await runStage(
			'plan',
			planner,
			stageInstructions('plan', decision),
			stageMessage('plan', { task, instructions: options.instructions, decision, inspectReport, guidance: options.guidance }),
		)
		stages.push(planStage)
		plan = planStage.report

		const executeStage = await runStage(
			'execute',
			executor,
			stageInstructions('execute', decision),
			stageMessage('execute', { task, decision, plan }),
		)
		stages.push(executeStage)
		execReport = executeStage.report

		let diff = ''
		if (options.deps.captureDiff) {
			try {
				diff = await options.deps.captureDiff()
			} catch {
				diff = ''
			}
		}

		const reviewStage = await runStage(
			'review',
			reviewer,
			stageInstructions('review', decision),
			stageMessage('review', { task, decision, execReport, diff }),
		)
		stages.push(reviewStage)
		reviewReport = reviewStage.report
	} catch (error) {
		if (error instanceof StageError) {
			const result: OrchestrateResult = {
				ok: false,
				status: 'failed',
				failedStage: error.stage,
				stages,
				profile,
				classification,
				message: `${error.message} Later stages were not started.`,
			}
			result.message = formatOrchestrateResult(result)
			return result
		}
		throw error
	}

	const reviewStatus = parseReviewVerdict(reviewReport)
	const result: OrchestrateResult = {
		ok: reviewStatus !== 'fail',
		status: 'completed',
		reviewStatus,
		stages,
		profile,
		classification,
		message: '',
	}
	result.message = formatOrchestrateResult(result)
	return result
}

/** Collect `git status`/`git diff` from the shared workspace for the review stage. */
async function captureWorkspaceDiff(amp: PluginAPI): Promise<string> {
	try {
		const status = await amp.$`git --no-pager status --porcelain`
		const diff = await amp.$`git --no-pager diff --no-color`
		const parts: string[] = []
		if (status.stdout.trim()) parts.push(`# git status --porcelain\n${status.stdout.trim()}`)
		if (diff.stdout.trim()) parts.push(`# git diff --no-color\n${diff.stdout.trim()}`)
		return parts.join('\n\n')
	} catch {
		return ''
	}
}

/** Display for a dispatched child thread, naming the requested model. */
function modelDisplay(model: RoutedModel): { label: string; color: string } {
	return { label: `Jev ${MODEL_LABEL[model]}`, color: MODEL_COLOR[model] }
}

const ROUTER_AGENT_INSTRUCTIONS = [
	'You are a worker thread dispatched by the Jev Router plugin.',
	'The calling thread classified this task with TypeSafe Jev and selected you for it.',
	'Work only on the dispatched task; do not widen the scope on your own.',
	'Keep the result self-contained: state the outcome, the evidence, and anything unresolved.',
].join(' ')

const ROUTER_COMMAND = 'jev-router-send'
const DISPATCH_TOOL = 'jev_dispatch_task'
const ORCHESTRATE_TOOL = 'jev_orchestrate_task'
const PARALLEL_INSPECT_TOOL = 'jev_parallel_inspect'
const MAX_TRACKED_THREADS = 512

/**
 * Thread IDs this plugin created, and threads already triaged by the `agent.start` hook.
 * `spawnedThreadIDs` is a second line of defence behind `parentThreadID()`: a dispatched child
 * must never be triaged again.
 */
const spawnedThreadIDs = new Set<string>()
const triagedThreadIDs = new Set<string>()

function rememberThread(store: Set<string>, id: string): void {
	store.add(id)
	while (store.size > MAX_TRACKED_THREADS) {
		const oldest = store.values().next().value
		if (oldest === undefined) break
		store.delete(oldest)
	}
}

export type DispatchMode = 'run' | 'thread'

export type DispatchResult = {
	/** Model the plugin pinned for this dispatch with `createAgent({ model })`. */
	requestedModel: RoutedModel
	/** Model Amp resolved into the agent definition, when the runtime exposes it. */
	agentModel?: string
	/** True when Amp's resolved agent model differs from the requested model. */
	modelMismatch: boolean
	mode: DispatchMode
	threadID?: ThreadID
	text: string
}

export type DispatchOptions = {
	task: string
	decision: RouteDecision
	mode?: DispatchMode
	parentThreadID?: ThreadID
	instructions?: string
}

function buildInstructions(decision: RouteDecision, extra?: string): string {
	const parts = [
		ROUTER_AGENT_INSTRUCTIONS,
		`Dispatch decision: ${describeDecision(decision)}.`,
	]
	if (decision.risk !== 'normal') {
		parts.push(
			`This task is classified ${decision.risk}: treat destructive, credential-touching, or ` +
				'production-affecting steps as confirmation-required, and report them instead of performing them.',
		)
	}
	if (extra?.trim()) parts.push(extra.trim())
	return parts.join('\n\n')
}

/**
 * Create one dedicated agent with the routed model and run it. The agent is created here, per
 * dispatch, with an explicit `model` and a model-named identity/display; the plugin never uses a
 * built-in-mode agent handle and never inherits a mode's model.
 */
export async function dispatch(amp: PluginAPI, options: DispatchOptions): Promise<DispatchResult> {
	const { decision, task } = options
	const mode = options.mode ?? 'run'
	const agent = amp.createAgent({
		name: `jev-router-${decision.route}-${MODEL_TAG[decision.model]}`,
		model: decision.model,
		reasoningEffort: MODEL_REASONING_EFFORT[decision.model],
		instructions: buildInstructions(decision, options.instructions),
		tools: 'all',
		// Dispatched work follows the same role policy as every other agent this plugin creates:
		// retrieval on the fast model, judgement on Astra.
		subagents: subagentPin(),
		oracle: resolveOraclePin(),
		display: modelDisplay(decision.model),
	})
	const agentModel = readAgentModel(agent)
	const modelMismatch = agentModel !== undefined && agentModel !== decision.model

	if (mode === 'thread') {
		const thread = await agent.createThread({ parentThreadID: options.parentThreadID, show: true })
		rememberThread(spawnedThreadIDs, thread.id)
		await thread.appendUserMessage({ type: 'user-message', content: task })
		return {
			requestedModel: decision.model,
			agentModel,
			modelMismatch,
			mode,
			threadID: thread.id,
			text: `Started ${decision.model} in thread ${thread.id}.`,
		}
	}

	const result = await agent.run(task, {
		parentThreadID: options.parentThreadID,
		timeoutMs: DEFAULT_DISPATCH_TIMEOUT_MS,
	})
	return { requestedModel: decision.model, agentModel, modelMismatch, mode, threadID: result.threadID, text: result.text }
}

/**
 * One-line provenance for a pinned model: what was requested, what Amp resolved into the agent
 * definition, and where the served model can actually be checked.
 */
export function describeModelPin(requestedModel: string, agentModel?: string, modelMismatch = false): string {
	const resolved =
		agentModel === undefined
			? 'Amp did not expose the resolved agent model to this plugin'
			: `Amp resolved agent definition: ${agentModel}${modelMismatch ? ' ⚠ MISMATCH' : ''}`
	return `requested model: ${requestedModel}; ${resolved} (the served provider model is only visible in Amp's Cost & Usage)`
}

type AgentStartEvent = PluginEventMap['agent.start']
type AgentStartContext = PluginEventContext<'agent.start'>

/**
 * The `agent.start` hook stays advisory. On a prompt that is obviously complex or
 * planning-shaped it injects one short, hidden instruction telling the *current* thread to call
 * `jev_orchestrate_task`; it never creates a child thread, so it can never put a second editor
 * next to the main agent. Non-complex prompts never touch Jev.
 */
async function autoTriage(
	amp: PluginAPI,
	event: AgentStartEvent,
	ctx: AgentStartContext,
): Promise<AgentStartResult> {
	// `agent.start` is a request event: returning a result is mandatory, so "do nothing" is the
	// empty result rather than `undefined`.
	if (!envFlag('JEV_ROUTER_AUTO_TRIAGE', true)) return {}
	if (triagedThreadIDs.has(event.thread.id)) return {}
	if (spawnedThreadIDs.has(event.thread.id)) return {}
	// Dispatched children, subagents, and Task workers always carry a parent: never re-triage them.
	if (await ctx.thread.parentThreadID().catch(() => null)) return {}

	const task = event.message?.trim()
	if (!task || !looksComplex(task)) return {}
	rememberThread(triagedThreadIDs, event.thread.id)

	let decision: RouteDecision
	let classification: ClassificationInfo
	try {
		decision = await classify(task, { timeoutMs: AUTO_TRIAGE_TIMEOUT_MS })
		classification = { source: 'jev' }
	} catch (error) {
		classification = { source: 'fallback', detail: redactSecrets(errorMessage(error)) }
		ctx.logger.log('Jev Router hook fell back to local signals:', classification.detail)
		decision = fallbackDecision(task)
	}

	if (triagePlan(decision) === 'none') return {}
	return {
		message: {
			content: `Jev Router classification: ${classificationLabel(classification)}. ${orchestrateHint(decision)}`,
			display: false,
		},
	}
}

/** Tools that can change the workspace or reach outside it. Readers are never gated. */
const MUTATING_TOOLS = new Set(['shell_command', 'edit_file', 'create_file', 'apply_patch'])

/**
 * Each pattern captures the offending token in group 1 so the hold message can name it. The
 * leading character class matters: `cat .git/config` and `cat .env` are as sensitive as
 * `/repo/.env`, and a command is the common way an agent reaches them.
 *
 * The edit's file list is authoritative — those are real paths — so it accepts a bare extension
 * (`/repo/.key`).
 */
const SENSITIVE_FILE_PATTERNS: readonly RegExp[] = [
	/(?:^|[\s/])(\.env\b)/,
	/(?:^|[\s/])(\.git\/)/,
	/(?:^|[\s/])([\w@+.-]*\.(?:pem|key|p12|keystore|jks))\b/i,
	/(?:^|[\s/])((?:credentials|secrets?)\.)/i,
]

/**
 * The same names inside a command's *unquoted* text. A filename stem is required here: a bare
 * `.key` in a command is far more often a JSON field (`jq '.key'`) or prose than a key file, and
 * that single pattern was responsible for most of the gate's false holds.
 */
const SENSITIVE_COMMAND_PATTERNS: readonly RegExp[] = [
	/(?:^|[\s/])(\.env\b)/,
	/(?:^|[\s/])(\.git\/)/,
	/(?:^|[\s/])([\w@+.-]*[\w@+-]\.(?:pem|key|p12|keystore|jks))\b/i,
	/(?:^|[\s/])((?:credentials|secrets?)\.)/i,
]

/**
 * A command with its quoted spans blanked out: what it *asks for*, as opposed to the data it
 * carries. A path an agent is about to read is an argument (`cat .git/config`), while the holds
 * users complained about were quoted payloads — `jq '.key'`, an `rg` pattern naming a credential,
 * a `--data '{"files":["/repo/.env"]}'` probe. Deliberately not used for the destructive check:
 * `bash -c "rm -rf /"` is quoted and still has to hold.
 */
export function unquotedText(command: string): string {
	return command.replace(/'[^']*'/g, ' ').replace(/"[^"]*"/g, ' ')
}

/** The sensitive token an action reaches for, or undefined when it reaches for none. */
function sensitivePath(files: readonly string[], command?: string): string | undefined {
	for (const file of files) {
		for (const pattern of SENSITIVE_FILE_PATTERNS) {
			const match = file.match(pattern)
			if (match?.[1]) return match[1]
		}
	}
	if (command) {
		const text = unquotedText(command)
		for (const pattern of SENSITIVE_COMMAND_PATTERNS) {
			const match = text.match(pattern)
			if (match?.[1]) return match[1]
		}
	}
	return undefined
}

/** One line describing what a tool call is about to do, for Jev and for the log. */
function describeAction(input: { tool: string; command?: string; files?: readonly string[] }): string {
	const parts = [input.tool]
	if (input.command) parts.push(input.command)
	const files = input.files ?? []
	if (files.length > 0) {
		const shown = files.slice(0, 10).join(', ')
		parts.push(`touches ${shown}${files.length > 10 ? ` (+${files.length - 10} more)` : ''}`)
	}
	return parts.join(': ')
}

export type GateDecision = { action: 'allow' } | { action: 'reject-and-continue'; message: string }

/**
 * Whether one intended action may run. Pure, so the policy is unit-testable without a network call
 * or an Amp runtime.
 *
 * Order matters. The deterministic signals decide first, so the gate still holds a `rm -rf` when
 * Jev is unreachable or has no key, and Jev can only ever add a hold, never lift one. An
 * infrastructure failure therefore fails open — the session keeps working — while an explicit risk
 * signal fails closed.
 */
export function gateAction(input: {
	tool: string
	command?: string
	files?: readonly string[]
	safety?: ActionDecision
}): GateDecision {
	const description = describeAction(input)
	// The command text is scanned too: `filesModifiedByToolCall` cannot know that `cat .git/config`
	// reaches repository internals, and the check is free either way. Quoted spans are treated as
	// data, not as the thing being reached for.
	const riskyPath = sensitivePath(input.files ?? [], input.command)
	if (riskyPath) {
		return {
			action: 'reject-and-continue',
			message:
				`Jev Router held this action because it touches ${riskyPath}, which usually carries credentials or ` +
				`repository internals: ${description}. Ask the user to confirm before running it.`,
		}
	}
	// Only the destructive half reads the raw command text, because a quoted destructive command is
	// still destructive (`bash -c "rm -rf /"`). Sensitive *topic words* are deliberately left to
	// Jev's judgment instead of a text scan: holding on `password` / `credential` / `payment`
	// anywhere in the text is what made this gate block ordinary inspection — an `rg` pattern, a
	// patch that mentions a credentials file, a JSON probe — and a hold the user learns to click
	// through is worse than no hold at all.
	if (DESTRUCTIVE_PATTERNS.some((pattern) => pattern.test(description))) {
		return {
			action: 'reject-and-continue',
			message:
				`Jev Router held this action because it looks destructive: ${description}. ` +
				'Ask the user to confirm before running it, and say what you would do instead.',
		}
	}
	if (input.safety && input.safety.permission !== 'safe') {
		// A guess must not be able to hold up work. Below the floor the plugin does not act on the
		// answer at all: the deterministic checks above still applied, and an unreachable or unsure
		// Jev is not evidence of danger. This is the same "do not act on a guess" rule the routing
		// path uses, applied in the cheaper direction — acting on a routing guess wastes money,
		// acting on a gate guess wastes the user's time and trains them to click through holds.
		if (input.safety.confidence < MIN_CONFIDENCE) return { action: 'allow' }
		return {
			action: 'reject-and-continue',
			message:
				`Jev classified this action as ${input.safety.permission} (risk ${input.safety.risk.toFixed(1)}/2, ` +
				`confidence ${(input.safety.confidence * 100).toFixed(0)}%): ${description}. ` +
				'Ask the user to confirm before running it.',
		}
	}
	return { action: 'allow' }
}

/**
 * The `tool.call` gate: the hook this plugin exists for. At the moment the agent is about to act,
 * the deterministic guard decides for free, and Jev is consulted only in the narrow case where a
 * shell command is about to run and nothing local flagged it. Readers are never gated, so ordinary
 * work adds no latency and no Jev cost.
 */
async function gateToolCall(
	amp: PluginAPI,
	event: ToolCall,
	ctx: PluginEventContext<'tool.call'>,
): Promise<ToolCallResult> {
	if (!MUTATING_TOOLS.has(event.tool)) return { action: 'allow' }
	const input = {
		tool: event.tool,
		command: amp.helpers.shellCommandFromToolCall(event)?.command,
		// `filesModifiedByToolCall` answers with file URIs, not paths; the sensitive-path patterns
		// are written against local paths (`cat .git/config`, `~/.env`), so convert them with the
		// documented helper. Without this the deterministic half of the gate silently sees
		// `file://…` strings instead of the paths it was written for.
		files: (amp.helpers.filesModifiedByToolCall(event) ?? []).map((uri) => amp.helpers.filePathFromURI(uri)),
	}
	if (!input.command && input.files.length === 0) return { action: 'allow' }

	// Decide locally first: a command the deterministic guard already holds must not also cost a
	// Jev call.
	const local = gateAction({ tool: input.tool, command: input.command, files: input.files })
	if (local.action !== 'allow') return local

	let safety: ActionDecision | undefined
	// Only shell commands are worth a Jev call. File edits are already visible in the diff and are
	// usually what the user asked for, and a Jev request per edit would cost more than it saves.
	if (envFlag('JEV_ROUTER_GATE', true) && event.tool === 'shell_command' && input.command) {
		try {
			safety = await classifyAction(describeAction(input), { timeoutMs: GATE_TIMEOUT_MS })
		} catch (error) {
			// Fail open: an unreachable Jev must not brick the session. The deterministic checks in
			// `gateAction` already ran.
			ctx.logger.log('Jev gate could not classify:', redactSecrets(errorMessage(error)))
		}
	}
	return safety === undefined ? { action: 'allow' } : gateAction({ ...input, safety })
}

export default function (amp: PluginAPI) {
	amp.on('agent.start', async (event, ctx) => {
		try {
			return await autoTriage(amp, event, ctx)
		} catch (error) {
			// The hook must never break a turn.
			ctx.logger.log('Jev Router auto triage failed:', redactSecrets(errorMessage(error)))
			return {}
		}
	})

	// The `tool.call` gate. It is a request event, so returning a result is mandatory; any plugin
	// bug fails open rather than blocking the agent mid-turn.
	amp.on('tool.call', async (event, ctx) => {
		try {
			return await gateToolCall(amp, event, ctx)
		} catch (error) {
			ctx.logger.log('Jev Router tool.call gate failed:', redactSecrets(errorMessage(error)))
			return { action: 'allow' }
		}
	})

	// The `tool.result` compressor. It is a request event; the handler is synchronous and
	// deterministic, with one optional, short Jev relevance call, and it fails open so an
	// oversized output is at worst left as-is. It never generates text and never drops errors.
	amp.on('tool.result', async (event, ctx) => {
		try {
			return await handleToolResult(amp, event, ctx)
		} catch (error) {
			ctx.logger.log('Jev Router tool.result compression failed:', redactSecrets(errorMessage(error)))
			return
		}
	})

	// The `agent.end` auto-review. It returns at most one bounded `continue` (or fires a
	// non-blocking read-only background thread) and never loops. Any failure fails open.
	amp.on('agent.end', async (event, ctx) => {
		try {
			return await handleAgentEnd(amp, event, ctx)
		} catch (error) {
			ctx.logger.log('Jev Router auto review failed:', redactSecrets(errorMessage(error)))
			return
		}
	})

	amp.registerTool({
		name: PARALLEL_INSPECT_TOOL,
		title: 'Jev: parallel inspect',
		transcriptGroup: { active: 'Inspecting in parallel with Jev', complete: 'Inspected in parallel with Jev' },
		description:
			'Run several independent, read-only evidence questions in parallel on the fast verified model. ' +
			'Use it when the questions do not depend on one another; each runs in its own read-only child thread ' +
			'(no editing tool) with a bounded concurrency, and one failing question never fails the others. ' +
			'Write/execute work stays serial — this tool only reads and reports evidence.',
		inputSchema: {
			type: 'object',
			properties: {
				questions: {
					type: 'array',
					items: { type: 'string' },
					description:
						'Independent, read-only evidence questions. Keep each self-contained; they run in parallel and ' +
						`cannot see one another. At most ${PARALLEL_INSPECT_MAX_QUESTIONS} are run.`,
				},
				maxConcurrency: {
					type: 'number',
					description: `Parallel child threads to allow (1-${PARALLEL_INSPECT_MAX_CONCURRENCY}; default ${PARALLEL_INSPECT_DEFAULT_CONCURRENCY}).`,
				},
			},
			required: ['questions'],
		},
		async execute(input, ctx) {
			const raw = Array.isArray(input.questions) ? input.questions : []
			const questions = raw.filter((q): q is string => typeof q === 'string' && q.trim().length > 0)
			if (questions.length === 0) return 'jev_parallel_inspect needs a non-empty "questions" array of strings.'
			const maxConcurrency = typeof input.maxConcurrency === 'number' ? input.maxConcurrency : undefined
			try {
				const summary = await parallelInspect(
					{
						createAgent: (config) => amp.createAgent(config),
						parentThreadID: ctx.thread.id,
						maxConcurrency,
					},
					questions,
				)
				return formatParallelInspect(summary)
			} catch (error) {
				const message = redactSecrets(errorMessage(error))
				ctx.logger.log('Jev parallel inspect failed:', message)
				return `Jev Router parallel inspect failed: ${message}`
			}
		},
	})

	amp.registerTool({
		name: ORCHESTRATE_TOOL,
		title: 'Jev: orchestrate task',
		transcriptGroup: { active: 'Orchestrating with Jev', complete: 'Orchestrated with Jev' },
		description:
			'Run a self-contained task through Jev Router\'s serial four-stage pipeline: inspect (fast, read-only ' +
			'evidence), plan (Jev-routed Sol/Astra), execute (fast model, or Sol for sensitive/destructive work), ' +
			'review (a senior model different from the executor). Each stage shares this workspace and reports the ' +
			'model it requested, the agent definition Amp resolved when the Plugin API exposes it, its thread ID, ' +
			'and a compact report, plus whether Jev or a local fallback produced the routing decision. A failing ' +
			'stage stops the rest and is named in the result. An optional cost/quality profile maps the stage ' +
			'models but can never lower the safety floor for sensitive or destructive work.',
		inputSchema: {
			type: 'object',
			properties: {
				task: {
					type: 'string',
					description:
						'Self-contained brief for the pipeline: the goal plus the context a stage needs to act, ' +
						'since stages do not see this thread\'s transcript.',
				},
				instructions: {
					type: 'string',
					description: 'Extra instructions appended to the inspect and plan stages.',
				},
				confirm: {
					type: 'boolean',
					description: 'Required when the task looks destructive; without it no stage runs.',
				},
				profile: {
					type: 'string',
					enum: [...COST_PROFILES],
					description:
						'Cost/quality profile for the stage models (economy, balanced, quality, sensitive). ' +
						'Defaults to JEV_ROUTER_PROFILE, then balanced. Never bypasses the sensitive/destructive floor.',
				},
				paths: {
					type: 'array',
					items: { type: 'string' },
					description:
						'Optional repo-relative paths the task touches, used to select relevant AGENTS.md guidance.',
				},
			},
			required: ['task'],
		},
		async execute(input, ctx) {
			const task = typeof input.task === 'string' ? input.task.trim() : ''
			if (!task) return 'jev_orchestrate_task needs a non-empty "task" string.'
			const instructions = typeof input.instructions === 'string' ? input.instructions : undefined
			const confirm = input.confirm === true
			const profile = parseProfile(typeof input.profile === 'string' ? input.profile.trim().toLowerCase() : undefined)
			const paths = Array.isArray(input.paths)
				? input.paths.filter((p): p is string => typeof p === 'string' && p.trim().length > 0)
				: []
			const guidance = await collectGuidance(amp, [...paths, ...pathHintsFromTask(task)])

			let result: OrchestrateResult
			try {
				result = await orchestrate({
					task,
					instructions,
					confirm,
					profile,
					guidance,
					deps: {
						createAgent: (config) => amp.createAgent(config),
						classify,
						captureDiff: () => captureWorkspaceDiff(amp),
						parentThreadID: ctx.thread.id,
					},
				})
			} catch (error) {
				const message = redactSecrets(errorMessage(error))
				ctx.logger.log('Jev orchestration failed:', message)
				return `Jev Router pipeline failed before it could report a stage: ${message}`
			}

			if (result.status !== 'completed') {
				ctx.logger.log('Jev orchestration stopped:', result.status, result.failedStage ?? '')
			}
			return result.message
		},
	})

	amp.registerTool({
		name: DISPATCH_TOOL,
		title: 'Jev: dispatch task',
		transcriptGroup: { active: 'Routing with Jev', complete: 'Routed with Jev' },
		description:
			'Classify a task with TypeSafe Jev and dispatch it to a verified Amp model in a child thread. ' +
			'Use it for self-contained work that is worth delegating; low-confidence, unclassified, and ' +
			'destructive tasks come back with a reason instead of a thread. The result states the requested ' +
			'model and the agent definition Amp resolved when the Plugin API exposes it.',
		inputSchema: {
			type: 'object',
			properties: {
				task: {
					type: 'string',
					description:
						'Self-contained brief for the routed agent, including the context it needs to act alone.',
				},
				mode: {
					type: 'string',
					enum: ['run', 'thread'],
					description:
						"'run' waits for the routed agent and returns its answer (default); 'thread' starts a " +
						'visible child thread and returns its ID.',
				},
				confirm: {
					type: 'boolean',
					description: 'Required when Jev classifies the task as destructive.',
				},
				instructions: {
					type: 'string',
					description: 'Extra instructions appended to the routed agent prompt.',
				},
			},
			required: ['task'],
		},
		async execute(input, ctx) {
			const task = typeof input.task === 'string' ? input.task.trim() : ''
			if (!task) return 'jev_dispatch_task needs a non-empty "task" string.'
			const mode: DispatchMode = input.mode === 'thread' ? 'thread' : 'run'
			const instructions = typeof input.instructions === 'string' ? input.instructions : undefined

			let decision: RouteDecision
			try {
				decision = await classify(task)
			} catch (error) {
				const message = redactSecrets(errorMessage(error))
				ctx.logger.log('Jev dispatch did not run:', message)
				if (error instanceof LowConfidenceError) {
					return (
						`Jev confidence is too low (${(error.confidence * 100).toFixed(0)}%), so Jev Router did not ` +
						'dispatch. Handle the task in this thread, or split it into a narrower task and call ' +
						'`jev_dispatch_task` again. Classification: local fallback was not used.'
					)
				}
				return `Jev Router did not dispatch: ${message} Handle the task in this thread instead.`
			}

			if (decision.risk === 'destructive' && input.confirm !== true) {
				return (
					`Jev classified this task as destructive, so no thread was created. Decision: ${describeDecision(decision)}. ` +
					'Get the user to confirm, then call `jev_dispatch_task` again with `confirm: true`.'
				)
			}

			const parentThreadID = (await ctx.thread.parentThreadID().catch(() => null)) ?? undefined
			try {
				const result = await dispatch(amp, { decision, task, mode, parentThreadID, instructions })
				const pin = describeModelPin(result.requestedModel, result.agentModel, result.modelMismatch)
				if (mode === 'thread') {
					return (
						`Jev Router started ${result.requestedModel} in thread ${result.threadID}. ` +
						`Decision: ${describeDecision(decision)}. ${pin}.`
					)
				}
				return `Jev Router ran ${result.requestedModel}. Decision: ${describeDecision(decision)}. ${pin}.\n\n${result.text}`
			} catch (error) {
				const message = redactSecrets(errorMessage(error))
				ctx.logger.log('Jev dispatch failed:', message)
				return `Jev dispatch failed after routing to ${decision.model}: ${message}`
			}
		},
	})

	amp.registerCommand(
		ROUTER_COMMAND,
		{
			title: 'Send',
			category: 'Jev Router',
			description: 'Classify a task with TypeSafe Jev and dispatch it to a routed model.',
		},
		async (ctx) => {
			const task = (await ctx.ui.input({
				title: 'Jev Router: Send',
				helpText: 'Jev classifies this task; it does not generate text.',
				submitButtonText: 'Classify and send',
			}))?.trim()
			if (!task) return

			try {
				const decision = await classify(task)
				const summary = `Jev route: ${decision.route}; complexity: ${decision.complexity.toFixed(1)}; ` +
					`long context: ${decision.needsLongContext ? 'yes' : 'no'}; tools: ${decision.needsTools ? 'yes' : 'no'}; ` +
					`risk: ${decision.risk}; confidence: ${(decision.confidence * 100).toFixed(0)}%; ` +
					`requested model: ${decision.model} (${decision.reason}); ` +
					`advisory built-in mode: ${decision.mode} (not the model).`
				await ctx.ui.notify(summary)

				if (!(await ctx.ui.confirm({ title: 'Dispatch routed task?', message: summary, confirmButtonText: 'Dispatch' }))) return
				const result = await dispatch(amp, {
					decision,
					task,
					mode: 'thread',
					parentThreadID: ctx.thread?.id,
				})
				await ctx.ui.notify(
					`Started ${result.requestedModel} in thread ${result.threadID}. ` +
						describeModelPin(result.requestedModel, result.agentModel, result.modelMismatch),
				)
			} catch (error) {
				const message = redactSecrets(errorMessage(error))
				await ctx.ui.notify(`Jev Router failed: ${message}`)
			}
		},
	)

	// Register each verified model as a first-class, model-pinned mode via `createAgent({ model })`
	// (no built-in-mode agent handle, no mode inheritance). Selecting one of these modes creates a
	// normal user thread whose served model is visible in Cost & Usage, which is the reliable way
	// to check whether Amp honours the plugin's model pin. Keep the `// @amp-agent-mode` directives
	// at the top of this file in sync with `AGENT_MODES`.
	for (const mode of AGENT_MODES) {
		const modeAgent = amp.createAgent({
			name: mode.key,
			model: mode.model,
				reasoningEffort: MODEL_REASONING_EFFORT[mode.model],
			// Whatever model this mode runs on, the subagents it spawns read and search on Flash.
			subagents: subagentPin(),
			oracle: resolveOraclePin(),
			instructions: [
				`You are the ${mode.label} mode, pinned by the Jev Router plugin to ${mode.model}.`,
				'Stay within the work the user gave you, and report what you did and what you verified.',
			].join(' '),
			tools: 'all',
			display: { label: mode.label, color: MODEL_COLOR[mode.model] },
		})
		amp.registerAgentMode({
			key: mode.key,
			label: mode.label,
			description: mode.description,
			color: MODEL_COLOR[mode.model],
			agent: modeAgent.definition,
		})
	}
}

// ===========================================================================
// tool.result: deterministic, fail-open compression of oversized tool output
//
// The `tool.result` hook cannot start a complex agent and must never break a
// turn, so the policy is code: a pure plan decides whether an output is worth
// touching, a deterministic compressor owns the result, and Jev is asked at
// most one structured relevance question that can only move the character
// budget between fixed bounds. Jev never writes text, and error lines are
// preserved before anything else is kept.
// ===========================================================================

export type ToolResultKind = 'diff' | 'logs' | 'search' | 'file' | 'generic'

export type CompressionPlan = {
	kind: ToolResultKind
	chars: number
	/** Deterministic default; Jev may only move the effective budget inside [minChars, maxChars]. */
	budgetChars: number
	minChars: number
	maxChars: number
	/** True when the output is large enough that a relevance judgment is worth one Jev call. */
	jevEligible: boolean
	/** False for short/known-safe outputs: they are returned untouched with no Jev call. */
	compress: boolean
	reason: string
}

const DIFF_STRUCTURE = /^(diff --git |index |new file mode |deleted file mode |--- |\+\+\+ |@@ )/
const TOOL_ERROR_LINE =
	/\b(error|errors|failed|failure|failing|fatal|panic|exception|traceback|assert(?:ion)?\s+failed|not ok)\b|(?:^|\s)[✗✘]|(?:^|\s)FAILED\b/i
const TEST_OR_LOG_COMMAND =
	/\b(test|tests|pytest|vitest|jest|gradle|maven|mvn|cargo|tsc|lint|build|bun\s+test|npm|pnpm|yarn)\b/i
const SEARCH_TOOLS = new Set(['rg', 'grep', 'search', 'search_files', 'find'])

/** Editor tools whose presence in a turn means the turn changed code. */
export const WRITE_TOOL_NAMES = new Set(['edit_file', 'create_file', 'apply_patch', 'multiedit', 'write'])

function countSearchShapedLines(lines: readonly string[]): number {
	let count = 0
	for (const line of lines) {
		if (/^(?:\.{0,2}\/)?[^\s:]+:\d+:/.test(line)) count += 1
		if (count >= 3) break
	}
	return count
}

/** Classify an output so the compressor can pick a structure-preserving strategy. */
export function detectToolResultKind(tool: string, input: Record<string, unknown>, text: string): ToolResultKind {
	const command = typeof input.command === 'string' ? input.command : ''
	if (tool === 'Read' || tool === 'read_file') return 'file'
	if (DIFF_STRUCTURE.test(text) || /\bdiff\b/.test(command)) return 'diff'
	if (TEST_OR_LOG_COMMAND.test(command) || /\bTraceback \(most recent call last\)/.test(text)) return 'logs'
	if (SEARCH_TOOLS.has(tool) || /\b(rg|grep)\b/.test(command) || countSearchShapedLines(text.split('\n')) >= 3) {
		return 'search'
	}
	return 'generic'
}

/**
 * Decide, purely, whether an output should be compressed. Short outputs never are, so an
 * ordinary `ls` or a small file read costs nothing and reaches no network.
 */
export function planToolResultCompression(
	tool: string,
	input: Record<string, unknown>,
	text: string,
): CompressionPlan {
	const chars = text.length
	const kind = detectToolResultKind(tool, input, text)
	const over = chars > TOOL_RESULT_SAFE_CHARS
	return {
		kind,
		chars,
		budgetChars: TOOL_RESULT_DEFAULT_CHARS,
		minChars: TOOL_RESULT_MIN_CHARS,
		maxChars: TOOL_RESULT_MAX_CHARS,
		jevEligible: over,
		compress: over,
		reason: over
			? `${kind} output of ${chars} characters exceeds the ${TOOL_RESULT_SAFE_CHARS}-character safe passthrough`
			: `output of ${chars} characters is short enough to pass through untouched`,
	}
}

/** Jev's structured relevance questions for `tool.result`; it judges, it never writes. */
const RELEVANCE_QUESTIONS = {
	signal: {
		type: 'choice',
		instructions: 'For the next engineering step, how much of this tool output is essential signal rather than noise?',
		criteria: {
			essential: 'Nearly all of it carries information the next step needs (for example a compact failing-test report).',
			mixed: 'Some lines matter, but much of it is repetition, passing output, or unchanged context.',
			noise: 'Almost all of it is redundant, unchanged context, or passing output with no errors.',
		},
	},
	signal_ratio: {
		type: 'score',
		instructions: 'What share of this output is signal rather than noise?',
		criteria: ['Mostly noise', 'Mixed', 'Mostly signal'],
	},
} as const

export type RelevanceSignal = 'essential' | 'mixed' | 'noise'
export type RelevanceDecision = { signal: RelevanceSignal; score: number; confidence: number }
const RELEVANCE_SIGNALS = ['essential', 'mixed', 'noise'] as const

/** Validate Jev's relevance answers with the same strictness as the routing answers. */
export function parseRelevanceResponse(value: unknown): RelevanceDecision {
	if (!isRecord(value) || !isRecord(value.answers)) {
		throw new Error('Jev returned an invalid response: answers are missing.')
	}
	const signal = answer(value.answers.signal, 'signal')
	const ratio = answer(value.answers.signal_ratio, 'signal_ratio')
	if (signal.type !== 'choice' || !RELEVANCE_SIGNALS.includes(signal.choice as RelevanceSignal)) {
		throw new Error('Jev returned an invalid relevance signal.')
	}
	if (ratio.type !== 'score' || typeof ratio.score !== 'number' || !Number.isFinite(ratio.score)) {
		throw new Error('Jev returned an invalid relevance score.')
	}
	if (ratio.score < 0 || ratio.score > 2) {
		throw new Error('Jev returned a relevance score outside the configured rubric.')
	}
	return {
		signal: signal.choice as RelevanceSignal,
		score: ratio.score,
		confidence: Math.min(confidence(signal.confidence, 'signal.confidence'), confidence(ratio.confidence, 'signal_ratio.confidence')),
	}
}

/** Ask Jev one structured relevance question about a large output. */
export async function classifyRelevance(state: string, options: { timeoutMs?: number } = {}): Promise<RelevanceDecision> {
	return parseRelevanceResponse(await postJev(RELEVANCE_QUESTIONS, state, options))
}

/** The compact state sent to Jev: metadata plus a clipped excerpt, never the whole output. */
export function buildRelevanceState(tool: string, plan: CompressionPlan, text: string): string {
	return JSON.stringify({
		tool,
		kind: plan.kind,
		chars: plan.chars,
		excerpt: clip(text, TOOL_RESULT_JEV_STATE_CHARS),
	})
}

/**
 * Fold a relevance judgment into a character budget. Code owns the result: a low-confidence
 * answer changes nothing, and every branch stays inside the plan's fixed bounds.
 */
export function adjustBudgetWithRelevance(plan: CompressionPlan, relevance: RelevanceDecision): number {
	if (relevance.confidence < MIN_CONFIDENCE) return plan.budgetChars
	const clamp = (value: number) => Math.min(plan.maxChars, Math.max(plan.minChars, Math.round(value)))
	if (relevance.signal === 'noise') return clamp(plan.budgetChars * 0.6)
	if (relevance.signal === 'essential') return plan.maxChars
	if (relevance.score >= 2) return clamp(plan.budgetChars * 1.3)
	return plan.budgetChars
}

/**
 * Deterministically shrink a text to at most `budget` characters while keeping, in priority
 * order: every line that looks like an error, diff/search structure, the head, and the tail.
 * Nothing here calls a model, so it cannot produce a summary that drops a failure.
 *
 * The caller is `tool.result`, so this only ever *removes* surrounding noise; it never invents
 * text. Error lines are selected first, so an error can only be dropped when the errors alone
 * exceed the whole budget (in which case the marker says how many were kept).
 */
export function compressToolResultText(text: string, kind: ToolResultKind, budget: number): string {
	if (text.length <= budget) return text
	const lines = text.split('\n')
	const total = lines.length
	const critical = lines.map((line) => TOOL_ERROR_LINE.test(line))
	const structural = kind === 'diff' ? lines.map((line) => DIFF_STRUCTURE.test(line)) : lines.map(() => false)
	const mustKeep = (index: number) => critical[index] || structural[index]
	const mustChars = lines.reduce((sum, line, index) => (mustKeep(index) ? sum + line.length + 1 : sum), 0)

	if (mustChars >= budget) {
		const kept: string[] = []
		let used = 0
		for (let index = 0; index < total && used < budget; index += 1) {
			if (!mustKeep(index)) continue
			kept.push(lines[index])
			used += lines[index].length + 1
		}
		kept.push(`…[Jev Router kept ${kept.length} error/structure lines; ${total} lines / ${text.length} characters total]`)
		return kept.join('\n')
	}

	const remaining = budget - mustChars
	const headBudget = Math.floor(remaining * 0.6)
	const tailBudget = remaining - headBudget
	const headCount = kind === 'file' ? 60 : 40
	const tailCount = kind === 'file' ? 60 : 40
	const out: string[] = []
	let elided = 0
	let headUsed = 0
	let tailUsed = 0
	const flushElided = () => {
		if (elided > 0) {
			out.push(`…[${elided} lines elided]`)
			elided = 0
		}
	}
	for (let index = 0; index < total; index += 1) {
		const line = lines[index]
		const cost = line.length + 1
		if (mustKeep(index)) {
			flushElided()
			out.push(line)
			continue
		}
		if (index < headCount && headUsed < headBudget) {
			const room = headBudget - headUsed
			flushElided()
			if (cost <= room) {
				out.push(line)
				headUsed += cost
			} else {
				// A single oversized line (a minified file, one-line JSON) is clipped, not kept whole.
				out.push(`${line.slice(0, Math.max(0, room))}…`)
				headUsed = headBudget
			}
			continue
		}
		if (index >= total - tailCount && tailUsed < tailBudget) {
			const room = tailBudget - tailUsed
			flushElided()
			if (cost <= room) {
				out.push(line)
				tailUsed += cost
			} else {
				out.push(`…${line.slice(-Math.max(0, room))}`)
				tailUsed = tailBudget
			}
			continue
		}
		elided += 1
	}
	flushElided()
	const body = out.join('\n')
	out.push(`…[Jev Router compressed ${text.length} → ${body.length} characters (${kind})]`)
	return out.join('\n')
}

/**
 * The `tool.result` handler. Returns `undefined` (no change) for anything that is not a
 * completed, oversized, string output, for the deterministic plan's short outputs, and for any
 * failure. Only a `done` result is touched, so an error result keeps its full message.
 */
async function handleToolResult(
	amp: PluginAPI,
	event: PluginEventMap['tool.result'],
	ctx: PluginEventContext<'tool.result'>,
): Promise<ToolResultResult> {
	if (event.status !== 'done') return
	const text = typeof event.output === 'string' ? event.output : undefined
	if (!text) return
	const plan = planToolResultCompression(event.tool, event.input, text)
	if (!plan.compress) return

	let budget = plan.budgetChars
	if (plan.jevEligible && envFlag('JEV_ROUTER_TOOL_RESULT', true) && env()?.TYPESAFE_API_KEY) {
		try {
			const relevance = await classifyRelevance(buildRelevanceState(event.tool, plan, text), {
				timeoutMs: TOOL_RESULT_JEV_TIMEOUT_MS,
			})
			budget = adjustBudgetWithRelevance(plan, relevance)
		} catch (error) {
			// Fail open: the deterministic compressor still runs with the default budget.
			ctx.logger.log('Jev Router tool.result relevance skipped:', redactSecrets(errorMessage(error)))
		}
	}

	const compressed = compressToolResultText(text, plan.kind, budget)
	if (compressed === text) return
	ctx.logger.log(
		`Jev Router compressed ${event.tool} output ${text.length} → ${compressed.length} characters (${plan.kind}).`,
	)
	return { status: 'done', output: compressed }
}

// ===========================================================================
// Structured evidence package (inspect → plan/execute handoff)
//
// inspect is asked for a JSON object with fixed fields; the handoff parses,
// validates, and truncates it so a later stage gets explicit fields instead of
// a wall of prose. A malformed or missing package degrades to a clipped raw
// excerpt, never to a silent empty handoff.
// ===========================================================================

export type EvidencePackage = {
	files: string[]
	symbols: string[]
	constraints: string[]
	unknowns: string[]
	checks: string[]
	risks: string[]
	/** True when a JSON object with at least one expected field was parsed. */
	parsed: boolean
	/** The original text, clipped, kept as a fallback and for provenance. */
	raw: string
}

export const EVIDENCE_KEYS = ['files', 'symbols', 'constraints', 'unknowns', 'checks', 'risks'] as const
export const EVIDENCE_MAX_ITEMS = 24
export const EVIDENCE_MAX_ITEM_CHARS = 300

function singleLine(text: string, max: number): string {
	const flat = text.replace(/\s+/g, ' ').trim()
	return flat.length <= max ? flat : `${flat.slice(0, max)}…`
}

function firstString(record: Record<string, unknown>, keys: readonly string[]): string | undefined {
	for (const key of keys) {
		const value = record[key]
		if (typeof value === 'string' && value.trim()) return value.trim()
	}
	return undefined
}

function evidenceItem(value: unknown): string | undefined {
	if (typeof value === 'string') return value.trim() ? singleLine(value, EVIDENCE_MAX_ITEM_CHARS) : undefined
	if (isRecord(value)) {
		const name = firstString(value, ['path', 'symbol', 'name', 'file', 'check', 'item'])
		const where = firstString(value, ['file', 'location', 'path', 'line'])
		const why = firstString(value, ['why', 'reason', 'note', 'detail', 'what', 'summary'])
		const parts = [name, where && where !== name ? where : undefined, why].filter(
			(part): part is string => Boolean(part),
		)
		if (parts.length > 0) return singleLine(parts.join(' — '), EVIDENCE_MAX_ITEM_CHARS)
	}
	return undefined
}

function normalizeEvidenceList(value: unknown): string[] {
	if (!Array.isArray(value)) return []
	const out: string[] = []
	for (const item of value) {
		const text = evidenceItem(item)
		if (text && !out.includes(text)) out.push(text)
		if (out.length >= EVIDENCE_MAX_ITEMS) break
	}
	return out
}

/** Pull the first JSON object out of a text, preferring a ```json fence. */
export function extractJsonObject(text: string): unknown {
	const fence = text.match(/```json\s*([\s\S]*?)```/i)
	const candidates: string[] = []
	if (fence?.[1]) candidates.push(fence[1])
	const start = text.indexOf('{')
	const end = text.lastIndexOf('}')
	if (start >= 0 && end > start) candidates.push(text.slice(start, end + 1))
	for (const candidate of candidates) {
		try {
			const parsed: unknown = JSON.parse(candidate)
			if (isRecord(parsed)) return parsed
		} catch {
			// try the next candidate
		}
	}
	return undefined
}

/** Parse and bound the inspect stage's evidence package. Never throws. */
export function parseEvidencePackage(text: string, maxRaw = STAGE_HANDOFF_CHARS): EvidencePackage {
	const raw = clip(text ?? '', maxRaw)
	const empty: EvidencePackage = {
		files: [],
		symbols: [],
		constraints: [],
		unknowns: [],
		checks: [],
		risks: [],
		parsed: false,
		raw,
	}
	const json = extractJsonObject(text ?? '')
	if (!json || !isRecord(json)) return empty
	if (!EVIDENCE_KEYS.some((key) => key in json)) return empty
	const out: EvidencePackage = { ...empty, parsed: true }
	for (const key of EVIDENCE_KEYS) out[key] = normalizeEvidenceList(json[key])
	return out
}

/** Render a parsed package compactly, or a labelled raw excerpt when parsing failed. */
export function formatEvidencePackage(pkg: EvidencePackage): string {
	if (!pkg.parsed) {
		return `(inspect did not return a JSON evidence package; raw excerpt follows)\n${pkg.raw}`
	}
	const sections: Array<[string, string[]]> = [
		['Relevant files', pkg.files],
		['Key symbols', pkg.symbols],
		['Constraints', pkg.constraints],
		['Unknowns', pkg.unknowns],
		['Checks', pkg.checks],
		['Risks', pkg.risks],
	]
	const rendered = sections
		.filter(([, items]) => items.length > 0)
		.map(([title, items]) => `## ${title}\n${items.map((item) => `- ${item}`).join('\n')}`)
	return rendered.length > 0 ? rendered.join('\n') : '(the evidence package was empty)'
}

// ===========================================================================
// Conditional project guidance (AGENTS.md / CLAUDE.md)
//
// The inspect and plan messages get *relevant* guidance only: a ranked, capped
// selection of a few known guidance paths, summarised to headless headings and
// a couple of content lines, with secret-looking lines dropped. When no file
// API is available the fallback is a path hint, so inspect can read the file
// itself rather than the plugin injecting anything.
// ===========================================================================

/** Directory part of a repo-relative path, or '' for a root-level file. */
export function guidanceDir(path: string): string {
	const index = path.lastIndexOf('/')
	return index < 0 ? '' : path.slice(0, index)
}

/** Cheap path hints for guidance selection: path-like tokens and bare file names. */
export function pathHintsFromTask(task: string): string[] {
	const matches = task.match(/[\w@.-]*(?:\/[\w@.-]+)+|\b[\w-]+\.[\w-]{1,8}\b/g) ?? []
	const cleaned = matches
		.map((match) => match.replace(/^\.\//, '').replace(/^\/+/, ''))
		.filter((match) => match.length > 1 && !match.includes('..'))
	return [...new Set(cleaned)]
}

/** Relevant score for one guidance file against the task's path hints. Root guidance always scores at least 1. */
export function guidanceScore(path: string, hints: readonly string[]): number {
	const dir = guidanceDir(path)
	let best = dir === '' ? 1 : 0
	for (const hint of hints) {
		const normalized = hint.replace(/^\.\//, '')
		if (!normalized) continue
		if (dir === '' || normalized === dir || normalized.startsWith(`${dir}/`)) {
			best = Math.max(best, dir === '' ? 1 : dir.split('/').length + 1)
		} else if (divergentTopLevel(dir, normalized)) {
			best = Math.max(best, 0)
		} else {
			best = Math.max(best, 1)
		}
	}
	return best
}

function divergentTopLevel(dir: string, hint: string): boolean {
	if (dir === '') return false
	return dir.split('/')[0] !== hint.split('/')[0]
}

/** Rank guidance files by relevance to the hints, then by depth and path for stability. */
export function rankGuidanceFiles(available: readonly string[], hints: readonly string[]): string[] {
	const files = [...new Set(available.map((path) => path.trim()).filter(Boolean))]
	return files
		.map((path) => ({ path, score: guidanceScore(path, hints), depth: path.split('/').length }))
		.sort((a, b) => b.score - a.score || b.depth - a.depth || a.path.localeCompare(b.path))
		.map((entry) => entry.path)
}

/** The few guidance files worth putting in front of a stage, in relevance order. */
export function selectGuidanceFiles(
	available: readonly string[],
	hints: readonly string[],
	max = MAX_GUIDANCE_FILES,
): string[] {
	return rankGuidanceFiles(available, hints).slice(0, Math.max(1, max))
}

function isSecretishLine(line: string): boolean {
	return (
		/(secret|api[_-]?key|password|passphrase|token|private key|BEGIN [A-Z ]*PRIVATE)/i.test(line) &&
		/[:=]\s*\S{8,}/.test(line)
	)
}

/** Heads + a couple of content lines per section, secret-looking lines dropped, then clipped. */
export function summarizeGuidance(text: string, maxChars = MAX_GUIDANCE_FILE_CHARS): string {
	const kept: string[] = []
	for (const raw of text.split('\n')) {
		const line = raw.trimEnd()
		if (!line.trim()) continue
		if (isSecretishLine(line)) continue
		if (/^#{1,6}\s/.test(line)) {
			kept.push(line)
			continue
		}
		if (kept.length < 12) kept.push(line)
	}
	return clip(redactSecrets(kept.join('\n')), maxChars)
}

/** Fallback when file reading is unavailable: list the relevant paths and let inspect read them. */
export function guidancePathHint(files: readonly string[]): string {
	if (files.length === 0) return ''
	return `Relevant project guidance files (read them yourself with Read):\n${files.map((path) => `- ${path}`).join('\n')}`
}

/**
 * Read the repository's guidance files through the plugin shell, rank them against the hints, and
 * return a compact block (summaries when readable, path hints otherwise). Fail-open and bounded:
 * anything unexpected yields an empty string and the stage runs with no guidance.
 */
export async function collectGuidance(amp: PluginAPI, hints: readonly string[]): Promise<string> {
	try {
		const shell = (amp as { $?: unknown }).$
		if (typeof shell !== 'function') return guidancePathHint(selectGuidanceFiles(KNOWN_GUIDANCE_PATHS, hints))
		const listed = await listGuidanceFiles(amp)
		const files = selectGuidanceFiles(listed.length > 0 ? listed : KNOWN_GUIDANCE_PATHS, hints)
		if (files.length === 0) return ''
		const blocks: string[] = []
		let used = 0
		for (const path of files) {
			if (used >= MAX_GUIDANCE_CHARS) break
			let excerpt = ''
			try {
				const result = await amp.$`cat ${path}`
				if (result.exitCode === 0) {
					excerpt = summarizeGuidance(result.stdout, Math.min(MAX_GUIDANCE_FILE_CHARS, MAX_GUIDANCE_CHARS - used))
				}
			} catch {
				excerpt = ''
			}
			const block = [`- ${path}`, excerpt].filter(Boolean).join('\n')
			blocks.push(block)
			used += block.length
		}
		return blocks.join('\n')
	} catch {
		return guidancePathHint(selectGuidanceFiles(KNOWN_GUIDANCE_PATHS, hints))
	}
}

/** List guidance files with ripgrep first (it respects ignores), falling back to git then find. */
async function listGuidanceFiles(amp: PluginAPI): Promise<string[]> {
	const attempts: Array<() => Promise<{ exitCode: number; stdout: string }>> = [
		() => amp.$`rg --files -g AGENTS.md -g CLAUDE.md`,
		() => amp.$`git ls-files '*AGENTS.md' '*CLAUDE.md'`,
		() => amp.$`find . -maxdepth 6 -name AGENTS.md`,
	]
	for (const attempt of attempts) {
		try {
			const result = await attempt()
			if (result.exitCode === 0 && result.stdout.trim()) {
				return result.stdout
					.split('\n')
					.map((line) => line.trim())
					.filter(Boolean)
			}
		} catch {
			// try the next strategy
		}
	}
	return []
}

// ===========================================================================
// agent.end: bounded, non-blocking, read-only auto review
//
// The API's `agent.end` result is a single `continue` (or nothing), so this
// path is deliberately small: it decides from the finished turn whether one
// read-only review is worth it, dedupes per turn, caps per thread, and never
// loops. The default is a background child thread whose model comes from
// `autoReviewModel` — the senior coding model (or one escalated off the parent's
// model), never the fast model unless `JEV_ROUTER_AUTO_REVIEW_MODEL` pins it —
// which the API runs independently of this handler, so the handler does not
// block the turn; `continue` mode instead asks the current thread for one
// bounded pass (and therefore reviews on the current thread's own model).
// ===========================================================================

export type AutoReviewMode = 'off' | 'thread' | 'continue'

/** Threads already given an auto review and how many each has had. Both are bounded. */
const reviewTurns = new Set<string>()
const reviewContinuations = new Map<string, number>()

/** Read the `JEV_ROUTER_AUTO_REVIEW` switch. Default: one background read-only review thread. */
export function parseAutoReviewMode(raw: string | undefined): AutoReviewMode {
	const value = raw?.trim().toLowerCase()
	if (!value) return 'thread'
	if (value === '0' || value === 'false' || value === 'no' || value === 'off') return 'off'
	if (value === 'continue') return 'continue'
	return 'thread'
}

/** Per-thread auto-review cap from `JEV_ROUTER_AUTO_REVIEW_MAX` (default 1, clamped to 0-5). */
export function autoReviewMax(raw: string | undefined = env()?.JEV_ROUTER_AUTO_REVIEW_MAX): number {
	const parsed = Number.parseInt(raw ?? '', 10)
	if (!Number.isFinite(parsed) || parsed < 0) return AUTO_REVIEW_DEFAULT_MAX
	return Math.min(parsed, 5)
}

/** Parse a verified model from either its short tag (`flash`/`sol`/`astra`) or its full model id. */
export function parseRoutedModel(raw: string | undefined): RoutedModel | undefined {
	const value = raw?.trim().toLowerCase()
	if (!value) return undefined
	for (const model of Object.values(VERIFIED_MODELS) as RoutedModel[]) {
		if (value === model.toLowerCase() || value === MODEL_TAG[model]) return model
	}
	return undefined
}

export type AutoReviewModelChoice = { model: RoutedModel; reason: string }

/**
 * Which model the read-only review observer runs on.
 *
 * The doc intent is *cross-model* review — a model that did not produce the turn. The Plugin API
 * exposes the parent's resolved model only for custom agents (`thread.agent().definition.model`);
 * an ordinary user thread runs a built-in mode and carries only a mode key. So, in order:
 * - `JEV_ROUTER_AUTO_REVIEW_MODEL` wins whenever it names a verified model;
 * - a known parent model escalates the reviewer off it, reusing the pipeline's own `reviewModel`
 *   rule so the reviewer is never the model that produced the work;
 * - an unknown parent model falls back to the senior coding model, never the fast one.
 */
export function autoReviewModel(
	input: { parentModel?: string; requested?: string } = {},
): AutoReviewModelChoice {
	const explicit = parseRoutedModel(input.requested)
	if (explicit) return { model: explicit, reason: 'pinned by JEV_ROUTER_AUTO_REVIEW_MODEL' }
	const parent = parseRoutedModel(input.parentModel)
	if (parent === undefined) {
		return {
			model: VERIFIED_MODELS.sol,
			reason:
				"the parent thread's model is not exposed for built-in modes, so the reviewer defaults to the senior coding model rather than the fast one",
		}
	}
	return reviewModel({ executor: parent, planner: parent, risk: 'normal' })
}

/**
 * The parent thread's resolved model, when the API exposes it. Custom agents carry
 * `definition.model`; built-in modes carry only a mode key, so this returns `undefined` for an
 * ordinary user thread and `autoReviewModel` applies its default. Never throws: a review that
 * cannot read the thread must still be able to start.
 */
export async function parentThreadModel(ctx: { thread?: unknown }): Promise<string | undefined> {
	try {
		const thread = ctx.thread as { agent?: () => Promise<unknown> } | undefined
		if (typeof thread?.agent !== 'function') return undefined
		return readAgentModel((await thread.agent()) as { definition?: { kind?: string; model?: unknown } })
	} catch {
		return undefined
	}
}

export type AutoReviewInput = {
	status: 'done' | 'error' | 'cancelled'
	mode: AutoReviewMode
	hasCodeChanges: boolean
	hasParent: boolean
	alreadyReviewed: boolean
	continuations: number
	maxContinuations: number
}

export type AutoReviewPlan = { action: 'none' | 'thread' | 'continue'; reason: string }

/**
 * Pure gate for the auto review. Every reason to do nothing comes first, so a turn only triggers
 * a review when it finished cleanly, changed code, is not a child, has not been reviewed, and is
 * under its per-thread cap.
 */
export function planAutoReview(input: AutoReviewInput): AutoReviewPlan {
	if (input.mode === 'off') return { action: 'none', reason: 'JEV_ROUTER_AUTO_REVIEW is off' }
	if (input.status !== 'done') return { action: 'none', reason: `the turn ended with status ${input.status}` }
	if (input.hasParent) return { action: 'none', reason: 'this is a child or subagent thread (recursion guard)' }
	if (!input.hasCodeChanges) return { action: 'none', reason: 'the turn made no code changes' }
	if (input.alreadyReviewed) return { action: 'none', reason: 'this turn was already reviewed' }
	if (input.continuations >= input.maxContinuations) {
		return { action: 'none', reason: `the per-thread review cap is reached (${input.maxContinuations})` }
	}
	return {
		action: input.mode,
		reason: input.mode === 'continue' ? 'one bounded read-only review turn' : 'a background read-only review thread',
	}
}

/** Pure: did this turn's messages contain an editor tool call? */
export function turnHasCodeChanges(messages: readonly ThreadMessage[]): boolean {
	for (const message of messages) {
		if (message.role !== 'assistant') continue
		for (const block of message.content) {
			if (block.type === 'tool_use' && WRITE_TOOL_NAMES.has(block.name)) return true
		}
	}
	return false
}

export const AUTO_REVIEW_INSTRUCTIONS = [
	'You are a read-only review observer started by the Jev Router plugin after a turn that changed code.',
	'You never edit files and never commit; if you find a defect you report it and stop.',
	'Re-run the cheapest existing check that covers the change, then judge the change against what was asked.',
	'Lead with findings; do not start another review and do not loop.',
].join(' ')

/** The concrete read-only pass the continue mode asks the current thread to run. */
export function autoReviewMessage(): string {
	return [
		'Jev Router: the previous turn finished and changed code. Do one bounded, read-only review pass before stopping:',
		'1. Re-run the most relevant existing check (the repo test command, a focused type check, or a targeted test).',
		'2. Report findings first: correctness, edge cases, regressions, and anything the change did not verify.',
		'3. Do not edit files in this pass, do not commit, and do not start another review.',
		'If you find a real defect, state it and stop; the user decides what to do next.',
	].join('\n')
}

function boundReviewContinuations(): void {
	while (reviewContinuations.size > MAX_TRACKED_THREADS) {
		const oldest = reviewContinuations.keys().next().value
		if (oldest === undefined) break
		reviewContinuations.delete(oldest)
	}
}

async function withTimeout<T>(promise: Promise<T>, ms: number, label: string): Promise<T> {
	let timer: ReturnType<typeof setTimeout> | undefined
	try {
		return await Promise.race([
			promise,
			new Promise<T>((_, reject) => {
				timer = setTimeout(() => reject(new Error(`${label} timed out after ${ms}ms`)), ms)
			}),
		])
	} finally {
		if (timer) clearTimeout(timer)
	}
}

/** Start the non-blocking read-only review thread. Model choice comes from `autoReviewModel`. */
async function startBackgroundReview(
	amp: PluginAPI,
	parentThreadID: ThreadID,
	ctx: PluginEventContext<'agent.end'>,
	choice: AutoReviewModelChoice = autoReviewModel(),
): Promise<ThreadID> {
	const model = choice.model
	const agent = amp.createAgent({
		name: `jev-review-observer-${MODEL_TAG[model]}`,
		model,
		reasoningEffort: MODEL_REASONING_EFFORT[model],
		instructions: AUTO_REVIEW_INSTRUCTIONS,
		tools: [...READ_ONLY_STAGE_TOOLS],
		subagents: subagentPin(),
		oracle: resolveOraclePin(),
		display: { label: `Jev Review · ${MODEL_LABEL[model]}`, color: MODEL_COLOR[model] },
	})
	const thread = await withTimeout(
		agent.createThread({ parentThreadID, show: false }),
		AUTO_REVIEW_TIMEOUT_MS,
		'Jev Router review thread creation',
	)
	rememberThread(spawnedThreadIDs, thread.id)
	await thread.appendUserMessage({ type: 'user-message', content: autoReviewMessage() })
	ctx.logger.log(`Jev Router started a read-only review observer in thread ${thread.id} on ${model}.`)
	return thread.id
}

/**
 * The `agent.end` handler. Books the review *before* the side effect so a duplicate event can
 * never review the same turn twice, and fails open on every error. The default background thread
 * is created through the Plugin API's own background-thread call; the handler waits only on that
 * bounded API call, never on the review's work.
 */
async function handleAgentEnd(
	amp: PluginAPI,
	event: PluginEventMap['agent.end'],
	ctx: PluginEventContext<'agent.end'>,
): Promise<AgentEndResult> {
	const mode = parseAutoReviewMode(env()?.JEV_ROUTER_AUTO_REVIEW)
	const threadID = String(event.thread.id)
	const turnKey = `${threadID}:${String(event.id)}`
	if (mode === 'off') return

	const parent = await ctx.thread.parentThreadID().catch(() => null)
	const plan = planAutoReview({
		status: event.status,
		mode,
		hasCodeChanges: turnHasCodeChanges(event.messages),
		hasParent: Boolean(parent),
		alreadyReviewed: reviewTurns.has(turnKey),
		continuations: reviewContinuations.get(threadID) ?? 0,
		maxContinuations: autoReviewMax(),
	})
	if (plan.action === 'none') return

	rememberThread(reviewTurns, turnKey)
	reviewContinuations.set(threadID, (reviewContinuations.get(threadID) ?? 0) + 1)
	boundReviewContinuations()

	if (plan.action === 'continue') {
		return { action: 'continue', userMessage: autoReviewMessage(), maxContinuations: 1 }
	}

	const choice = autoReviewModel({
		parentModel: await parentThreadModel(ctx),
		requested: env()?.JEV_ROUTER_AUTO_REVIEW_MODEL,
	})
	ctx.logger.log(`Jev Router auto review on ${MODEL_TAG[choice.model]}: ${choice.reason}`)

	try {
		await startBackgroundReview(amp, event.thread.id, ctx, choice)
	} catch (error) {
		// Fail open: a review that cannot start must not fail the finished turn.
		ctx.logger.log('Jev Router background review could not start:', redactSecrets(errorMessage(error)))
	}
	return
}

// ===========================================================================
// Parallel read-only exploration (jev_parallel_inspect)
//
// Independent evidence questions fan out to sibling createAgent runs at a
// bounded concurrency. Every child is read-only (no editing tool), pinned to
// the fast model, and isolated: one failure is reported per question and never
// fails the others. Write/execute work stays serial — this tool only reads.
// ===========================================================================

export type ParallelInspectDeps = {
	createAgent: (config: CreateAgentConfig) => OrchestratorAgent
	parentThreadID?: ThreadID
	maxConcurrency?: number
}

export type ParallelInspectResult = {
	id: string
	question: string
	ok: boolean
	threadID?: ThreadID
	report?: string
	error?: string
}

export type ParallelInspectSummary = {
	model: RoutedModel
	concurrency: number
	results: ParallelInspectResult[]
	ok: boolean
}

const PARALLEL_INSPECT_INSTRUCTIONS = [
	'You answer one independent, read-only evidence question for the Jev Router plugin.',
	'You have no editing tool: never create, edit, move, or remove a file.',
	'You may run read-only shell commands for evidence but never a command that changes the workspace, and never commit.',
	'Return a compact evidence package: relevant files, key symbols with file:line, constraints, unknowns, and any read-only check you ran.',
].join(' ')

/**
 * Run `worker` over `items` with a fixed concurrency, preserving input order. The worker is
 * expected to catch its own failures (see `parallelInspect`); this helper only bounds fan-out.
 */
export async function runWithConcurrency<T, R>(
	items: readonly T[],
	limit: number,
	worker: (item: T, index: number) => Promise<R>,
): Promise<R[]> {
	const results = new Array<R>(items.length)
	if (items.length === 0) return results
	const width = Math.max(1, Math.min(Math.floor(limit) || 1, items.length))
	let next = 0
	const runners = Array.from({ length: width }, async () => {
		for (;;) {
			const index = next
			next += 1
			if (index >= items.length) return
			results[index] = await worker(items[index], index)
		}
	})
	await Promise.all(runners)
	return results
}

/**
 * Fan out independent read-only evidence questions. Duplicate and empty questions are dropped,
 * the count and concurrency are capped, and each question is isolated so one failure is reported
 * without failing the rest.
 */
export async function parallelInspect(
	deps: ParallelInspectDeps,
	questions: readonly string[],
	model: RoutedModel = VERIFIED_MODELS.flash,
): Promise<ParallelInspectSummary> {
	const cleaned: string[] = []
	for (const question of questions) {
		const trimmed = question?.trim?.() ?? ''
		if (!trimmed) continue
		if (cleaned.includes(trimmed)) continue
		cleaned.push(trimmed)
		if (cleaned.length >= PARALLEL_INSPECT_MAX_QUESTIONS) break
	}
	const requested = deps.maxConcurrency ?? PARALLEL_INSPECT_DEFAULT_CONCURRENCY
	const concurrency = Math.max(1, Math.min(Math.floor(requested) || 1, PARALLEL_INSPECT_MAX_CONCURRENCY, cleaned.length || 1))

	const results = await runWithConcurrency(cleaned, concurrency, async (question, index): Promise<ParallelInspectResult> => {
		const id = `q${index + 1}`
		try {
			const agent = deps.createAgent({
				name: `jev-parallel-inspect-${MODEL_TAG[model]}-${id}`,
				model,
				reasoningEffort: MODEL_REASONING_EFFORT[model],
				instructions: PARALLEL_INSPECT_INSTRUCTIONS,
				tools: [...READ_ONLY_STAGE_TOOLS],
				subagents: subagentPin(),
				oracle: resolveOraclePin(),
				display: { label: `Jev Inspect · ${MODEL_LABEL[model]}`, color: MODEL_COLOR[model] },
			})
			const result = await agent.run(
				`Read-only evidence question:\n${question}\n\nReturn a compact evidence package citing paths and line numbers instead of pasting code.`,
				{ timeoutMs: PARALLEL_INSPECT_TIMEOUT_MS, parentThreadID: deps.parentThreadID },
			)
			return { id, question, ok: true, threadID: result.threadID, report: clip(result.text, STAGE_SUMMARY_CHARS) }
		} catch (error) {
			return { id, question, ok: false, error: redactSecrets(errorMessage(error)) }
		}
	})

	return { model, concurrency, results, ok: results.every((result) => result.ok) }
}

/** Render the structured summary: one block per question, failures named rather than hidden. */
export function formatParallelInspect(summary: ParallelInspectSummary): string {
	const header =
		`Jev Router ran ${summary.results.length} read-only evidence question(s) in parallel on ${summary.model} ` +
		`(max concurrency ${summary.concurrency}); ${summary.ok ? 'all succeeded' : 'some failed and are labelled below'}.`
	const blocks = summary.results.map((result) =>
		result.ok
			? `### ${result.id} · thread ${result.threadID}\n${result.report ?? ''}`
			: `### ${result.id} · FAILED\n${result.error ?? 'unknown error'}`,
	)
	return [header, ...blocks].join('\n\n')
}

// ===========================================================================
// Cost/quality profiles
//
// A profile maps the four pipeline stages to verified models. It is a
// preference, not a permission: deterministic code enforces a safety floor
// afterwards so no profile can put sensitive or destructive execution on the
// fast model, plan/review destructive work cheaply, or let the reviewer equal
// the executor. `balanced` is exactly the original risk-driven behaviour.
// ===========================================================================

export const COST_PROFILES = ['economy', 'balanced', 'quality', 'sensitive'] as const
export type CostProfile = (typeof COST_PROFILES)[number]

const TIER_ORDER: RoutedModel[] = [VERIFIED_MODELS.flash, VERIFIED_MODELS.sol, VERIFIED_MODELS.astra]

/** 0 = fast, 1 = coding, 2 = deep reasoning. */
export function modelTier(model: RoutedModel): number {
	return Math.max(0, TIER_ORDER.indexOf(model))
}

function tierAt(rank: number): RoutedModel {
	return TIER_ORDER[Math.max(0, Math.min(TIER_ORDER.length - 1, rank))]
}

export function isCostProfile(value: unknown): value is CostProfile {
	return typeof value === 'string' && (COST_PROFILES as readonly string[]).includes(value)
}

export function parseProfile(value: unknown): CostProfile | undefined {
	return isCostProfile(value) ? value : undefined
}

export function profileFromEnv(): CostProfile {
	return parseProfile(env()?.JEV_ROUTER_PROFILE?.trim().toLowerCase()) ?? 'balanced'
}

export type StageModelPlan = {
	models: Record<OrchestrateStage, RoutedModel>
	reasons: Record<OrchestrateStage, string>
}

/** Enforce the safety floor that every profile shares. Risk only ever raises a tier. */
function enforceSafety(plan: StageModelPlan, decision: RouteDecision): void {
	if (decision.risk === 'destructive') {
		if (modelTier(plan.models.execute) < 1) {
			plan.models.execute = VERIFIED_MODELS.sol
			plan.reasons.execute = 'destructive work never runs on the fast model'
		}
		if (modelTier(plan.models.plan) < 2) {
			plan.models.plan = VERIFIED_MODELS.astra
			plan.reasons.plan = 'destructive work is planned on the deep-reasoning model'
		}
		if (modelTier(plan.models.review) < 2) {
			plan.models.review = VERIFIED_MODELS.astra
			plan.reasons.review = 'destructive work is reviewed on the deep-reasoning model'
		}
	}
	if (decision.risk === 'sensitive' && modelTier(plan.models.execute) < 1) {
		plan.models.execute = VERIFIED_MODELS.sol
		plan.reasons.execute = 'sensitive work must not run on the fast model'
	}
	if (plan.models.review === plan.models.execute) {
		plan.models.review = plan.models.execute === VERIFIED_MODELS.astra ? VERIFIED_MODELS.sol : VERIFIED_MODELS.astra
		plan.reasons.review = 'the reviewer must differ from the executor'
	}
}

/**
 * Map a profile to the four stage models. Everything is derived from `VERIFIED_MODELS`; the
 * profile can only move tiers, and `enforceSafety` runs last so the sensitive/destructive floor
 * holds for every profile. `balanced` reproduces the original risk-driven choices unchanged.
 */
export function resolveStageModels(profile: CostProfile, decision: RouteDecision): StageModelPlan {
	const inspect = inspectModel()
	const planner = planModel(decision)
	const executor = executeModel(decision)
	const reviewer = reviewModel({ executor: executor.model, planner: planner.model, risk: decision.risk })
	const plan: StageModelPlan = {
		models: {
			inspect: inspect.model,
			plan: planner.model,
			execute: executor.model,
			review: reviewer.model,
		},
		reasons: {
			inspect: inspect.reason,
			plan: planner.reason,
			execute: executor.reason,
			review: reviewer.reason,
		},
	}

	if (profile === 'economy') {
		plan.models.plan = tierAt(Math.min(modelTier(plan.models.plan), 1))
		plan.reasons.plan = 'economy caps the planner at the coding model unless risk requires more'
		plan.models.review = tierAt(Math.min(modelTier(plan.models.review), 1))
		plan.reasons.review = 'economy caps the reviewer at the coding model unless risk requires more'
	} else if (profile === 'quality') {
		plan.models.plan = tierAt(Math.max(modelTier(plan.models.plan), 2))
		plan.reasons.plan = 'quality plans on the deep-reasoning model'
		plan.models.execute = tierAt(Math.max(modelTier(plan.models.execute), 1))
		plan.reasons.execute = 'quality executes on the coding model or above'
		plan.models.review = tierAt(Math.max(modelTier(plan.models.review), 2))
		plan.reasons.review = 'quality reviews on the deep-reasoning model'
	} else if (profile === 'sensitive') {
		plan.models.plan = tierAt(2)
		plan.reasons.plan = 'the sensitive profile plans on the deep-reasoning model'
		plan.models.execute = tierAt(Math.max(modelTier(plan.models.execute), 1))
		plan.reasons.execute = 'the sensitive profile never executes on the fast model'
		plan.models.review = tierAt(2)
		plan.reasons.review = 'the sensitive profile reviews on the deep-reasoning model'
	}

	enforceSafety(plan, decision)
	return plan
}

/** Every model a profile can produce, for the drift check that keeps profiles inside VERIFIED_MODELS. */
export function profileModels(profile: CostProfile): RoutedModel[] {
	const normal: RouteDecision = {
		route: 'coding',
		complexity: 0,
		needsLongContext: false,
		needsTools: true,
		confidence: 0.95,
		risk: 'normal',
		model: VERIFIED_MODELS.flash,
		reason: 'profile check',
		mode: 'high',
	}
	const risky: RouteDecision = { ...normal, risk: 'sensitive' }
	const destructive: RouteDecision = { ...normal, risk: 'destructive' }
	const planning: RouteDecision = { ...normal, route: 'planning', complexity: 2 }
	const models = new Set<RoutedModel>()
	for (const decision of [normal, risky, destructive, planning]) {
		const plan = resolveStageModels(profile, decision)
		for (const stage of ORCHESTRATE_STAGES) models.add(plan.models[stage])
	}
	return [...models]
}

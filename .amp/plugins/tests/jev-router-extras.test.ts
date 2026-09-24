import { afterEach, beforeEach, describe, expect, test } from 'bun:test'
import type { CreateAgentConfig, ThreadMessage } from '@ampcode/plugin'
import type { OrchestrateStage, RouteDecision } from '../jev-router.ts'
import plugin, {
	COST_PROFILES,
	EVIDENCE_MAX_ITEMS,
	KNOWN_GUIDANCE_PATHS,
	READ_ONLY_STAGE_TOOLS,
	TOOL_RESULT_DEFAULT_CHARS,
	TOOL_RESULT_MAX_CHARS,
	TOOL_RESULT_MIN_CHARS,
	VERIFIED_MODELS,
	adjustBudgetWithRelevance,
	autoReviewMax,
	autoReviewMessage,
	buildRelevanceState,
	collectGuidance,
	compressToolResultText,
	detectToolResultKind,
	extractJsonObject,
	formatEvidencePackage,
	formatParallelInspect,
	guidancePathHint,
	guidanceScore,
	orchestrate,
	parseAutoReviewMode,
	parseEvidencePackage,
	parseProfile,
	parseRelevanceResponse,
	pathHintsFromTask,
	planAutoReview,
	planToolResultCompression,
	profileFromEnv,
	profileModels,
	rankGuidanceFiles,
	resolveStageModels,
	runWithConcurrency,
	selectGuidanceFiles,
	stageMessage,
	summarizeGuidance,
	turnHasCodeChanges,
	parallelInspect,
} from '../jev-router.ts'

// ---------------------------------------------------------------------------
// harness
// ---------------------------------------------------------------------------

type Handler = (event: unknown, ctx: unknown) => Promise<unknown>

function makeApi() {
	const handlers = new Map<string, Handler>()
	const agents: CreateAgentConfig[] = []
	const tools: string[] = []
	const api = {
		on: (event: string, handler: Handler) => {
			handlers.set(event, handler)
		},
		registerTool: (def: { name: string }) => {
			tools.push(def.name)
		},
		registerCommand: () => {},
		registerAgentMode: () => {},
		createAgent: (config: CreateAgentConfig) => {
			agents.push(config)
			return {
				definition: { kind: 'agent-definition', model: config.model },
				run: async () => ({ threadID: 'T-run', text: 'ok' }),
				createThread: async () => ({ id: 'T-review', appendUserMessage: async () => {} }),
			}
		},
		logger: { log: () => {} },
		helpers: {
			shellCommandFromToolCall: () => null,
			filesModifiedByToolCall: () => null,
			filePathFromURI: (uri: string | { toString(): string }) => String(uri),
		},
	}
	return { api, handlers, agents, tools }
}

function loaded() {
	const harness = makeApi()
	plugin(harness.api as never)
	return harness
}

function decision(overrides: Partial<RouteDecision> = {}): RouteDecision {
	const base: RouteDecision = {
		route: 'coding',
		complexity: 0,
		needsLongContext: false,
		needsTools: true,
		confidence: 0.95,
		risk: 'normal',
		model: VERIFIED_MODELS.flash,
		reason: 'test',
		mode: 'high',
	}
	return { ...base, ...overrides }
}

function longText(chars: number): string {
	const line = 'x'.repeat(119)
	const lines: string[] = []
	lines.push('ERROR: something failed at file.ts:42')
	while (lines.join('\n').length < chars) lines.push(line)
	return lines.join('\n').slice(0, chars)
}

const originalFetch = globalThis.fetch
beforeEach(() => {
	delete process.env.TYPESAFE_API_KEY
	delete process.env.JEV_ROUTER_AUTO_REVIEW
	delete process.env.JEV_ROUTER_AUTO_REVIEW_MAX
	delete process.env.JEV_ROUTER_PROFILE
	delete process.env.JEV_ROUTER_TOOL_RESULT
	globalThis.fetch = (async () => {
		throw new Error('network is disabled in tests')
	}) as unknown as typeof fetch
})
afterEach(() => {
	globalThis.fetch = originalFetch
	delete process.env.TYPESAFE_API_KEY
	delete process.env.JEV_ROUTER_AUTO_REVIEW
	delete process.env.JEV_ROUTER_AUTO_REVIEW_MAX
	delete process.env.JEV_ROUTER_PROFILE
	delete process.env.JEV_ROUTER_TOOL_RESULT
})

// ---------------------------------------------------------------------------
// tool.result compression
// ---------------------------------------------------------------------------

describe('tool.result compression', () => {
	test('classifies diffs, logs, searches, file reads, and generic output', () => {
		expect(detectToolResultKind('shell_command', { command: 'git diff' }, 'diff --git a/x b/x\n@@ -1 +1 @@')).toBe('diff')
		expect(detectToolResultKind('shell_command', { command: 'bun test' }, 'FAIL ')).toBe('logs')
		expect(detectToolResultKind('rg', { command: 'rg foo' }, 'a.ts:1:foo')).toBe('search')
		expect(detectToolResultKind('shell_command', { command: 'cat a.ts' }, 'a.ts:1:foo\nb.ts:2:bar\nc.ts:3:baz')).toBe('search')
		expect(detectToolResultKind('Read', {}, 'file body')).toBe('file')
		expect(detectToolResultKind('shell_command', { command: 'echo hi' }, 'hi')).toBe('generic')
	})

	test('short outputs are never compressed and never reach Jev', () => {
		const plan = planToolResultCompression('Read', {}, 'small output')
		expect(plan.compress).toBe(false)
		expect(plan.jevEligible).toBe(false)
		const big = planToolResultCompression('Read', {}, longText(20_000))
		expect(big.compress).toBe(true)
		expect(big.jevEligible).toBe(true)
		expect(big.kind).toBe('file')
	})

	test('compression keeps error lines and stays within the budget', () => {
		const text = longText(40_000)
		const out = compressToolResultText(text, 'generic', TOOL_RESULT_DEFAULT_CHARS)
		expect(out).toContain('ERROR: something failed at file.ts:42')
		expect(out).toContain('Jev Router compressed')
		expect(out.length).toBeLessThanOrEqual(TOOL_RESULT_DEFAULT_CHARS + 300)
		expect(out.length).toBeLessThan(text.length)
	})

	test('a single oversized line is clipped instead of bypassing the budget', () => {
		const oneLine = 'y'.repeat(50_000)
		const out = compressToolResultText(oneLine, 'file', TOOL_RESULT_MIN_CHARS)
		expect(out.length).toBeLessThanOrEqual(TOOL_RESULT_MIN_CHARS + 300)
	})

	test('diff structure is preserved', () => {
		const body = Array.from({ length: 400 }, (_, i) => (i % 7 === 0 ? `+added line ${i}` : ` unchanged line ${i}`)).join('\n')
		const text = `diff --git a/a.ts b/a.ts\nindex 111..222 100644\n--- a/a.ts\n+++ b/a.ts\n@@ -1,3 +1,3 @@\n${body}`
		const out = compressToolResultText(text, 'diff', TOOL_RESULT_MIN_CHARS)
		expect(out).toContain('diff --git a/a.ts b/a.ts')
		expect(out).toContain('@@ -1,3 +1,3 @@')
	})

	test('compression is deterministic', () => {
		const text = longText(30_000)
		expect(compressToolResultText(text, 'logs', 6_000)).toBe(compressToolResultText(text, 'logs', 6_000))
	})

	test('short text is returned unchanged', () => {
		expect(compressToolResultText('abc', 'generic', TOOL_RESULT_MIN_CHARS)).toBe('abc')
	})

	test('a structured relevance judgment only moves the budget inside the bounds', () => {
		const plan = planToolResultCompression('Read', {}, longText(20_000))
		const low = { signal: 'noise' as const, score: 0, confidence: 0.3 }
		const noise = { signal: 'noise' as const, score: 0, confidence: 0.9 }
		const mixed = { signal: 'mixed' as const, score: 2, confidence: 0.9 }
		const essential = { signal: 'essential' as const, score: 2, confidence: 0.9 }
		expect(adjustBudgetWithRelevance(plan, low)).toBe(TOOL_RESULT_DEFAULT_CHARS)
		expect(adjustBudgetWithRelevance(plan, noise)).toBe(8_400)
		expect(adjustBudgetWithRelevance(plan, mixed)).toBe(18_200)
		expect(adjustBudgetWithRelevance(plan, essential)).toBe(TOOL_RESULT_MAX_CHARS)
		for (const relevance of [low, noise, mixed, essential]) {
			const budget = adjustBudgetWithRelevance(plan, relevance)
			expect(budget).toBeGreaterThanOrEqual(TOOL_RESULT_MIN_CHARS)
			expect(budget).toBeLessThanOrEqual(TOOL_RESULT_MAX_CHARS)
		}
	})

	test('parseRelevanceResponse validates Jev answers', () => {
		expect(() => parseRelevanceResponse({})).toThrow('answers are missing')
		expect(() =>
			parseRelevanceResponse({
				answers: { signal: { type: 'choice', choice: 'maybe' }, signal_ratio: { type: 'score', score: 0 } },
			}),
		).toThrow('invalid relevance signal')
		expect(() =>
			parseRelevanceResponse({
				answers: { signal: { type: 'choice', choice: 'noise' }, signal_ratio: { type: 'score', score: 9 } },
			}),
		).toThrow('outside the configured rubric')
		expect(
			parseRelevanceResponse({
				answers: {
					signal: { type: 'choice', choice: 'essential', confidence: 0.8 },
					signal_ratio: { type: 'score', score: 2, confidence: 0.7 },
				},
			}),
		).toEqual({ signal: 'essential', score: 2, confidence: 0.7 })
	})

	test('buildRelevanceState sends metadata and a clipped excerpt only', () => {
		const text = longText(40_000)
		const plan = planToolResultCompression('shell_command', { command: 'bun test' }, text)
		const state = JSON.parse(buildRelevanceState('shell_command', plan, text)) as { kind: string; chars: number; excerpt: string }
		expect(state.kind).toBe('logs')
		expect(state.chars).toBe(text.length)
		expect(state.excerpt.length).toBeLessThan(text.length)
		expect(state.excerpt).toContain('truncated')
	})
})

describe('tool.result handler', () => {
	function toolResult(text: unknown, overrides: Record<string, unknown> = {}) {
		const { handlers } = loaded()
		const handler = handlers.get('tool.result')!
		return handler(
			{ tool: 'shell_command', input: { command: 'cat big.log' }, status: 'done', output: text, thread: { id: 'T' }, ...overrides },
			{ logger: { log: () => {} } },
		)
	}

	test('leaves a short output and an error result untouched', async () => {
		expect(await toolResult('short')).toBeUndefined()
		expect(await toolResult(longText(30_000), { status: 'error', error: 'boom' })).toBeUndefined()
		expect(await toolResult({ stdout: 'structured' })).toBeUndefined()
	})

	test('compresses a long output deterministically with no Jev key', async () => {
		const result = (await toolResult(longText(30_000))) as { status: string; output: string }
		expect(result.status).toBe('done')
		expect(result.output).toContain('ERROR: something failed at file.ts:42')
		expect(result.output.length).toBeLessThan(30_000)
	})

	test('uses a Jev relevance answer to pick between the budget bounds', async () => {
		process.env.TYPESAFE_API_KEY = 'test-key'
		const calls = { count: 0 }
		const reply = (signal: string) => {
			globalThis.fetch = (async () => {
				calls.count += 1
				return {
					ok: true,
					status: 200,
					json: async () => ({
						answers: {
							signal: { type: 'choice', choice: signal, confidence: 0.95 },
							signal_ratio: { type: 'score', score: signal === 'noise' ? 0 : 2, confidence: 0.95 },
						},
					}),
				}
			}) as unknown as typeof fetch
		}
		reply('noise')
		const noise = (await toolResult(longText(60_000))) as { output: string }
		reply('essential')
		const essential = (await toolResult(longText(60_000))) as { output: string }
		expect(calls.count).toBe(2)
		expect(essential.output.length).toBeGreaterThan(noise.output.length)
		expect(noise.output.length).toBeLessThanOrEqual(8_400 + 300)
		expect(essential.output.length).toBeLessThanOrEqual(TOOL_RESULT_MAX_CHARS + 300)
	})

	test('fails open when Jev relevance is unavailable', async () => {
		process.env.TYPESAFE_API_KEY = 'test-key'
		globalThis.fetch = (async () => {
			throw new Error('boom')
		}) as unknown as typeof fetch
		const result = (await toolResult(longText(30_000))) as { status: string; output: string }
		expect(result.status).toBe('done')
		expect(result.output.length).toBeLessThanOrEqual(TOOL_RESULT_DEFAULT_CHARS + 300)
	})

	test('does not consult Jev when the switch is off', async () => {
		process.env.TYPESAFE_API_KEY = 'test-key'
		process.env.JEV_ROUTER_TOOL_RESULT = '0'
		const calls = { count: 0 }
		globalThis.fetch = (async () => {
			calls.count += 1
			return { ok: true, status: 200, json: async () => ({}) }
		}) as unknown as typeof fetch
		const result = (await toolResult(longText(30_000))) as { status: string }
		expect(result.status).toBe('done')
		expect(calls.count).toBe(0)
	})
})

// ---------------------------------------------------------------------------
// structured evidence package
// ---------------------------------------------------------------------------

describe('structured evidence package', () => {
	const fenced = [
		'Some prose.',
		'```json',
		JSON.stringify({
			files: ['src/a.ts — parser'],
			symbols: [{ symbol: 'parse()', file: 'src/a.ts:10' }],
			constraints: ['do not change the public API'],
			unknowns: [],
			checks: ['bun test'],
			risks: ['low'],
		}),
		'```',
	].join('\n')

	test('parses a JSON evidence package and normalizes object items', () => {
		const pkg = parseEvidencePackage(fenced)
		expect(pkg.parsed).toBe(true)
		expect(pkg.files).toEqual(['src/a.ts — parser'])
		expect(pkg.symbols).toEqual(['parse() — src/a.ts:10'])
		expect(pkg.constraints).toEqual(['do not change the public API'])
		expect(pkg.checks).toEqual(['bun test'])
		expect(pkg.unknowns).toEqual([])
	})

	test('truncates oversized packages', () => {
		const many = parseEvidencePackage(JSON.stringify({ files: Array.from({ length: 100 }, (_, i) => `file-${i}.ts`) }))
		expect(many.files.length).toBe(EVIDENCE_MAX_ITEMS)
		const long = parseEvidencePackage(JSON.stringify({ files: ['x'.repeat(900)] }))
		expect(long.files[0].length).toBeLessThanOrEqual(301)
		expect(long.files[0].endsWith('…')).toBe(true)
	})

	test('degrades to a clipped raw excerpt when there is no JSON package', () => {
		const pkg = parseEvidencePackage('not json at all')
		expect(pkg.parsed).toBe(false)
		expect(formatEvidencePackage(pkg)).toContain('did not return a JSON evidence package')
		const huge = parseEvidencePackage('x'.repeat(80_000))
		expect(huge.raw).toContain('truncated')
	})

	test('a JSON object without expected keys is not treated as a package', () => {
		const pkg = parseEvidencePackage('{"other": 1}')
		expect(pkg.parsed).toBe(false)
	})

	test('extractJsonObject finds fenced and bare objects', () => {
		expect(extractJsonObject('```json\n{"a":1}\n```')).toEqual({ a: 1 })
		expect(extractJsonObject('prefix {"a":1} suffix')).toEqual({ a: 1 })
		expect(extractJsonObject('nothing here')).toBeUndefined()
	})

	test('the plan handoff carries structured fields, not a wall of prose', () => {
		const message = stageMessage('plan', { task: 'fix it', decision: decision(), inspectReport: fenced })
		expect(message).toContain('## Relevant files')
		expect(message).toContain('src/a.ts — parser')
		expect(message).toContain('## Checks')
	})
})

// ---------------------------------------------------------------------------
// conditional guidance
// ---------------------------------------------------------------------------

describe('conditional guidance', () => {
	test('extracts path hints from a task', () => {
		const hints = pathHintsFromTask('fix apps/android/app/Foo.kt and docs/README.md today')
		expect(hints).toContain('apps/android/app/Foo.kt')
		expect(hints).toContain('docs/README.md')
	})

	test('ranks the guidance nearest the hinted paths first, root as fallback', () => {
		const available = ['AGENTS.md', 'apps/android/AGENTS.md', 'docs/AGENTS.md']
		expect(rankGuidanceFiles(available, ['apps/android/app/Foo.kt'])).toEqual([
			'apps/android/AGENTS.md',
			'AGENTS.md',
			'docs/AGENTS.md',
		])
		expect(rankGuidanceFiles(available, [])[0]).toBe('AGENTS.md')
		expect(guidanceScore('AGENTS.md', [])).toBeGreaterThanOrEqual(1)
		expect(guidanceScore('apps/android/AGENTS.md', ['apps/android/app/Foo.kt'])).toBeGreaterThan(guidanceScore('AGENTS.md', ['apps/android/app/Foo.kt']))
	})

	test('caps how many guidance files are selected', () => {
		const many = Array.from({ length: 10 }, (_, i) => `d${i}/AGENTS.md`)
		expect(selectGuidanceFiles(many, [], 2).length).toBe(2)
	})

	test('summarizes guidance, dropping secret-looking lines and capping length', () => {
		const text = ['# Rules', 'api_key = supersecretvalue', 'Keep changes small.', 'x'.repeat(5000)].join('\n')
		const summary = summarizeGuidance(text, 400)
		expect(summary).toContain('# Rules')
		expect(summary).toContain('Keep changes small.')
		expect(summary).not.toContain('supersecretvalue')
		expect(summary.length).toBeLessThanOrEqual(400 + 60)
	})

	test('falls back to path hints when the shell is unavailable', async () => {
		const { api } = loaded()
		delete (api as { $?: unknown }).$
		const guidance = await collectGuidance(api as never, ['apps/android/app/Foo.kt'])
		expect(guidance).toContain('apps/android/AGENTS.md')
		expect(guidance).toContain('read them yourself')
	})

	test('reads and summarizes the highest-ranked guidance when the shell works', async () => {
		const { api } = loaded()
		;(api as { $?: unknown }).$ = async (strings: TemplateStringsArray, ...values: unknown[]) => {
			const head = strings[0]
			if (head.includes('rg --files')) return { exitCode: 0, stdout: 'AGENTS.md\napps/android/AGENTS.md\n' }
			if (head.trim() === 'cat' && values[0] === 'apps/android/AGENTS.md') {
				return { exitCode: 0, stdout: '# Android rules\nKeep the View layer intact.' }
			}
			return { exitCode: 0, stdout: '# Root rules' }
		}
		const guidance = await collectGuidance(api as never, ['apps/android/app/Foo.kt'])
		expect(guidance).toContain('apps/android/AGENTS.md')
		expect(guidance).toContain('# Android rules')
	})

	test('guidancePathHint lists paths or is empty', () => {
		expect(guidancePathHint(['AGENTS.md'])).toContain('AGENTS.md')
		expect(guidancePathHint([])).toBe('')
		expect(KNOWN_GUIDANCE_PATHS).toContain('AGENTS.md')
	})

	test('the inspect stage message only adds guidance when it is present', () => {
		const without = stageMessage('inspect', { task: 't', decision: decision() })
		expect(without).not.toContain('Project guidance selected')
		const withGuidance = stageMessage('inspect', { task: 't', decision: decision(), guidance: '- AGENTS.md\n# Rules' })
		expect(withGuidance).toContain('Project guidance selected')
		expect(withGuidance).toContain('# Rules')
	})
})

// ---------------------------------------------------------------------------
// auto review (agent.end)
// ---------------------------------------------------------------------------

function assistantWithTool(name: string): ThreadMessage {
	return { role: 'assistant', id: 'A1', content: [{ type: 'tool_use', id: 'tu1', name, input: {} }] }
}

describe('auto review planning', () => {
	test('parses the switch and cap', () => {
		expect(parseAutoReviewMode(undefined)).toBe('thread')
		expect(parseAutoReviewMode('0')).toBe('off')
		expect(parseAutoReviewMode('off')).toBe('off')
		expect(parseAutoReviewMode('continue')).toBe('continue')
		expect(parseAutoReviewMode('1')).toBe('thread')
		expect(autoReviewMax(undefined)).toBe(1)
		expect(autoReviewMax('3')).toBe(3)
		expect(autoReviewMax('-2')).toBe(1)
		expect(autoReviewMax('99')).toBe(5)
		expect(autoReviewMax('nope')).toBe(1)
	})

	test('only a clean, code-changing, uncapped, non-child turn is reviewed', () => {
		const base = {
			status: 'done' as const,
			mode: 'thread' as const,
			hasCodeChanges: true,
			hasParent: false,
			alreadyReviewed: false,
			continuations: 0,
			maxContinuations: 1,
		}
		expect(planAutoReview(base)).toEqual({ action: 'thread', reason: expect.stringContaining('background') })
		expect(planAutoReview({ ...base, mode: 'continue' }).action).toBe('continue')
		expect(planAutoReview({ ...base, mode: 'off' }).action).toBe('none')
		expect(planAutoReview({ ...base, status: 'error' }).action).toBe('none')
		expect(planAutoReview({ ...base, hasParent: true }).action).toBe('none')
		expect(planAutoReview({ ...base, hasCodeChanges: false }).action).toBe('none')
		expect(planAutoReview({ ...base, alreadyReviewed: true }).action).toBe('none')
		expect(planAutoReview({ ...base, continuations: 1 }).action).toBe('none')
	})

	test('detects code changes from editor tool uses only', () => {
		expect(turnHasCodeChanges([assistantWithTool('edit_file')])).toBe(true)
		expect(turnHasCodeChanges([assistantWithTool('create_file')])).toBe(true)
		expect(turnHasCodeChanges([assistantWithTool('Read')])).toBe(false)
		expect(turnHasCodeChanges([{ role: 'user', id: 'U1', content: [{ type: 'text', text: 'hi' }] }])).toBe(false)
	})

	test('the review message is read-only and bounded', () => {
		const message = autoReviewMessage()
		expect(message).toContain('read-only')
		expect(message).toContain('Do not edit files')
	})
})

describe('auto review handler', () => {
	const makeEvent = (
		threadID: string,
		id: string,
		messages: ThreadMessage[] = [assistantWithTool('edit_file')],
		overrides: Record<string, unknown> = {},
	) => ({
		thread: { id: threadID },
		message: 'do the thing',
		id,
		status: 'done' as const,
		messages,
		...overrides,
	})
	const makeCtx = (threadID: string, parent: string | null = null) => ({
		thread: { id: threadID, parentThreadID: async () => parent },
		logger: { log: () => {} },
	})

	test('starts one background read-only thread on the fast model by default', async () => {
		const { handlers, agents } = loaded()
		const handler = handlers.get('agent.end')!
		expect(await handler(makeEvent('T-a', 'M-default'), makeCtx('T-a'))).toBeUndefined()
		const review = agents.find((agent) => String(agent.name).includes('review-observer'))
		expect(review).toBeDefined()
		expect(review!.model).toBe(VERIFIED_MODELS.flash)
		const tools = review!.tools as readonly string[]
		expect(tools).toEqual([...READ_ONLY_STAGE_TOOLS])
		expect(tools).not.toContain('edit_file')
	})

	test('is off when the switch is 0', async () => {
		process.env.JEV_ROUTER_AUTO_REVIEW = '0'
		const { handlers, agents } = loaded()
		expect(await handlers.get('agent.end')!(makeEvent('T-b', 'M-off'), makeCtx('T-b'))).toBeUndefined()
		expect(agents.some((agent) => String(agent.name).includes('review-observer'))).toBe(false)
	})

	test('continue mode returns one bounded continuation and no agent', async () => {
		process.env.JEV_ROUTER_AUTO_REVIEW = 'continue'
		const { handlers, agents } = loaded()
		const result = (await handlers.get('agent.end')!(makeEvent('T-c', 'M-continue'), makeCtx('T-c'))) as {
			action: string
			userMessage: string
			maxContinuations: number
		}
		expect(result.action).toBe('continue')
		expect(result.maxContinuations).toBe(1)
		expect(result.userMessage).toContain('read-only')
		expect(agents.some((agent) => String(agent.name).includes('review-observer'))).toBe(false)
	})

	test('never reviews a child thread or a failed/no-change turn', async () => {
		const { handlers, agents } = loaded()
		const handler = handlers.get('agent.end')!
		expect(await handler(makeEvent('T-child', 'M-child'), makeCtx('T-child', 'T-parent'))).toBeUndefined()
		expect(await handler(makeEvent('T-error', 'M-error', [assistantWithTool('edit_file')], { status: 'error' }), makeCtx('T-error'))).toBeUndefined()
		expect(await handler(makeEvent('T-nocode', 'M-nocode', []), makeCtx('T-nocode'))).toBeUndefined()
		expect(agents.some((agent) => String(agent.name).includes('review-observer'))).toBe(false)
	})

	test('dedupes a turn and caps reviews per thread', async () => {
		process.env.JEV_ROUTER_AUTO_REVIEW = 'continue'
		process.env.JEV_ROUTER_AUTO_REVIEW_MAX = '1'
		const { handlers } = loaded()
		const handler = handlers.get('agent.end')!
		const first = (await handler(makeEvent('T-cap', 'M-dup'), makeCtx('T-cap'))) as { action: string } | undefined
		const duplicate = await handler(makeEvent('T-cap', 'M-dup'), makeCtx('T-cap'))
		const second = await handler(makeEvent('T-cap', 'M-other'), makeCtx('T-cap'))
		expect(first?.action).toBe('continue')
		expect(duplicate).toBeUndefined()
		expect(second).toBeUndefined()
		delete process.env.JEV_ROUTER_AUTO_REVIEW_MAX
	})
})

// ---------------------------------------------------------------------------
// parallel read-only inspect
// ---------------------------------------------------------------------------

describe('parallel read-only inspect', () => {
	test('runWithConcurrency preserves order and respects the limit', async () => {
		let active = 0
		let peak = 0
		const results = await runWithConcurrency([1, 2, 3, 4, 5], 2, async (item) => {
			active += 1
			peak = Math.max(peak, active)
			await new Promise((resolve) => setTimeout(resolve, 1))
			active -= 1
			return item * 2
		})
		expect(results).toEqual([2, 4, 6, 8, 10])
		expect(peak).toBeLessThanOrEqual(2)
		expect(await runWithConcurrency([], 3, async () => 1)).toEqual([])
	})

	test('isolates a failing question and pins every child to the fast model', async () => {
		const created: CreateAgentConfig[] = []
		const summary = await parallelInspect(
			{
				createAgent: (config) => {
					created.push(config)
					return {
						definition: { kind: 'agent-definition', model: config.model },
						run: async () => {
							if (String(config.name).includes('-q2')) throw new Error('boom')
							return { threadID: `T-${config.name}`, text: 'evidence' }
						},
					}
				},
				maxConcurrency: 2,
			},
			['what is A?', 'what is B?', 'what is A?'],
		)
		expect(summary.model).toBe(VERIFIED_MODELS.flash)
		expect(summary.results.length).toBe(2) // duplicate dropped
		expect(summary.results[0].ok).toBe(true)
		expect(summary.results[1].ok).toBe(false)
		expect(summary.results[1].error).toContain('boom')
		expect(summary.ok).toBe(false)
		for (const config of created) {
			expect(config.model).toBe(VERIFIED_MODELS.flash)
			expect(config.tools).toEqual([...READ_ONLY_STAGE_TOOLS])
		}
	})

	test('formats the summary with per-question ids and failures', () => {
		const text = formatParallelInspect({
			model: VERIFIED_MODELS.flash,
			concurrency: 2,
			ok: false,
			results: [
				{ id: 'q1', question: 'a', ok: true, threadID: 'T-1', report: 'found it' },
				{ id: 'q2', question: 'b', ok: false, error: 'boom' },
			],
		})
		expect(text).toContain('q1')
		expect(text).toContain('found it')
		expect(text).toContain('FAILED')
		expect(text).toContain('boom')
	})
})

// ---------------------------------------------------------------------------
// cost/quality profiles
// ---------------------------------------------------------------------------

describe('cost/quality profiles', () => {
	test('balanced reproduces the original risk-driven stage models', () => {
		const focused = resolveStageModels('balanced', decision()).models
		expect(focused).toEqual({
			inspect: VERIFIED_MODELS.flash,
			plan: VERIFIED_MODELS.sol,
			execute: VERIFIED_MODELS.flash,
			review: VERIFIED_MODELS.astra,
		})
		const planning = resolveStageModels('balanced', decision({ route: 'planning', complexity: 2 })).models
		expect(planning).toEqual({
			inspect: VERIFIED_MODELS.flash,
			plan: VERIFIED_MODELS.astra,
			execute: VERIFIED_MODELS.flash,
			review: VERIFIED_MODELS.sol,
		})
	})

	test('economy caps the planner and reviewer, quality and sensitive raise the tiers', () => {
		const economy = resolveStageModels('economy', decision()).models
		expect(economy.review).toBe(VERIFIED_MODELS.sol)
		expect(economy.execute).toBe(VERIFIED_MODELS.flash)

		const quality = resolveStageModels('quality', decision()).models
		expect(quality).toEqual({
			inspect: VERIFIED_MODELS.flash,
			plan: VERIFIED_MODELS.astra,
			execute: VERIFIED_MODELS.sol,
			review: VERIFIED_MODELS.astra,
		})

		const sensitive = resolveStageModels('sensitive', decision()).models
		expect(sensitive.execute).toBe(VERIFIED_MODELS.sol)
		expect(sensitive.plan).toBe(VERIFIED_MODELS.astra)
	})

	test('no profile can lower the safety floor for sensitive or destructive work', () => {
		for (const profile of COST_PROFILES) {
			for (const risk of ['sensitive', 'destructive'] as const) {
				const models = resolveStageModels(profile, decision({ risk })).models
				expect(models.execute).not.toBe(VERIFIED_MODELS.flash)
				expect(models.review).not.toBe(models.execute)
				if (risk === 'destructive') {
					expect(models.plan).toBe(VERIFIED_MODELS.astra)
					expect(models.review).toBe(VERIFIED_MODELS.astra)
				}
			}
		}
	})

	test('every profile model comes from VERIFIED_MODELS', () => {
		const verified = new Set<string>(Object.values(VERIFIED_MODELS))
		for (const profile of COST_PROFILES) {
			for (const model of profileModels(profile)) expect(verified.has(model)).toBe(true)
		}
	})

	test('parses profiles and defaults to balanced', () => {
		expect(parseProfile('quality')).toBe('quality')
		expect(parseProfile('nope')).toBeUndefined()
		expect(parseProfile(3)).toBeUndefined()
		expect(profileFromEnv()).toBe('balanced')
		process.env.JEV_ROUTER_PROFILE = 'economy'
		expect(profileFromEnv()).toBe('economy')
		process.env.JEV_ROUTER_PROFILE = 'bogus'
		expect(profileFromEnv()).toBe('balanced')
	})

	test('the pipeline honors an explicit profile', async () => {
		const created: Array<{ name: string; model: string }> = []
		const result = await orchestrate({
			task: 'fix the parser',
			decision: decision(),
			profile: 'quality',
			deps: {
				createAgent: (config) => {
					created.push({ name: String(config.name), model: String(config.model) })
					return {
						definition: { kind: 'agent-definition', model: config.model },
						run: async () => ({ threadID: `T-${config.name}`, text: config.name?.includes('review') ? '## Verdict\nPASS — ok' : 'report' }),
					}
				},
			},
		})
		expect(result.status).toBe('completed')
		expect(result.profile).toBe('quality')
		const plan = created.find((entry) => entry.name.includes('plan'))
		const execute = created.find((entry) => entry.name.includes('execute'))
		expect(plan!.model).toBe(VERIFIED_MODELS.astra)
		expect(execute!.model).toBe(VERIFIED_MODELS.sol)
	})
})

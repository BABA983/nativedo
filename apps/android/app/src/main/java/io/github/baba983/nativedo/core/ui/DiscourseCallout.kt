package io.github.baba983.nativedo.core.ui

data class DiscourseCalloutSpec(
    val type: String,
    val icon: String,
    val color: String,
)

private data class DiscourseCalloutDefinition(
    val spec: DiscourseCalloutSpec,
    val aliases: Set<String> = emptySet(),
)

// Mirrors the default Quote Callouts theme component settings mapping:
// https://raw.githubusercontent.com/Arkshine/discourse-quote-callouts/main/settings.yml
private val discourseCalloutDefinitions = listOf(
    DiscourseCalloutDefinition(
        spec = DiscourseCalloutSpec(type = "note", icon = "far-pen-to-square", color = "#086ddd"),
    ),
    DiscourseCalloutDefinition(
        spec = DiscourseCalloutSpec(type = "abstract", icon = "far-clipboard", color = "#00bfbc"),
        aliases = setOf("summary", "tldr"),
    ),
    DiscourseCalloutDefinition(
        spec = DiscourseCalloutSpec(type = "info", icon = "far-lightbulb", color = "#027aff"),
    ),
    DiscourseCalloutDefinition(
        spec = DiscourseCalloutSpec(type = "todo", icon = "far-circle-check", color = "#027aff"),
    ),
    DiscourseCalloutDefinition(
        spec = DiscourseCalloutSpec(type = "tip", icon = "fire-flame-curved", color = "#00bfbc"),
        aliases = setOf("hint", "important"),
    ),
    DiscourseCalloutDefinition(
        spec = DiscourseCalloutSpec(type = "success", icon = "check", color = "#44cf6e"),
        aliases = setOf("check", "done"),
    ),
    DiscourseCalloutDefinition(
        spec = DiscourseCalloutSpec(type = "question", icon = "far-circle-question", color = "#ec7500"),
        aliases = setOf("help", "faq"),
    ),
    DiscourseCalloutDefinition(
        spec = DiscourseCalloutSpec(type = "warning", icon = "triangle-exclamation", color = "#ec7500"),
        aliases = setOf("caution", "attention"),
    ),
    DiscourseCalloutDefinition(
        spec = DiscourseCalloutSpec(type = "failure", icon = "xmark", color = "#e93147"),
        aliases = setOf("fail", "missing"),
    ),
    DiscourseCalloutDefinition(
        spec = DiscourseCalloutSpec(type = "danger", icon = "bolt", color = "#e93147"),
        aliases = setOf("error"),
    ),
    DiscourseCalloutDefinition(
        spec = DiscourseCalloutSpec(type = "bug", icon = "bug", color = "#e93147"),
    ),
    DiscourseCalloutDefinition(
        spec = DiscourseCalloutSpec(type = "example", icon = "list", color = "#7852ee"),
    ),
    DiscourseCalloutDefinition(
        spec = DiscourseCalloutSpec(type = "quote", icon = "quote-left", color = "#9e9e9e"),
        aliases = setOf("cite"),
    ),
)

private val discourseCalloutFallbackSpec = DiscourseCalloutSpec(
    type = "note",
    icon = "far-pen-to-square",
    color = "#027aff",
)

private val discourseCalloutSpecsByType = buildMap {
    discourseCalloutDefinitions.forEach { definition ->
        put(definition.spec.type, definition.spec)
        definition.aliases.forEach { alias ->
            put(alias, definition.spec)
        }
    }
}

internal fun resolveDiscourseCalloutSpec(
    rawType: String,
    rawIcon: String = "",
): DiscourseCalloutSpec {
    val normalizedType = rawType.trim().lowercase()
    val spec = if (normalizedType.isBlank()) {
        discourseCalloutFallbackSpec
    } else {
        discourseCalloutSpecsByType[normalizedType] ?: discourseCalloutFallbackSpec
    }
    val icon = rawIcon
        .trim()
        .substringAfterLast('#')
        .substringBefore('?')
        .substringAfterLast('/')
        .ifBlank { spec.icon }
    return if (icon == spec.icon) {
        spec
    } else {
        spec.copy(icon = icon)
    }
}

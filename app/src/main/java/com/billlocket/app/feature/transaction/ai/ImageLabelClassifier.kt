package com.billlocket.app.feature.transaction.ai

import java.util.Locale

object ImageLabelClassifier {
    const val MIN_AGGREGATE_CONFIDENCE = 0.70f
    const val MIN_AMBIGUITY_MARGIN = 0.10f
    const val MIN_SINGLE_LABEL_CONFIDENCE = 0.50f
    const val MAX_SUPPORTING_LABELS = 5

    private val BLOCKED_GENERIC_LABELS = setOf(
        "person",
        "face",
        "selfie",
        "portrait",
        "people",
        "human",
        "crowd",
        "landscape",
        "sky",
        "road",
        "street",
        "building",
        "room",
        "paper",
        "text",
        "document",
        "receipt",
        "screenshot",
        "photography",
        "image",
        "product",
        "object",
        "material",
        "event",
        "fun",
        "technology",
        "electronic device",
    )

    private val WHITELIST: Map<String, SemanticCategoryKey> = buildMap {
        listOf(
            "food", "meal", "dish", "cuisine", "restaurant", "coffee", "tea",
            "beverage", "drink", "breakfast", "lunch", "dinner", "snack",
            "dessert", "fast food",
        ).forEach { put(it, SemanticCategoryKey.FOOD_DRINK) }

        listOf(
            "shirt", "clothing", "clothes", "dress", "footwear", "shoe",
            "bag", "handbag", "fashion accessory",
        ).forEach { put(it, SemanticCategoryKey.CLOTHING_SHOPPING) }

        listOf(
            "car", "bus", "train", "motorcycle", "motor vehicle", "vehicle",
            "fuel station", "gas station", "public transport",
        ).forEach { put(it, SemanticCategoryKey.TRANSPORT_FUEL) }

        listOf(
            "medicine", "medication", "pharmacy", "healthcare",
            "medical equipment", "first aid",
        ).forEach { put(it, SemanticCategoryKey.HEALTH) }

        listOf(
            "book", "stationery", "notebook", "laptop", "computer",
            "office supplies", "office equipment", "school supplies",
        ).forEach { put(it, SemanticCategoryKey.STUDY_WORK) }

        listOf(
            "cinema", "movie", "video game", "game", "sports equipment",
            "musical instrument",
        ).forEach { put(it, SemanticCategoryKey.ENTERTAINMENT) }

        listOf(
            "furniture", "home appliance", "household appliance",
            "kitchen appliance", "cookware", "home accessories",
        ).forEach { put(it, SemanticCategoryKey.HOME) }
    }

    fun classify(rawLabels: List<RecognizedLabel>): CategorySuggestion? {
        if (rawLabels.isEmpty()) return null

        // Retain only the highest confidence for each distinct normalized label
        val deduplicatedLabels = mutableMapOf<String, Float>()
        for (label in rawLabels) {
            val normalizedText = normalizeLabel(label.text)
            if (normalizedText.isBlank() || BLOCKED_GENERIC_LABELS.contains(normalizedText)) {
                continue
            }
            if (!WHITELIST.containsKey(normalizedText)) {
                continue
            }
            val validConfidence = label.confidence.coerceIn(0.0f, 1.0f)
            val currentMax = deduplicatedLabels[normalizedText] ?: 0.0f
            if (validConfidence > currentMax) {
                deduplicatedLabels[normalizedText] = validConfidence
            }
        }

        if (deduplicatedLabels.isEmpty()) return null

        // Group deduplicated labels by semantic key
        val groupScores = mutableMapOf<SemanticCategoryKey, MutableList<RecognizedLabel>>()
        for ((normalizedText, maxConfidence) in deduplicatedLabels) {
            val key = WHITELIST[normalizedText] ?: continue
            groupScores.getOrPut(key) { mutableListOf() }
                .add(RecognizedLabel(normalizedText, maxConfidence))
        }

        // Calculate aggregate confidence per group
        data class ScoredGroup(
            val key: SemanticCategoryKey,
            val aggregateConfidence: Float,
            val maxSingleConfidence: Float,
            val supportingLabels: List<String>,
        )

        val scoredGroups = groupScores.map { (key, labels) ->
            var complementProduct = 1.0
            var maxSingle = 0.0f
            labels.forEach {
                complementProduct *= (1.0 - it.confidence.toDouble())
                if (it.confidence > maxSingle) maxSingle = it.confidence
            }
            val aggregate = (1.0 - complementProduct).toFloat().coerceIn(0.0f, 1.0f)
            val sortedSupporting = labels
                .sortedByDescending { it.confidence }
                .map { it.text }
                .distinct()
                .take(MAX_SUPPORTING_LABELS)

            ScoredGroup(key, aggregate, maxSingle, sortedSupporting)
        }.sortedByDescending { it.aggregateConfidence }

        val best = scoredGroups.firstOrNull() ?: return null

        // Must meet minimum aggregate confidence
        if (best.aggregateConfidence < MIN_AGGREGATE_CONFIDENCE) return null

        // Must have at least one reasonably confident supporting label to prevent noise accumulation
        if (best.maxSingleConfidence < MIN_SINGLE_LABEL_CONFIDENCE) return null

        // Must exceed ambiguity margin over second-best group
        if (scoredGroups.size > 1) {
            val second = scoredGroups[1]
            val margin = best.aggregateConfidence - second.aggregateConfidence
            if (margin < MIN_AMBIGUITY_MARGIN) return null
        }

        return CategorySuggestion(
            semanticKey = best.key,
            confidence = best.aggregateConfidence,
            supportingLabels = best.supportingLabels,
        )
    }

    fun normalizeLabel(text: String): String =
        text.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}

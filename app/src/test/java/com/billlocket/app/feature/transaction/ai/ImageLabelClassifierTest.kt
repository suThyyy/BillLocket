package com.billlocket.app.feature.transaction.ai

import org.junit.Assert.*
import org.junit.Test

class ImageLabelClassifierTest {
    @Test
    fun clothingPhraseLabels_areRecognizedWithoutExactStringMatch() {
        listOf("Dress shirt", "Fashion design", "Running shoe").forEach { label ->
            val suggestion = ImageLabelClassifier.classify(listOf(RecognizedLabel(label, 0.72f)))
            assertEquals(SemanticCategoryKey.CLOTHING_SHOPPING, suggestion?.semanticKey)
        }
    }

    @Test
    fun `empty labels returns null`() {
        val result = ImageLabelClassifier.classify(emptyList())
        assertNull(result)
    }

    @Test
    fun `food drink labels classify correctly`() {
        val labels = listOf(RecognizedLabel("food", 0.85f))
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNotNull(suggestion)
        assertEquals(SemanticCategoryKey.FOOD_DRINK, suggestion!!.semanticKey)
        assertEquals(0.85f, suggestion.confidence, 0.001f)
        assertEquals(listOf("food"), suggestion.supportingLabels)
    }

    @Test
    fun `clothing shopping labels classify correctly`() {
        val labels = listOf(RecognizedLabel("clothing", 0.82f))
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNotNull(suggestion)
        assertEquals(SemanticCategoryKey.CLOTHING_SHOPPING, suggestion!!.semanticKey)
        assertEquals(listOf("clothing"), suggestion.supportingLabels)
    }

    @Test
    fun `transport fuel labels classify correctly`() {
        val labels = listOf(RecognizedLabel("vehicle", 0.78f))
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNotNull(suggestion)
        assertEquals(SemanticCategoryKey.TRANSPORT_FUEL, suggestion!!.semanticKey)
        assertEquals(listOf("vehicle"), suggestion.supportingLabels)
    }

    @Test
    fun `health labels classify correctly`() {
        val labels = listOf(RecognizedLabel("medicine", 0.80f))
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNotNull(suggestion)
        assertEquals(SemanticCategoryKey.HEALTH, suggestion!!.semanticKey)
        assertEquals(listOf("medicine"), suggestion.supportingLabels)
    }

    @Test
    fun `study work labels classify correctly`() {
        val labels = listOf(RecognizedLabel("book", 0.75f))
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNotNull(suggestion)
        assertEquals(SemanticCategoryKey.STUDY_WORK, suggestion!!.semanticKey)
        assertEquals(listOf("book"), suggestion.supportingLabels)
    }

    @Test
    fun `entertainment labels classify correctly`() {
        val labels = listOf(RecognizedLabel("video game", 0.88f))
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNotNull(suggestion)
        assertEquals(SemanticCategoryKey.ENTERTAINMENT, suggestion!!.semanticKey)
        assertEquals(listOf("video game"), suggestion.supportingLabels)
    }

    @Test
    fun `home labels classify correctly`() {
        val labels = listOf(RecognizedLabel("furniture", 0.77f))
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNotNull(suggestion)
        assertEquals(SemanticCategoryKey.HOME, suggestion!!.semanticKey)
        assertEquals(listOf("furniture"), suggestion.supportingLabels)
    }

    @Test
    fun `case and whitespace normalization work`() {
        val labels = listOf(RecognizedLabel("  FoOd \n", 0.85f))
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNotNull(suggestion)
        assertEquals(SemanticCategoryKey.FOOD_DRINK, suggestion!!.semanticKey)
        assertEquals(listOf("food"), suggestion.supportingLabels)
    }

    @Test
    fun `unknown labels do not produce suggestion`() {
        val labels = listOf(
            RecognizedLabel("spaceship", 0.95f),
            RecognizedLabel("alien", 0.90f),
            RecognizedLabel("nebula", 0.85f)
        )
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNull(suggestion)
    }

    @Test
    fun `person and face labels produce no suggestion`() {
        val labels = listOf(
            RecognizedLabel("person", 0.95f),
            RecognizedLabel("face", 0.92f),
            RecognizedLabel("selfie", 0.90f),
            RecognizedLabel("human", 0.88f)
        )
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNull(suggestion)
    }

    @Test
    fun `landscape and road labels produce no suggestion`() {
        val labels = listOf(
            RecognizedLabel("landscape", 0.95f),
            RecognizedLabel("sky", 0.90f),
            RecognizedLabel("road", 0.88f),
            RecognizedLabel("street", 0.85f),
            RecognizedLabel("building", 0.80f)
        )
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNull(suggestion)
    }

    @Test
    fun `receipt document text screenshot produce no suggestion`() {
        val labels = listOf(
            RecognizedLabel("receipt", 0.98f),
            RecognizedLabel("paper", 0.95f),
            RecognizedLabel("text", 0.92f),
            RecognizedLabel("document", 0.90f),
            RecognizedLabel("screenshot", 0.88f)
        )
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNull(suggestion)
    }

    @Test
    fun `blocked generic labels alongside weak evidence do not trigger suggestion`() {
        val labels = listOf(
            RecognizedLabel("receipt", 0.95f),
            RecognizedLabel("document", 0.90f),
            RecognizedLabel("food", 0.44f) // below MIN_SINGLE_LABEL_CONFIDENCE = 0.45f
        )
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNull(suggestion)
    }

    @Test
    fun `single label below single-label threshold is rejected`() {
        val labels = listOf(RecognizedLabel("food", 0.44f))
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNull(suggestion)
    }

    @Test
    fun `multiple labels aggregate confidence according to formula`() {
        // formula: combined = 1 - (1 - 0.60) * (1 - 0.55) = 1 - (0.40 * 0.45) = 1 - 0.18 = 0.82
        val labels = listOf(
            RecognizedLabel("food", 0.60f),
            RecognizedLabel("beverage", 0.55f)
        )
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNotNull(suggestion)
        assertEquals(SemanticCategoryKey.FOOD_DRINK, suggestion!!.semanticKey)
        assertEquals(0.82f, suggestion.confidence, 0.01f)
        assertEquals(listOf("food", "beverage"), suggestion.supportingLabels)
    }

    @Test
    fun `aggregate confidence below min threshold is rejected`() {
        val labels = listOf(RecognizedLabel("food", 0.59f))
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNull(suggestion)
    }

    @Test
    fun `common ML Kit fruit label classifies as food`() {
        val suggestion = ImageLabelClassifier.classify(listOf(RecognizedLabel("fruit", 0.66f)))

        assertNotNull(suggestion)
        assertEquals(SemanticCategoryKey.FOOD_DRINK, suggestion!!.semanticKey)
    }

    @Test
    fun `common ML Kit t-shirt label classifies as clothing`() {
        val suggestion = ImageLabelClassifier.classify(listOf(RecognizedLabel("T-shirt", 0.68f)))

        assertNotNull(suggestion)
        assertEquals(SemanticCategoryKey.CLOTHING_SHOPPING, suggestion!!.semanticKey)
    }

    @Test
    fun `competing categories below ambiguity margin are rejected`() {
        // FOOD_DRINK: 0.75f, CLOTHING_SHOPPING: 0.70f -> lead = 0.05f < 0.10f -> rejected
        val labels = listOf(
            RecognizedLabel("food", 0.75f),
            RecognizedLabel("clothing", 0.70f)
        )
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNull(suggestion)
    }

    @Test
    fun `competing categories with clear winner above margin are accepted`() {
        // FOOD_DRINK: 0.85f, CLOTHING_SHOPPING: 0.70f -> lead = 0.15f >= 0.10f -> accepted
        val labels = listOf(
            RecognizedLabel("food", 0.85f),
            RecognizedLabel("clothing", 0.70f)
        )
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNotNull(suggestion)
        assertEquals(SemanticCategoryKey.FOOD_DRINK, suggestion!!.semanticKey)
    }

    @Test
    fun `supporting labels are capped and sanitized`() {
        val labels = listOf(
            RecognizedLabel("food", 0.85f),
            RecognizedLabel("dish", 0.80f),
            RecognizedLabel("meal", 0.75f),
            RecognizedLabel("lunch", 0.70f),
            RecognizedLabel("dinner", 0.65f),
            RecognizedLabel("snack", 0.60f),
            RecognizedLabel("dessert", 0.55f)
        )
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNotNull(suggestion)
        assertTrue(suggestion!!.supportingLabels.size <= 5)
        assertEquals(listOf("food", "dish", "meal", "lunch", "dinner"), suggestion.supportingLabels)
    }

    @Test
    fun `repeated duplicate labels cannot artificially boost confidence`() {
        val labels = listOf(
            RecognizedLabel("food", 0.35f),
            RecognizedLabel("FOOD", 0.35f),
            RecognizedLabel(" food ", 0.35f),
            RecognizedLabel("food", 0.40f)
        )
        val suggestion = ImageLabelClassifier.classify(labels)
        assertNull(suggestion)
    }
}

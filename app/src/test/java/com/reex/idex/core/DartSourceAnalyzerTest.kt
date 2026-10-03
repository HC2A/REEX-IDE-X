package com.reex.idex.core

import org.junit.Assert.assertTrue
import org.junit.Test

class DartSourceAnalyzerTest {
    @Test
    fun detectsUnclosedBracket() {
        val result = DartSourceAnalyzer.analyze("void main() {")
        assertTrue(result.any { it.severity == Severity.ERROR && it.message.contains("Unclosed") })
    }

    @Test
    fun acceptsBasicFlutterProgram() {
        val source = "import 'package:flutter/material.dart';\nvoid main() { runApp(const MaterialApp()); }"
        val result = DartSourceAnalyzer.analyze(source)
        assertTrue(result.none { it.severity == Severity.ERROR })
    }

    @Test
    fun completionContainsFlutterWidgets() {
        assertTrue(CompletionEngine.suggest("Scaf").any { it.label == "Scaffold" })
    }
}

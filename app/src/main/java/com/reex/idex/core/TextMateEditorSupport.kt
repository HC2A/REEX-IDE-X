package com.reex.idex.core

import android.content.Context
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import org.eclipse.tm4e.core.registry.IThemeSource
import io.github.rosemoe.sora.widget.CodeEditor

object TextMateEditorSupport {
    @Volatile private var initialized = false

    fun configureDart(context: Context, editor: CodeEditor) {
        runCatching {
            initialize(context)
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
            editor.setEditorLanguage(TextMateLanguage.create("source.dart", true))
        }
    }

    @Synchronized
    private fun initialize(context: Context) {
        if (initialized) return
        val themeRegistry = ThemeRegistry.getInstance()
        val themePath = "textmate/darcula.json"
        val source = FileProviderRegistry.getInstance()
            .tryGetInputStream(themePath)
        themeRegistry.loadTheme(
            ThemeModel(IThemeSource.fromInputStream(source, themePath, null), "darcula")
        )
        themeRegistry.setTheme("darcula")
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        initialized = true
    }
}

package com.reex.idex.core

data class CompletionItem(
    val label: String,
    val detail: String,
    val insertText: String
)

object CompletionEngine {
    private val flutterWidgets = listOf(
        "MaterialApp","CupertinoApp","Scaffold","AppBar","BottomNavigationBar","NavigationBar",
        "Drawer","NavigationDrawer","FloatingActionButton","SafeArea","Container","SizedBox",
        "Padding","Center","Align","Expanded","Flexible","Spacer","AspectRatio","FittedBox",
        "Column","Row","Wrap","Stack","Positioned","ListView","ListView.builder","GridView",
        "GridView.builder","SingleChildScrollView","CustomScrollView","SliverAppBar",
        "Text","RichText","Icon","Image","CircleAvatar","Card","Chip","Divider","VerticalDivider",
        "ElevatedButton","FilledButton","OutlinedButton","TextButton","IconButton",
        "DropdownButton","Checkbox","Radio","Switch","Slider","TextField","TextFormField",
        "Form","FormField","FutureBuilder","StreamBuilder","ValueListenableBuilder",
        "AnimatedContainer","AnimatedOpacity","Hero"
    )

    private val dartKeywords = listOf(
        "abstract","as","assert","async","await","break","case","catch","class","const","continue",
        "covariant","default","deferred","do","dynamic","else","enum","export","extends","extension",
        "external","factory","false","final","finally","for","Function","get","hide","if","implements",
        "import","in","interface","is","late","library","mixin","new","null","on","operator","part",
        "required","rethrow","return","set","show","static","super","switch","sync","this","throw",
        "true","try","typedef","var","void","while","with","yield"
    )

    private val commonMembers = listOf(
        "build","context","setState","initState","dispose","didChangeDependencies","didUpdateWidget",
        "mounted","Theme.of","MediaQuery.of","Navigator.of","ScaffoldMessenger.of","showDialog",
        "showModalBottomSheet","Future.delayed","DateTime.now","debugPrint","print"
    )

    private val items: List<CompletionItem> =
        (flutterWidgets.map { CompletionItem(it, "Flutter widget", "$it()") } +
            dartKeywords.map { CompletionItem(it, "Dart keyword", "$it ") } +
            commonMembers.map { CompletionItem(it, "Dart/Flutter API", it) })

    fun suggest(prefix: String): List<CompletionItem> {
        val p = prefix.trim().lowercase()
        val ranked = items.sortedWith(
            compareBy<CompletionItem> {
                when {
                    p.isBlank() -> 1
                    it.label.lowercase() == p -> 0
                    it.label.lowercase().startsWith(p) -> 1
                    it.label.lowercase().contains(p) -> 2
                    else -> 3
                }
            }.thenBy { it.label }
        )
        return ranked.filter { p.isBlank() || it.label.lowercase().contains(p) }
            .take(24)
            .ifEmpty { items.take(24) }
    }

    fun all(): List<CompletionItem> = items
}

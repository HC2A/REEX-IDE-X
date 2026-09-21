package com.reex.idex.core

data class CompletionItem(
    val label: String,
    val detail: String,
    val insertText: String
)

object CompletionEngine {
    private val flutterWidgets = listOf(
        "MaterialApp","CupertinoApp","Scaffold","AppBar","NavigationBar","NavigationRail",
        "Drawer","NavigationDrawer","FloatingActionButton","SafeArea","Container","SizedBox",
        "Padding","Center","Align","Expanded","Flexible","Spacer","AspectRatio","FittedBox",
        "Column","Row","Wrap","Stack","Positioned","IndexedStack","ListView","ListView.builder",
        "GridView","GridView.builder","CustomScrollView","SliverAppBar","SliverList","SliverGrid",
        "Text","RichText","SelectableText","Icon","Image","CircleAvatar","Card","Chip","Divider",
        "ElevatedButton","FilledButton","OutlinedButton","TextButton","IconButton",
        "DropdownButton","Checkbox","Radio","Switch","Slider","TextField","TextFormField",
        "Form","FormField","FutureBuilder","StreamBuilder","ValueListenableBuilder",
        "AnimatedContainer","AnimatedOpacity","AnimatedBuilder","TweenAnimationBuilder","Hero",
        "GestureDetector","InkWell","IgnorePointer","AbsorbPointer","Visibility","Offstage",
        "LayoutBuilder","MediaQuery","Theme","Builder","DefaultTextStyle"
    )

    private val dartKeywords = listOf(
        "abstract","as","assert","augment","await","base","break","case","catch","class","const",
        "continue","covariant","default","deferred","do","dynamic","else","enum","extends",
        "extension","external","factory","false","final","finally","for","Function","get","hide",
        "if","implements","import","in","interface","is","late","library","macro","mixin","new",
        "null","on","operator","part","required","rethrow","return","sealed","set","show","static",
        "super","switch","sync","this","throw","true","try","typedef","var","void","when","while",
        "with","yield"
    )

    private val commonMembers = listOf(
        "build","context","setState","initState","dispose","didChangeDependencies","didUpdateWidget",
        "mounted","Theme.of","MediaQuery.of","Navigator.of","ScaffoldMessenger.of","showDialog",
        "showModalBottomSheet","showDatePicker","Future.delayed","Future.wait","DateTime.now",
        "debugPrint","print","jsonEncode","jsonDecode","async","await"
    )

    private val snippets = listOf(
        CompletionItem("stateless widget", "Flutter snippet", "class WidgetName extends StatelessWidget {\n  const WidgetName({super.key});\n\n  @override\n  Widget build(BuildContext context) => const SizedBox();\n}"),
        CompletionItem("stateful widget", "Flutter snippet", "class WidgetName extends StatefulWidget {\n  const WidgetName({super.key});\n\n  @override\n  State<WidgetName> createState() => _WidgetNameState();\n}\n\nclass _WidgetNameState extends State<WidgetName> {\n  @override\n  Widget build(BuildContext context) => const SizedBox();\n}"),
        CompletionItem("main", "Dart entrypoint", "void main() {\n  runApp(const MyApp());\n}"),
        CompletionItem("future builder", "Flutter snippet", "FutureBuilder<T>(\n  future: future,\n  builder: (context, snapshot) => const SizedBox(),\n)"),
        CompletionItem("list builder", "Flutter snippet", "ListView.builder(\n  itemCount: items.length,\n  itemBuilder: (context, index) => const SizedBox(),\n)")
    )

    fun suggest(prefix: String, source: String = ""): List<CompletionItem> {
        val p = prefix.trim().lowercase()
        val contextual = mutableListOf<CompletionItem>()
        val declaredClasses = Regex("""\bclass\s+([A-Za-z_][A-Za-z0-9_]*)""")
            .findAll(source).map { it.groupValues[1] }.distinct()
        declaredClasses.forEach {
            contextual += CompletionItem(it, "Project class", "$it()")
        }

        val declaredFunctions = Regex("""\b(?:Future<[^>]+>|void|String|int|double|bool|dynamic|[A-Z][A-Za-z0-9_]*)\s+([a-zA-Z_][A-Za-z0-9_]*)\s*\(""")
            .findAll(source).map { it.groupValues[1] }
            .filterNot { it in setOf("if","for","while","switch","catch") }
            .distinct()
        declaredFunctions.forEach {
            contextual += CompletionItem(it, "Project function", "$it()")
        }

        val variables = Regex("""\b(?:final|const|var|late)\s+(?:[A-Za-z_][A-Za-z0-9_<>,? ]*\s+)?([a-zA-Z_][A-Za-z0-9_]*)""")
            .findAll(source).map { it.groupValues[1] }.distinct()
        variables.forEach {
            contextual += CompletionItem(it, "Project variable", it)
        }

        val catalog =
            flutterWidgets.map { CompletionItem(it, "Flutter widget", "$it(") } +
            dartKeywords.map { CompletionItem(it, "Dart keyword", "$it ") } +
            commonMembers.map { CompletionItem(it, "Dart/Flutter API", it) } +
            snippets + contextual

        val ranked = catalog.distinctBy { it.label + "|" + it.detail }.sortedWith(
            compareBy<CompletionItem> {
                when {
                    p.isBlank() -> 2
                    it.label.lowercase() == p -> 0
                    it.label.lowercase().startsWith(p) -> 1
                    it.label.lowercase().contains(p) -> 3
                    else -> 4
                }
            }.thenBy { it.label }
        )
        return ranked.filter { p.isBlank() || it.label.lowercase().contains(p) }.take(40)
            .ifEmpty { ranked.take(40) }
    }

    fun all(): List<CompletionItem> = suggest("")
}

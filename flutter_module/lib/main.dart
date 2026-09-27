import 'package:flutter/material.dart';

void main() {
  runApp(const ReexRuntimeApp());
}

class ReexRuntimeApp extends StatelessWidget {
  const ReexRuntimeApp({super.key});

  @override
  Widget build(BuildContext context) {
    final route = WidgetsBinding.instance.platformDispatcher.defaultRouteName;
    final uri = Uri.tryParse(route);
    final project = uri?.queryParameters['project'] ?? 'workspace';
    final source = uri?.queryParameters['source'] ?? 'lib/main.dart';

    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'REEX IDE X',
      theme: ThemeData(useMaterial3: true, brightness: Brightness.dark),
      home: RuntimePreview(project: project, source: source),
    );
  }
}

class RuntimePreview extends StatelessWidget {
  const RuntimePreview({super.key, required this.project, required this.source});

  final String project;
  final String source;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('REEX • Flutter Runtime'),
        actions: [
          IconButton(
            tooltip: 'Reload',
            onPressed: () {},
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 720),
          child: Card(
            child: Padding(
              padding: const EdgeInsets.all(28),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.flutter_dash, size: 64),
                  const SizedBox(height: 18),
                  const Text(
                    'Embedded Flutter Engine is running',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 12),
                  Text('Project: $project', textAlign: TextAlign.center),
                  Text('Source: $source', textAlign: TextAlign.center),
                  const SizedBox(height: 24),
                  const Text(
                    'This runtime is the real Flutter module bundled with REEX IDE X. '
                    'Arbitrary edited Dart source is compiled only when the Flutter toolchain builds the project.',
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';

void main() {
  runApp(const ReexRuntimeApp());
}

class ReexRuntimeApp extends StatelessWidget {
  const ReexRuntimeApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      onGenerateRoute: (settings) {
        final uri = Uri.tryParse(settings.name ?? '/') ?? Uri(path: '/');
        if (uri.path == '/reex_preview') {
          return MaterialPageRoute<void>(
            builder: (_) => ReexPreviewPage(
              title: uri.queryParameters['title'] ?? 'REEX IDE X',
              text: uri.queryParameters['text'] ?? 'Hello Flutter',
              widgets: int.tryParse(uri.queryParameters['widgets'] ?? '0') ?? 0,
            ),
          );
        }
        return MaterialPageRoute<void>(
          builder: (_) => const ReexPreviewPage(
            title: 'REEX IDE X',
            text: 'Flutter Runtime',
            widgets: 0,
          ),
        );
      },
    );
  }
}

class ReexPreviewPage extends StatelessWidget {
  const ReexPreviewPage({
    super.key,
    required this.title,
    required this.text,
    required this.widgets,
  });

  final String title;
  final String text;
  final int widgets;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.flutter_dash, size: 72),
              const SizedBox(height: 24),
              Text(
                text,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 16),
              Text(
                'REEX IDE X • Flutter Engine Runtime',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 8),
              Text('Detected widgets: $widgets'),
            ],
          ),
        ),
      ),
    );
  }
}

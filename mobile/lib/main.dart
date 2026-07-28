import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import 'src/api/crewcomp_api.dart';
import 'src/data/database_opener.dart';
import 'src/data/local_store.dart';
import 'src/data/sync_engine.dart';
import 'src/ui/app_state.dart';
import 'src/ui/nocturne.dart';
import 'src/ui/screens.dart';

/// CREWCOMP crew self-service (§7).
///
/// The app is: my certifications, my roster, my alerts. It is not a planning tool and it never
/// writes a holding — the client-originated writes are evidence submissions, read-marks and the
/// crew member's own one-tap statements, all of which go through the outbound queue (§7.5, §7.6)
/// and none of which the §5 engine reads.
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // SEC-12: refuse to start rather than store crew records in the clear. A plain SQLite build
  // ignores `pragma key` silently, so without this check a mis-built app would run perfectly
  // and write everything readable.
  final cipher = cipherBuildVersion();
  if (cipher == null) {
    runApp(const _MisconfiguredApp());
    return;
  }
  debugPrint('Local store cipher: $cipher');

  final opener = await EncryptedFileOpener.create(DatabaseKeyStore());
  final store = LocalStore(opener.open());

  final api = CrewcompApi(
    baseUrl: Uri.parse(
      // Overridden per build; the default is a simulator talking to `quarkus:dev`.
      const String.fromEnvironment('CREWCOMP_API', defaultValue: 'http://127.0.0.1:8080'),
    ),
    // The development sign-in shim, and the whole of it.
    //
    // `kDebugMode` is a compile-time constant, so a release build drops this expression and the
    // header names with it. Same guarantee the backend gives by removing its shim bean at build
    // time, and the same one the admin SPA gets from `import.meta.env.DEV`.
    //
    // The real sign-in is ADR 0003's: an OIDC code flow through the system browser with tokens
    // held outside the app. That is the identity spike's deliverable (MOB-6).
    devIdentity: kDebugMode
        ? const DevIdentity(
            personId: int.fromEnvironment('CREWCOMP_DEV_PERSON', defaultValue: 2),
            label: 'Dev Crew',
          )
        : null,
  );

  final state = AppState(
    store: store,
    engine: SyncEngine(api: api, store: store),
    // MOB-11's tab. A supervisory role is the identity spike's to establish and nothing in the
    // sync payload carries one, so this is a debug-only switch for driving the screen — and
    // `kDebugMode` being a compile-time constant means a release build cannot reach it at all.
    supervisor: kDebugMode && const bool.fromEnvironment('CREWCOMP_DEV_SUPERVISOR'),
  );
  await state.load();

  runApp(CrewcompApp(state: state));
}

class CrewcompApp extends StatelessWidget {
  const CrewcompApp({super.key, required this.state});

  final AppState state;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Attest',
      debugShowCheckedModeBanner: false,
      // One theme, and it is dark. Nocturne has no light mode; following the platform would put
      // the crew app on a ground the console does not have, half-ported.
      theme: nocturneTheme(),
      darkTheme: nocturneTheme(),
      themeMode: ThemeMode.dark,
      home: CrewHome(state: state),
    );
  }
}

/// Shown when the app was built without the cipher-capable SQLite. Deliberately a dead end.
class _MisconfiguredApp extends StatelessWidget {
  const _MisconfiguredApp();

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.error_outline, size: 48),
                SizedBox(height: 16),
                Text(
                  'This build cannot encrypt its local store, so it will not run.\n\n'
                  'Restore `hooks.user_defines.sqlite3.source: sqlite3mc` in pubspec.yaml.',
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

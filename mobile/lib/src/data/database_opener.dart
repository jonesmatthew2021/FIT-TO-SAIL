import 'dart:io';
import 'dart:math';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sqlite3/sqlite3.dart';

/// Opening the encrypted local store, and looking after its key (SEC-12).
///
/// The database is SQLite3MultipleCiphers — selected in `pubspec.yaml` through the `sqlite3`
/// package's build hook (`hooks.user_defines.sqlite3.source: sqlite3mc`), not by depending on a
/// separate native-library package: those are end-of-life since `sqlite3` 3.x bundles its own
/// build. ADR 0002 predates that change and says otherwise.
///
/// The key is a 256-bit random value held in the iOS Keychain / Android Keystore via
/// `flutter_secure_storage`, never derived from anything the user types and never leaving the
/// device. Two consequences worth stating plainly:
///
///  * Losing the key means losing the local replica, which is fine — the replica is
///    reconstructible from a snapshot and holds nothing the server does not.
///  * Wiping the key is therefore a complete and instant logout, faster and more thorough than
///    deleting rows.
class DatabaseKeyStore {
  DatabaseKeyStore({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              // Not `first_unlock_this_device` and certainly not `always`: crew data should not
              // be readable while the phone is sitting locked in a bunk.
              iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
            );

  final FlutterSecureStorage _storage;

  static const _keyName = 'crewcomp.local-store-key';

  /// Returns the store's key, minting one on first run.
  Future<String> obtain() async {
    final existing = await _storage.read(key: _keyName);
    if (existing != null && existing.isNotEmpty) return existing;

    final key = _generateKey();
    await _storage.write(key: _keyName, value: key);
    return key;
  }

  /// Discards the key. The database file becomes unreadable immediately (SEC-12).
  Future<void> forget() => _storage.delete(key: _keyName);

  static String _generateKey() {
    final random = Random.secure();
    final bytes = List<int>.generate(32, (_) => random.nextInt(256));
    return bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  }
}

/// How the local store gets opened. Two implementations, and the difference matters.
abstract class DatabaseOpener {
  QueryExecutor open();

  /// Removes the underlying database entirely. Used on sign-out.
  Future<void> destroy();
}

/// The real one: an encrypted file in the app's private directory.
class EncryptedFileOpener implements DatabaseOpener {
  EncryptedFileOpener({required this.key, required this.directory});

  final String key;
  final Directory directory;

  static const fileName = 'crewcomp.db';

  /// Resolves the app-private directory and the key together.
  static Future<EncryptedFileOpener> create(DatabaseKeyStore keys) async {
    final directory = await getApplicationSupportDirectory();
    return EncryptedFileOpener(key: await keys.obtain(), directory: directory);
  }

  File get file => File('${directory.path}/$fileName');

  @override
  QueryExecutor open() => NativeDatabase.createInBackground(
        file,
        setup: (database) {
          // Must be the first statement on the connection: SQLite3MultipleCiphers derives the
          // page cipher from it, and any earlier read would hit an undecryptable header.
          database.execute("pragma key = '$key';");
          // Fails loudly here rather than at the first query if the cipher build is not the one
          // in use — an unencrypted store that silently works is the bad outcome.
          database.execute('select count(*) from sqlite_master;');
        },
      );

  @override
  Future<void> destroy() async {
    if (await file.exists()) await file.delete();
  }
}

/// In-memory and unencrypted, for tests.
///
/// Named for what it is. A test double that pretended to be encrypted would make the encryption
/// look tested when it is not — what the suite actually verifies about encryption is in
/// `test/encrypted_store_test.dart`, which opens a real cipher-backed file.
class InMemoryOpener implements DatabaseOpener {
  @override
  QueryExecutor open() => NativeDatabase.memory();

  @override
  Future<void> destroy() async {}
}

/// The SQLite3MultipleCiphers build identifier, or null if this is a plain SQLite build.
///
/// Checked at start-up so a build that has lost the `sqlite3mc` hook fails visibly instead of
/// quietly writing crew data to an unencrypted file (SEC-12). That failure mode is real and
/// silent: plain SQLite **ignores** an unknown `pragma key` rather than rejecting it, so the app
/// would open, sync, and work perfectly while storing everything in the clear.
///
/// Two detectors that do *not* work, both tried:
///
///  * `pragma compile_options` — SQLite3MultipleCiphers is a codec layer over stock SQLite and
///    adds no compile-time flag, so the option list is identical to a plain build's.
///  * `pragma cipher_version` — returns an empty result set here, and an unknown pragma on plain
///    SQLite also returns an empty result set. It cannot tell them apart.
///
/// `sqlite3mc_version()` is a SQL function the cipher build registers and a plain build does
/// not, so calling it either answers or throws.
String? cipherBuildVersion() {
  final probe = sqlite3.openInMemory();
  try {
    final rows = probe.select('select sqlite3mc_version() as version');
    return rows.isEmpty ? null : rows.first['version'] as String?;
  } on SqliteException {
    return null;
  } finally {
    probe.close();
  }
}

/// True when the local store will actually be encrypted.
bool cipherSupportAvailable() => cipherBuildVersion() != null;

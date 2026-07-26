import 'dart:io';

import 'package:crewcomp_crew/src/data/database_opener.dart';
import 'package:crewcomp_crew/src/data/local_store.dart';
import 'package:drift/drift.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqlite3/sqlite3.dart';

/// SEC-12: the local store is encrypted at rest.
///
/// This is the test that stops the encryption being a claim in a document. It writes a crew
/// member's name into a real database file through the real opener, then reads the raw bytes off
/// disk and asserts the name is not in them — and separately, that the wrong key cannot open it.
///
/// Both assertions matter. A plain SQLite build **silently ignores** `pragma key`, so an app
/// that had lost the `sqlite3mc` build hook would keep working, keep passing any test that only
/// checked "can I open it with the key", and write crew data to a readable file.
void main() {
  late Directory directory;

  setUp(() {
    directory = Directory.systemTemp.createTempSync('crewcomp-store-test');
  });

  tearDown(() {
    if (directory.existsSync()) directory.deleteSync(recursive: true);
  });

  test('the linked SQLite is a cipher build', () {
    // If this fails, `hooks.user_defines.sqlite3.source: sqlite3mc` has been lost from
    // pubspec.yaml and the app would store crew data in the clear.
    expect(cipherBuildVersion(), contains('SQLite3 Multiple Ciphers'));
  });

  test('crew data is not readable in the database file', () async {
    final opener = EncryptedFileOpener(key: 'a' * 64, directory: directory);
    final store = LocalStore(opener.open());

    await store.into(store.people).insert(
          PeopleCompanion.insert(
            id: const Value(1),
            sam: 'SAM001',
            name: 'Wilhelmina Featherstonehaugh',
            positionId: 1,
            positionName: 'Chief Officer',
            partnershipAbbrev: 'UNI',
            status: 'active',
          ),
        );
    await store.close();

    final bytes = opener.file.readAsBytesSync();
    expect(bytes.length, greaterThan(0));

    // A distinctive name, so a match cannot be coincidence.
    expect(
      String.fromCharCodes(bytes).contains('Featherstonehaugh'),
      isFalse,
      reason: 'Crew data appears in plaintext — the store is not encrypted',
    );
    // The SQLite header itself is encrypted too, so even the file's type is not evident.
    expect(String.fromCharCodes(bytes.take(16)).startsWith('SQLite format 3'), isFalse);
  });

  test('the wrong key cannot open the store', () async {
    final store = LocalStore(EncryptedFileOpener(key: 'a' * 64, directory: directory).open());
    await store.into(store.people).insert(
          PeopleCompanion.insert(
            id: const Value(1),
            sam: 'SAM001',
            name: 'Ada Nakamura',
            positionId: 1,
            positionName: 'Master',
            partnershipAbbrev: 'UNI',
            status: 'active',
          ),
        );
    await store.close();

    // Opening with a different key must fail rather than silently produce an empty database —
    // the second would look, to the app, exactly like a crew member with no records.
    final wrong = sqlite3.open('${directory.path}/${EncryptedFileOpener.fileName}');
    addTearDown(wrong.close);
    wrong.execute("pragma key = '${'b' * 64}';");

    expect(
      () => wrong.select('select * from people'),
      throwsA(isA<SqliteException>()),
    );
  });

  test('the right key reads it back', () async {
    final opener = EncryptedFileOpener(key: 'c' * 64, directory: directory);

    final first = LocalStore(opener.open());
    await first.into(first.people).insert(
          PeopleCompanion.insert(
            id: const Value(7),
            sam: 'SAM007',
            name: 'Bruno Oyelaran',
            positionId: 2,
            positionName: 'Chief Officer',
            partnershipAbbrev: 'UNI',
            status: 'active',
          ),
        );
    await first.close();

    final second = LocalStore(opener.open());
    addTearDown(second.close);
    final people = await second.select(second.people).get();

    expect(people, hasLength(1));
    expect(people.single.name, 'Bruno Oyelaran');
  });
}

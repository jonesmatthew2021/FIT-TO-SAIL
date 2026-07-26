// Generates `lib/src/api/schema.g.dart` from the backend's OpenAPI schema.
//
// DEV-2: client types are generated from the backend's schema and never hand-written, and CI
// fails when the committed output drifts from it. This is the Dart counterpart of
// `admin-web/scripts/generate-api-types.sh`.
//
//   dart run tool/generate_api.dart            # regenerate
//   dart run tool/generate_api.dart --check    # fail if the committed file is stale (CI)
//
// Why a 200-line generator rather than openapi-generator: that tool emits an entire HTTP client
// with its own dependency stack, and ADR 0002's whole argument for Flutter rests on a small,
// auditable dependency tree. What this app needs from the schema is data classes with
// `fromJson`, which is exactly what this produces and nothing more.
//
// Type mapping worth knowing about:
//
//   * `LocalDate` becomes **String**, not DateTime. Business dates are calendar dates in the
//     operating timezone (NFR-5, O-11); a Dart `DateTime` always carries a time and a timezone,
//     so parsing "2026-08-01" into one produces a value that is a different day depending on
//     where the phone is. `lib/src/domain/calendar.dart` does the arithmetic on the strings.
//   * `Instant` becomes DateTime, parsed to UTC — those genuinely are instants.
//   * `UUID` becomes String: the app treats it as an opaque idempotency key.

import 'dart:convert';
import 'dart:io';

const schemaPath = '../backend/target/openapi/openapi.json';
const outputPath = 'lib/src/api/schema.g.dart';

void main(List<String> args) {
  final check = args.contains('--check');

  final schemaFile = File(schemaPath);
  if (!schemaFile.existsSync()) {
    stderr.writeln(
      'Cannot find $schemaPath.\n'
      'The backend writes it during `./mvnw package` or `./mvnw quarkus:dev`.',
    );
    exit(2);
  }

  final document = jsonDecode(schemaFile.readAsStringSync()) as Map<String, dynamic>;
  final schemas = ((document['components'] as Map<String, dynamic>)['schemas']
      as Map<String, dynamic>);

  final generated = _render(schemas);
  final output = File(outputPath);

  if (check) {
    final current = output.existsSync() ? output.readAsStringSync() : '';
    if (current != generated) {
      stderr.writeln(
        'API types are stale: $outputPath does not match the backend schema.\n'
        'Run `dart run tool/generate_api.dart` and commit the result.',
      );
      exit(1);
    }
    stdout.writeln('API types are up to date.');
    return;
  }

  output.parent.createSync(recursive: true);
  output.writeAsStringSync(generated);
  stdout.writeln('Wrote $outputPath');
}

/// Schemas that are scalars in disguise — they map to a Dart primitive, not a class.
const _scalarAliases = {'LocalDate', 'Instant', 'UUID'};

String _render(Map<String, dynamic> schemas) {
  final buffer = StringBuffer()
    ..writeln('// GENERATED FILE — DO NOT EDIT.')
    ..writeln('//')
    ..writeln('// Produced by `dart run tool/generate_api.dart` from the backend\'s OpenAPI')
    ..writeln('// schema (DEV-2). Committed so the app builds without a JDK; `--check` in CI')
    ..writeln('// fails the build when the backend contract has moved and this has not.')
    ..writeln('//')
    ..writeln('// LocalDate is String on purpose — see the generator for why.')
    ..writeln()
    ..writeln('// ignore_for_file: unnecessary_cast, prefer_const_constructors')
    ..writeln();

  final names = schemas.keys.where((n) => !_scalarAliases.contains(n)).toList()..sort();

  for (final name in names) {
    final schema = schemas[name] as Map<String, dynamic>;
    if (schema['type'] != 'object') continue;
    _renderClass(buffer, name, schema);
  }
  return buffer.toString();
}

void _renderClass(StringBuffer out, String name, Map<String, dynamic> schema) {
  final properties = (schema['properties'] as Map<String, dynamic>? ?? {});
  final required = ((schema['required'] as List?) ?? const []).cast<String>().toSet();

  final fields = properties.entries.map((entry) {
    final propertySchema = entry.value as Map<String, dynamic>;
    final nullable = !required.contains(entry.key) || _isNullable(propertySchema);
    return _Field(
      name: _dartName(entry.key),
      wireName: entry.key,
      type: _dartType(propertySchema),
      nullable: nullable,
    );
  }).toList();

  out.writeln('class $name {');
  for (final field in fields) {
    out.writeln('  final ${field.type}${field.nullable ? '?' : ''} ${field.name};');
  }
  out.writeln();
  out.writeln('  const $name({');
  for (final field in fields) {
    out.writeln('    ${field.nullable ? '' : 'required '}this.${field.name},');
  }
  out.writeln('  });');
  out.writeln();

  out.writeln('  factory $name.fromJson(Map<String, dynamic> json) => $name(');
  for (final field in fields) {
    out.writeln('        ${field.name}: ${_readExpression(field)},');
  }
  out.writeln('      );');
  out.writeln();

  out.writeln('  Map<String, dynamic> toJson() => {');
  for (final field in fields) {
    out.writeln("        '${field.wireName}': ${_writeExpression(field)},");
  }
  out.writeln('      };');
  out.writeln('}');
  out.writeln();
}

bool _isNullable(Map<String, dynamic> schema) {
  final type = schema['type'];
  if (type is List && type.contains('null')) return true;
  final anyOf = schema['anyOf'];
  if (anyOf is List) {
    return anyOf.any((entry) => entry is Map && entry['type'] == 'null');
  }
  return false;
}

/// Unwraps `anyOf: [X, null]` and `type: [x, "null"]` down to the meaningful schema.
Map<String, dynamic> _unwrap(Map<String, dynamic> schema) {
  final anyOf = schema['anyOf'];
  if (anyOf is List) {
    final real = anyOf.firstWhere(
      (entry) => entry is Map && entry['type'] != 'null',
      orElse: () => <String, dynamic>{},
    );
    return (real as Map).cast<String, dynamic>();
  }
  return schema;
}

String _dartType(Map<String, dynamic> raw) {
  final schema = _unwrap(raw);

  final ref = schema[r'$ref'];
  if (ref is String) {
    final name = ref.split('/').last;
    return switch (name) {
      // Calendar date, deliberately a String — a DateTime would carry a timezone.
      'LocalDate' => 'String',
      'UUID' => 'String',
      'Instant' => 'DateTime',
      _ => name,
    };
  }

  var type = schema['type'];
  if (type is List) {
    type = type.firstWhere((entry) => entry != 'null', orElse: () => 'string');
  }

  switch (type) {
    case 'integer':
      return 'int';
    case 'number':
      return 'double';
    case 'boolean':
      return 'bool';
    case 'array':
      final items = (schema['items'] as Map<String, dynamic>? ?? {});
      return 'List<${_dartType(items)}>';
    case 'object':
      return 'Map<String, dynamic>';
    default:
      return schema['format'] == 'date-time' ? 'DateTime' : 'String';
  }
}

String _readExpression(_Field field) {
  final source = "json['${field.wireName}']";
  final type = field.type;

  String parse(String value) {
    if (type == 'DateTime') return 'DateTime.parse($value as String).toUtc()';
    if (type.startsWith('List<')) {
      final element = type.substring(5, type.length - 1);
      final elementParse = _isPrimitive(element)
          ? '(e as $element)'
          : '$element.fromJson(e as Map<String, dynamic>)';
      return '($value as List<dynamic>).map((e) => $elementParse).toList(growable: false)';
    }
    if (_isPrimitive(type)) return '$value as $type';
    if (type == 'Map<String, dynamic>') return '$value as Map<String, dynamic>';
    return '$type.fromJson($value as Map<String, dynamic>)';
  }

  if (field.nullable) {
    return '$source == null ? null : ${parse(source)}';
  }
  return parse(source);
}

String _writeExpression(_Field field) {
  final name = field.name;
  final type = field.type;
  final q = field.nullable ? '?' : '';

  if (type == 'DateTime') return '$name$q.toIso8601String()';
  if (type.startsWith('List<')) {
    final element = type.substring(5, type.length - 1);
    if (_isPrimitive(element)) return name;
    return '$name$q.map((e) => e.toJson()).toList(growable: false)';
  }
  if (_isPrimitive(type) || type == 'Map<String, dynamic>') return name;
  return '$name$q.toJson()';
}

bool _isPrimitive(String type) =>
    type == 'String' || type == 'int' || type == 'double' || type == 'bool';

/// `from` and `to` are Dart keywords-adjacent but legal; only genuine reserved words need work.
String _dartName(String wireName) => switch (wireName) {
      'default' => 'defaultValue',
      'is' => 'isValue',
      'in' => 'inValue',
      _ => wireName,
    };

class _Field {
  final String name;
  final String wireName;
  final String type;
  final bool nullable;

  _Field({
    required this.name,
    required this.wireName,
    required this.type,
    required this.nullable,
  });
}

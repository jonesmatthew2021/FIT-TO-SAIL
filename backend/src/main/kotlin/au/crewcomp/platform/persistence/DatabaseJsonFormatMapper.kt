package au.crewcomp.platform.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.hibernate.orm.JsonFormat
import io.quarkus.hibernate.orm.PersistenceUnitExtension
import jakarta.inject.Singleton
import org.hibernate.type.format.FormatMapper
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper

/**
 * How `jsonb` columns are serialised — with an `ObjectMapper` of its own, kept away from the REST one.
 *
 * Quarkus refuses to start without this decision being made explicitly, and it is right to. The
 * application's REST `ObjectMapper` is customised: `write-dates-as-timestamps` is off, and the MCP
 * extension registers a customiser of its own. Sharing it with the persistence layer would mean that
 * **a change to how the API renders JSON changes how the database stores it** — the same class of bug
 * as `audit_event.before_state` being `text` rather than `jsonb`, which this codebase already paid
 * for: a stored payload that does not round-trip is a payload whose meaning depends on the version of
 * the code that read it.
 *
 * So this is a plain, deliberately un-customised mapper. It serialises the two `jsonb` columns the
 * schema has — `app_config.value` and `evidence_document.extraction` — both of which hold plain
 * objects of strings, numbers and booleans. Nothing here needs date handling, and nothing here should
 * acquire any: a date belongs in a `date` column (NFR-5), not inside a JSON blob.
 *
 * Note what this does **not** touch. `audit_event.before_state`/`after_state` and
 * `evidence_document.extraction_raw` are `text`, written by explicit `writeValueAsString` calls
 * through the REST mapper, and hashed byte-for-byte (ADR 0007). They are attested bytes rather than
 * queryable structure, and they must not be routed through here or anywhere else that might
 * normalise them.
 */
@Singleton
@JsonFormat
@PersistenceUnitExtension
class DatabaseJsonFormatMapper : FormatMapper by JacksonJsonFormatMapper(ObjectMapper())

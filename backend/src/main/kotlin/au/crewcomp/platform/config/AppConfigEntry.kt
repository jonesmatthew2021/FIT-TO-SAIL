package au.crewcomp.platform.config

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * One row of `app_config` — the ADM-10 configuration store.
 *
 * The value column is `jsonb`, and it always holds an **object** with a single `value` member
 * rather than a bare scalar:
 *
 * ```json
 * {"value": 90}
 * ```
 *
 * That is a convention, not a limitation of the column: `jsonb` stores a bare `90` perfectly well.
 * It exists because the mapping below is `Map<String, Any?>`, which is the shape Hibernate's JSON
 * support handles without a custom type, and because wrapping means a setting that starts as a
 * number and grows into an object (a threshold that becomes per-requirement thresholds) is a
 * change to what is *inside* `value` rather than a change of the row's shape.
 */
@Entity
@Table(name = "app_config")
class AppConfigEntry {

    /**
     * A business key **as** the primary key, which is the one place §4's rule does not apply: this
     * is not a domain entity, it is a keyed settings store, and a surrogate id on it would buy
     * nothing but a second lookup.
     */
    @Id
    @Column(name = "key", nullable = false)
    lateinit var key: String

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value", nullable = false)
    var value: MutableMap<String, Any?> = mutableMapOf()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Column(name = "updated_by", nullable = false)
    var updatedBy: String = "system"
}

@ApplicationScoped
class AppConfigRepository : PanacheRepositoryBase<AppConfigEntry, String> {
    fun allOrdered(): List<AppConfigEntry> = listAll(io.quarkus.panache.common.Sort.by("key"))
}

package au.crewcomp.people

import au.crewcomp.platform.persistence.CreatedEntity
import au.crewcomp.platform.security.ScopeGuard
import au.crewcomp.reference.CrewChange
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * MOB-9 — a crew member's pre-sail declaration.
 *
 * The screen this comes from says "a false declaration is a disciplinary matter", and that sentence
 * is the specification for this class. It is why the record is write-once ([CreatedEntity]: a
 * declaration is not edited, and changing your mind is signing again for a later swing), why the
 * declarations confirmed are stored line by line rather than as a boolean, and above all why
 * [signedAt] is the **server's** clock.
 *
 * A device clock is settable by the person making the declaration. Accepting one would make the
 * timestamp on a legal record an assertion by the party it is evidence against, which is worth
 * nothing. The app knows this and deliberately prints no timestamp of its own — its signature block
 * says the office records the time it arrives.
 */
@Entity
@Table(name = "attestation")
class Attestation : CreatedEntity() {

    /** The device's queue-entry id, and the idempotency key (§7.6). Unique in the schema. */
    @Column(name = "op_id", nullable = false)
    lateinit var opId: String

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    lateinit var person: Person

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    lateinit var assignment: Assignment

    /**
     * Denormalised from the assignment, deliberately.
     *
     * A declaration is about the swing the person signed for. Re-rostering them afterwards must not
     * silently rewrite what they attested to, and following the assignment would do exactly that.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crew_change_id", nullable = false)
    lateinit var crewChange: CrewChange

    /** Set on arrival, by the server, always. */
    @Column(name = "signed_at", nullable = false)
    var signedAt: Instant = Instant.now()

    /**
     * The device spike's biometric assertion (ADR 0002), when there is one.
     *
     * Null on this build, and that is the honest state rather than an omission: there is no
     * `local_auth` binding, so the record means "confirmed on this device by the authenticated crew
     * member" — weaker than a signature, and stored as such.
     */
    @Column(name = "assertion")
    var assertion: String? = null

    /**
     * Which lines were ticked.
     *
     * Not a count and not a boolean: a crew member may legitimately sign with one line outstanding,
     * and *which* one is the whole content of the record. Eager because every read of an attestation
     * is a read of what it says.
     *
     * The ids are the client's today (`declarationsFor` in `action_screens.dart`). When the
     * declaration set becomes server-owned — handoff §2.5 — the **wording as presented** should be
     * stored alongside each id, because what someone signed is the sentence, not the key.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "attestation_declaration",
        joinColumns = [JoinColumn(name = "attestation_id")],
    )
    @Column(name = "declaration_id", nullable = false)
    var declarations: MutableSet<String> = mutableSetOf()

    /** §10.3 sync cursor, trigger-assigned — see [Person.updatedSeq]. */
    @Column(name = "updated_seq", insertable = false, updatable = false)
    var updatedSeq: Long = 0
}

@ApplicationScoped
class AttestationRepository(
    private val scopeGuard: ScopeGuard,
) : PanacheRepositoryBase<Attestation, Long> {

    /** The row a replayed operation already created, or null. Keyed on a client-minted UUID. */
    fun byOpId(opId: String): Attestation? = find("opId", opId).firstResult()

    /**
     * One crew member's own attestations, for their §10.3 snapshot.
     *
     * Fetch-joins the crew change because the DTO names the swing after this transaction closes.
     */
    fun forPersonScoped(personId: Long): List<Attestation> {
        scopeGuard.assertVisible(personId)
        return find(
            "from Attestation a join fetch a.crewChange where a.person.id = ?1 order by a.signedAt desc",
            personId,
        ).list()
    }
}

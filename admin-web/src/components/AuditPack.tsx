import { useState } from 'react'
import { api, type CrewChange, type Partnership, type SwingEvaluation } from '../api/client'
import { useAllHoldings, usePeople, useRequirements } from '../api/queries'
import { useSession } from '../api/session'
import { formatDate, formatDayMonth } from '../domain/dates'
import { needsAttention } from '../domain/enums'
import { saveReportPdf, type ReportGroup } from '../domain/report-pdf'

/**
 * The audit pack (OPS): one button that writes down what an AMSA, class or customer auditor
 * asks for on a swing — the verdict, every person's certificates with dates, the vessel's own
 * papers, the manning check and the shift rules — as a PDF.
 */
export function AuditPack({ ship, swing, evaluation }: { ship: Partnership; swing: CrewChange; evaluation: SwingEvaluation }): React.ReactNode {
  const session = useSession()
  const requirements = useRequirements()
  const people = usePeople()
  const holdings = useAllHoldings(evaluation.assignments.map((a) => a.personId))
  const [busy, setBusy] = useState(false)

  const build = async () => {
    setBusy(true)
    try {
      const requirementById = new Map((requirements.data ?? []).map((r) => [r.id, r]))
      const personById = new Map((people.data ?? []).map((p) => [p.id, p]))
      const [vessel, manning] = await Promise.all([api.vesselCertificates(ship.abbrev), api.manningCheck(ship.abbrev, swing.ccId)])
      const notClear = evaluation.assignments.filter((a) => a.evaluation.rollUp === 'gap')

      const groups: ReportGroup[] = [
        {
          heading: 'The swing',
          items: [
            {
              code: swing.ccId,
              title: `${swing.rotation !== null ? `Crew ${swing.rotation}` : swing.ccId} · onboard ${formatDate(swing.from)} to ${formatDate(swing.to)}`,
              status: notClear.length === 0 ? 'compliant' : `${notClear.length} not clear`,
              notes: [
                `${evaluation.assignments.length} rostered · ${evaluation.openSlots.length} open seats · ${evaluation.quotas.filter((q) => !q.satisfied).length} shift rules short · ${manning.short} manning positions short`,
              ],
            },
          ],
        },
        {
          heading: "The vessel's certificates",
          meta: `${vessel.length}`,
          items: vessel.map((c) => ({
            code: c.kind,
            title: `${c.title}${c.reference !== null ? ` · ${c.reference}` : ''}`,
            status: c.expiresOn === null ? 'no expiry' : `expires ${formatDate(c.expiresOn)}`,
            notes: [[c.issuer, c.issuedOn !== null ? `issued ${formatDate(c.issuedOn)}` : null, c.vesselName].filter(Boolean).join(' · ')],
          })),
        },
        {
          heading: 'Minimum safe manning',
          meta: manning.lines.length === 0 ? 'no table' : `${manning.short} short`,
          items: manning.lines.map((l) => ({
            code: '',
            title: `${l.positionName} · ${l.shift ?? 'whole swing'}`,
            status: `${l.standing} of ${l.required}`,
            notes: [l.names.join(', ') || 'nobody standing'],
          })),
        },
        {
          heading: 'Certificates required by shift',
          meta: `${evaluation.quotas.filter((q) => q.satisfied).length} met · ${evaluation.quotas.filter((q) => !q.satisfied).length} short`,
          items: evaluation.quotas.map((q) => ({
            code: requirementById.get(q.requirementId)?.code ?? `#${q.requirementId}`,
            title: `${requirementById.get(q.requirementId)?.title ?? ''} · ${q.shift ?? 'whole swing'}`,
            status: q.satisfied ? `met · ${q.actual} of ${q.min}` : `short · ${q.actual} of ${q.min}`,
            notes: [],
          })),
        },
        ...evaluation.assignments
          .slice()
          .sort((a, b) => a.name.localeCompare(b.name))
          .map((a) => {
            const person = personById.get(a.personId)
            const held = (holdings.byPerson.get(a.personId) ?? []).slice().sort((x, y) => (requirementById.get(x.requirementId)?.code ?? '').localeCompare(requirementById.get(y.requirementId)?.code ?? ''))
            return {
              heading: `${a.name} · ${person?.positionName ?? ''}`,
              meta: a.evaluation.rollUp,
              blurb: `Sam # ${a.sam} · seat ${String(a.slotRef).padStart(2, '0')} · ${formatDayMonth(a.from)} – ${formatDayMonth(a.to)}`,
              items: held.map((h) => {
                const cell = a.evaluation.cells.find((c) => c.requirementId === h.requirementId)
                return {
                  code: requirementById.get(h.requirementId)?.code ?? `#${h.requirementId}`,
                  title: requirementById.get(h.requirementId)?.title ?? '',
                  status: cell !== undefined && needsAttention(cell.state) ? cell.state : h.status === 'held_perpetual' ? 'held' : h.status.replace('_', ' '),
                  notes: [[h.issueDate !== null ? `issued ${formatDate(h.issueDate)}` : null, h.expiry !== null ? `expires ${formatDate(h.expiry)}` : null].filter(Boolean).join(' · ')],
                }
              }),
            }
          }),
      ]

      await saveReportPdf({
        title: `Audit pack — ${ship.abbrev} ${formatDayMonth(swing.from)} – ${formatDayMonth(swing.to)}`,
        subtitle: `${ship.name} · prepared ${formatDate(session.today)} by ${session.label}`,
        intro: `Everything an auditor asks for on this swing, as the records stood on ${formatDate(session.today)}: the swing's verdict, the vessel's own certificates, the manning check, the shift rules, and every rostered crew member's certificates with their dates.`,
        filename: `audit-pack-${ship.abbrev}-${swing.from}.pdf`,
        groups,
      })
    } finally {
      setBusy(false)
    }
  }

  return (
    <button type="button" className="button" disabled={busy || holdings.isPending} onClick={() => void build()} title="Everything an auditor asks for on this swing, as one PDF">
      {busy ? 'Writing…' : 'Audit pack (PDF)'}
    </button>
  )
}

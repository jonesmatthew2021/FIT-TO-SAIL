# Vessel portals — Matt's Coolibah portal as a per-company template

This folder makes Matt's TSV Coolibah crew portal a **template**: every company
FIT TO SAIL takes on can be given its own portal, branded for its vessel,
running on its own free Cloudflare worker with its own crew password, its own
file store (the company's SharePoint once connected, Cloudflare's R2 until
then) and its own AI reading account. The Coolibah portal is the first
instance; its live address is in `vessels/tsv-coolibah.json`.

**Nothing here replaces anything in CREWCOMP, and that is the point.** The
portal and CREWCOMP read the same crew certificates through two independently
written systems — Matt's certification checkers in the portal, the compliance
engine here — so each is a cross-check on the other. Where the two disagree
about a person's standing, one of them has found a mistake worth a look.
CREWCOMP's engine, screens and pipeline stay exactly as they are.

## What's in here

| Path | What |
|---|---|
| `template/portal.html` | The portal page, verbatim from Matt's portal build (`source/index.html` there). All of its code is present and untouched — the build only stamps the vessel identity block. |
| `template/crew-list-form.html` | The Portways crew list & shift allocation form the page opens pre-filled. |
| `vessels/*.json` | One file per vessel: the branding block and where that vessel's portal lives. |
| `build.mjs` | Stamps a vessel config into the template → `dist/<vessel-id>/`. |

## Minting a portal for a new company

1. Copy `vessels/_new-vessel.json.example` to `vessels/<vessel-id>.json` and
   fill it in (operator, vessel name, strapline).
2. `node build.mjs <vessel-id>` — writes `dist/<vessel-id>/index.html` and the
   crew list form beside it.
3. Deploy it as that vessel's own worker following `worker/DEPLOY.md` in
   Matt's portal build repo: a fresh worker name, its own D1 database, R2
   bucket, crew password, and (when the company's IT grants it) its own
   SharePoint app registration. The stamped page from step 2 is that worker's
   `assets/index.html`.
4. Add the deployed address to the vessel's json (`portalUrl`) and to
   `admin-web/src/domain/vesselPortals.ts`, so the company's ship card in
   CREWCOMP links to it.

Each portal is a separate deployment on purpose: a company's crew data,
password, files and AI spend never share anything with another company's.

## The admin-web link

`admin-web/src/domain/vesselPortals.ts` maps a partnership's abbreviation to
its portal address, and the Company screen shows a "Crew portal" button on
ships that have one. The map is a deliberate interim: when a portal address
becomes a server-side field on the partnership (a one-line entity change and a
migration), the map goes away and the link reads the generated type like
everything else. Kept client-side for now so this branch adds no schema
changes without Chris's eyes on them.

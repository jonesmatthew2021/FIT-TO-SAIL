# deploy/ — the persistent instance on a Tailscale node

One long-lived box running the real artefacts against a real PostgreSQL, reachable at the node's
tailnet name and rebuilt from a checkout with one command. It exists because `scripts/dev-start.sh`
is deliberately impermanent — Ryuk reaps its database on every stop, which is the right property
for a development loop and the wrong one for something you want to show somebody twice.

**This is not `infra/` and it is not the deploy lane.** ADR 0005 has not chosen a platform and
ADR 0004 puts the deploy lane behind that verdict; writing a cloud stack now would be writing the
thing the spike is meant to decide. This is three containers on a machine you own, with no cloud
service anywhere in it — so it stays clear of that rule rather than pre-empting it.

## What it means that there is no login

There is no login. ADR 0003's BFF session is the identity spike's deliverable, so the only way to
have a *usable* running instance is one carrying the development authentication shim — which
authenticates every request and grants it the roles in `CREWCOMP_DEV_AUTH_DEFAULT_ROLES`.

**Anyone who can reach this box is a Compliance Lead.** They can publish a matrix version, approve
an exemption, and edit anybody's holdings. There is no password to get wrong because there is no
password.

So the Tailscale ACL is the entire access control, and it should be a named grant rather than a
tailnet-wide one:

```jsonc
// tailnet policy file — grant the node to the people who should have it, not to tailnet:members
{
  "acls": [
    { "action": "accept", "src": ["you@example.com"], "dst": ["chris-hp:443"] }
  ]
}
```

Two things follow, and both are already true of what is in this directory:

- **`tailscale serve`, never `tailscale funnel`.** Serve is tailnet-only. Funnel publishes to the
  public internet, and on this box that would be publishing an unauthenticated admin console.
- **Nothing binds a routable interface.** The database and the backend publish no host port at all;
  Caddy publishes `127.0.0.1:8081`. The only path in is `tailscale serve` on the host, so a wrong
  ACL is a second mistake rather than the first one.

The artefact itself is honest about what it is: `crewcomp.dev-auth.enabled` is a **build**-time
property, so the shim is compiled into this image and could not be added to a production one by a
runtime override. An image built from `backend.Dockerfile` is a demo image by construction.

## What the box needs

Docker with the Compose v2 plugin, git, and enough memory to *compile* — which is the binding
constraint, not running. The backend image builds Kotlin, and the Kotlin compiler dies with
"Internal Kotlin compilation error" rather than anything that says "out of memory": a 1 GiB VM
fails, ~4 GB is comfortable. Running the built stack wants far less. If the box is small, give it
swap before concluding the Dockerfile is wrong.

Disk: a couple of gigabytes for images and the Maven dependency layer, plus the database volume.

## Bringing it up

On the Linux box, once:

```bash
sudo apt install docker.io docker-compose-v2 git      # or the distro's equivalent
sudo usermod -aG docker "$USER" && newgrp docker

git clone <this repo> ~/attest && cd ~/attest/deploy
cp .env.example .env
$EDITOR .env                                          # POSTGRES_PASSWORD at minimum

./rebuild.sh --no-pull                                # builds everything, waits until the API answers
```

**No `sudo tailscale serve` step, and no host port.** The stack carries its own tailnet node: the
`ts-attest` sidecar joins as the device `attest`, and `web` runs inside that container's network
namespace, so Caddy's `:8081` exists only in there. Tailscale provisions a real Let's Encrypt
certificate for the node's name, so the address is:

```
https://attest.<tailnet>.ts.net
```

Two reasons it is a sidecar rather than `tailscale serve` on the host, and neither is aesthetic:

- **The host's own 443 is taken.** `chris-hp` already runs pihole there, plus adminer on 8080 and
  a PostgreSQL on 5432. Serving from the host meant a non-default port (`:8443`) and a name shared
  with everything else on the box. Its own node gets its own name and a clean 443.
- **A device is the unit Tailscale shares.** An app alone inside a machine's network namespace can
  be shared with somebody outside the tailnet, giving them that app and nothing else on the box —
  without inviting them into the tailnet at all. See "Sharing it outside the tailnet" below.

Nothing in this stack binds a host port now: not the database, not the backend, not Caddy. Which
is also why nothing collided with the three services already on that box.

## Rebuilding

```bash
cd ~/attest/deploy
./rebuild.sh                 # pull, rebuild, restart, block until /health/ready answers
./rebuild.sh --web           # just the SPA
docker compose logs -f backend
```

**A rebuild does not touch the data.** New code runs its Flyway migrations against the database
that is already there, and `rebuild.sh` blocks on the backend's healthcheck, so a migration that
fails shows up as a failed command with the logs printed — not as a screen that half-loads later.

That is worth more than convenience: this is the first place the forward-only, expand/contract
rule is *enforced* rather than asserted. A migration that is not backward-compatible with the
previous revision, or an edit to one that has already run, fails start-up here. `./reset.sh` is
the way out, and taking it is the signal that a migration needs rewriting rather than retrying.

## The dataset

`CREWCOMP_DEV_SEED_DATASET` is read **only when the database is empty** — the seeder refuses one
that already holds people, because §11 requires that the synthetic and extracted catalogues never
mix. So the dataset is chosen at the moment the volume is created and changing the variable alone
does nothing.

The box seeds **synthetic** — invented crew, safe for any audience.

### Switching to the extracted dataset

The extracts are the POC's real workbook data: real crew names, Sam numbers, expiry dates. On this
box they would be at rest on the disk, readable by anyone the ACL admits, behind a shim that trusts
every request. That is a deliberate decision with a tail, not a flag:

```bash
scp -r ~/shipping/{seed,exceptions.csv} chris-hp:~/attest/deploy/extracts/   # from the Mac
cd ~/attest/deploy
./backup.sh                                       # if anything in the current database matters
sed -i 's/^CREWCOMP_DEV_SEED_DATASET=.*/CREWCOMP_DEV_SEED_DATASET=extracted/' .env
./reset.sh                                        # destroys the volume, re-seeds from the extracts
```

Before doing it: narrow the ACL to the people attending the demonstration, and afterwards run
`./reset.sh` back to `synthetic` and delete `deploy/extracts/` rather than leaving it there. The
directory is gitignored, so the extracts can never be committed from here — but that is the only
protection this repository can give them.

### Switching to the portal dataset

The portal snapshot is the Coolibah crew portal's live data — real crew names, employee ids,
certificate expiries — so everything above about the extracts applies verbatim: at rest on the
disk, readable by anyone the ACL admits, reset back and delete when the audience is gone. The
loader needs only `portal-state.json`; refresh the snapshot on the Mac first
(`./scripts/portal-snapshot.sh`), then:

```bash
scp ~/coolibah-portal/latest/portal-state.json chris-hp:~/attest/deploy/portal/   # from the Mac
cd ~/attest/deploy
./backup.sh                                       # if anything in the current database matters
sed -i 's/^CREWCOMP_DEV_SEED_DATASET=.*/CREWCOMP_DEV_SEED_DATASET=portal/' .env
./reset.sh                                        # destroys the volume, re-seeds from the snapshot
```

Loading a *newer* snapshot is the same procedure from the `scp` line — the seeder refuses a
populated database, so a refresh is a reset, and whatever anyone entered through the UI since the
last seed goes with it. The published matrix's label says which revision the box is showing
(`Coolibah portal rev N`, on the ADM-3 screen). `docs/handoff/coolibah-portal-dataset.md` is the
map of the source and the mapping decisions.

## Showing one role

The SPA is a **production** bundle, so `import.meta.env.DEV` is false and the dev sign-in and
"Switch role" button are compiled out of it — the same rule the backend follows, held to on the
client. The box therefore presents whatever `CREWCOMP_DEV_AUTH_DEFAULT_ROLES` says, and that is a
runtime property:

```bash
sed -i 's/^CREWCOMP_DEV_AUTH_DEFAULT_ROLES=.*/CREWCOMP_DEV_AUTH_DEFAULT_ROLES=data_steward/' .env
docker compose up -d backend        # a few seconds
```

Never set it empty: the session call would 401 and the SPA would render its sign-in screen, whose
only button points at the BFF the identity spike has not built yet.

Person-scoped views (`X-Dev-Person-Id`) have no configuration equivalent — they are a request
header, so the crew app can set them and the browser cannot.

## The crew app against this box

This is the first place the Flutter app talks real TLS over a real network rather than loopback to
a simulator — `mobile/CLAUDE.md` records "nothing enforces TLS" as a known gap, and a tailnet host
is the cheapest honest answer to it. With Tailscale installed and signed in on the phone:

```bash
cd mobile
flutter run --dart-define=CREWCOMP_API=https://attest.<tailnet>.ts.net \
            --dart-define=CREWCOMP_DEV_PERSON=<a person id in the seeded dataset>
```

**Debug mode, not release.** The app's dev identity sits behind `kDebugMode`, so a release build
sends no `X-Dev-*` headers and every call answers 401. That is the guarantee working correctly; it
just means device testing runs a debug build until the identity spike lands.

What this makes reachable that a simulator could not: sync and the resumable evidence upload over a
network that genuinely drops, from a device with a real camera, against an instance whose data is
still there tomorrow.

## What is deliberately not here

- **MCP is off** (`%demo.crewcomp.mcp.enabled=false`). Nothing off-box is meant to reach this
  instance's MCP server; turning it on is a deliberate act, with a token to go with it.
- **The dev extractor is off**, unlike `%dev`. A box other people look at should not appear to
  extract certificate fields when §14.5 has chosen no provider. Empty extractions landing in
  ADM-9's queue for a human is LLM-2's launch posture and is what this box should be seen doing.
- **No TLS inside the box, no certificates in this directory.** `tailscale serve` terminates HTTPS
  and forwards over loopback; Caddy runs with `auto_https off` so it never tries to provision one.
- **No monitoring, no log shipping, no secret store.** Those are the platform spike's, and every
  one of them is a decision ADR 0005 owns.

## What has been verified, and what has not

Verified on a Mac against a real PostgreSQL 16 before any of this reached the box:

- the `demo` profile packages, starts, migrates all 11 Flyway migrations and seeds the synthetic
  dataset;
- `GET /api/v1/session` answers 200 with the four back-office roles and no headers, so the
  production SPA bundle signs in;
- a crew-shaped request (`X-Dev-Roles: crew_member`, `X-Dev-Person-Id`) gets a sync snapshot, so
  the phone works against it;
- `/mcp` answers 404, so `%demo`'s MCP gate holds;
- **an audited write survives a full stop and restart of the artefact**, and the seeder then skips
  the populated database ("Development seed skipped: 11 people already present") — which is the
  one property this whole directory exists for;
- Caddy serves the SPA's client-side routes, caches fingerprinted assets hard and the document not
  at all, and routes `/api` and `/health` to the backend rather than rewriting them to index.html.

Not verified: the backend **image** build, because the Mac's container VM has 1 GiB and the Kotlin
compiler needs more (see above). Everything it runs was run outside a container first, and the
runtime stage is Quarkus's own fast-jar layout — but expect the first `./rebuild.sh` on the box to
be where that step is genuinely proven, and read its output rather than assuming.

## Sharing it outside the tailnet

Because the app has its own node, it can be shared with a Tailscale account outside the tailnet —
admin console, Machines, `attest`, **Share**. The recipient gets that one machine and nothing else
on the box, does not join the tailnet, does not count against its user total, and cannot initiate
connections back (shared machines are quarantined by default). They reach it at the full name in
*this* tailnet's domain, `https://attest.<tailnet>.ts.net`.

Access is still governed by this tailnet's policy, so the grant is written here:

```jsonc
{ "src": ["autogroup:shared"], "dst": ["attest"], "ip": ["443"] }
```

Two things to do once, in the admin console:

- **Disable key expiry on the `attest` device.** It is registered as a user-owned node — untagged
  on purpose, because only users can accept a machine share and Tailscale's wording on whether a
  *tagged* machine can be shared reads both ways. The cost of untagged is a 90-day key expiry that
  would take the share down with it.
- **Time-box any external share.** This box has no login (see the top of this file), so whoever
  accepts the share is a Compliance Lead. That is defensible for a demonstration you are driving
  and not for standing access; revoke it when the demonstration is over.

## Backups

`./backup.sh` writes a `pg_dump -Fc` into `deploy/backups/` and prunes past a fortnight. From cron:

```
15 2 * * *  /home/<you>/attest/deploy/backup.sh >/dev/null 2>&1
```

Worth doing precisely because this box is not the dev stack: the value in it is whatever people
have done on it since the last seed, and nothing else in this repository is holding a copy.

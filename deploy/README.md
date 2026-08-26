# deploy/ — the persistent instance on a Tailscale node

One long-lived box running the real artefacts against a real PostgreSQL, reachable at the node's
tailnet name, pulling its own code from GitHub and operated entirely over ssh — `ssh chris-hp
attest update`, and nothing needed from a development machine. It exists because `scripts/dev-start.sh`
is deliberately impermanent — Ryuk reaps its database on every stop, which is the right property
for a development loop and the wrong one for something you want to show somebody twice.

**This is not `infra/` and it is not the deploy lane.** ADR 0005 has not chosen a platform and
ADR 0004 puts the deploy lane behind that verdict; writing a cloud stack now would be writing the
thing the spike is meant to decide. This is three containers on a machine you own, with no cloud
service anywhere in it — so it stays clear of that rule rather than pre-empting it.

## Operating it remotely

Everything below can be done with one command from anywhere with ssh access to the box — a
laptop, a phone, a machine that has never seen this repository:

```bash
ssh chris-hp attest status                          # code, containers, dataset, identity, disk
ssh chris-hp attest update                          # pull main, rebuild, restart, wait for /health/ready
ssh chris-hp attest dataset portal --refresh --yes  # re-snapshot Matt's portal and re-seed from it
ssh chris-hp attest dataset synthetic --yes         # back to invented crew when the audience leaves
ssh chris-hp attest reset --yes                     # same dataset, empty database
ssh chris-hp attest backup                          # database dump + the sidecar's tailnet identity
ssh chris-hp attest roles data_steward              # what an unheadered request is granted
ssh chris-hp attest logs backend                    # follow (needs a terminal; ctrl-c to stop)
```

`deploy/attest.sh` is the implementation and `~/bin/attest` on the box is a symlink to it, so it
updates itself with the checkout. It delegates to `rebuild.sh`, `reset.sh`, `backup.sh` and
`scripts/portal-snapshot.sh` rather than duplicating them; what it adds is the three things a
one-shot ssh command needs and those scripts cannot assume:

- **no terminal.** `reset.sh` asks you to type `destroy`, which nothing can answer over
  `ssh host cmd`. The destructive verbs take `--yes` instead, and still print what they are about
  to destroy and whose data it is.
- **an environment that is actually complete.** Every key in `.env.example` must be present in
  `.env`; `attest` refuses to run until they are and `attest env --fix` appends the rest. A key
  that is merely *absent* gets compose's default silently, which is how the portal snapshot came
  to be mounted from inside the repository — `CREWCOMP_PORTAL_ROOT` was never added, so `./portal`
  won.
- **the identity preflight** described under "The tailnet identity" below.

Nothing here needs anything from a development machine. The box pulls its own code from GitHub
over a read-only deploy key, and takes its own snapshot of the Coolibah portal over the tailnet.

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
sudo apt install docker.io docker-compose-v2 git curl python3   # or the distro's equivalent
sudo usermod -aG docker "$USER" && newgrp docker

# Read-only git access, so the box can fetch its own code and can never push. Paste the public
# key as a DEPLOY KEY on the repository (Settings -> Deploy keys), with write access UNCHECKED.
ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519_attest_deploy -N "" -C "attest deploy key (read-only)"
cat ~/.ssh/id_ed25519_attest_deploy.pub
cat >> ~/.ssh/config <<'EOF'
Host github.com
  HostName github.com
  User git
  IdentityFile ~/.ssh/id_ed25519_attest_deploy
  IdentitiesOnly yes
EOF
ssh-keyscan -t ed25519 github.com >> ~/.ssh/known_hosts

git clone git@github.com:cdjones32/attest.git ~/attest && cd ~/attest/deploy
cp .env.example .env
$EDITOR .env                                          # POSTGRES_PASSWORD and TS_AUTHKEY at minimum
./attest.sh install                                   # ~/bin/attest, on PATH for `ssh host attest ...`
./rebuild.sh --no-pull                                # builds everything, waits until the API answers
```

A read-only deploy key rather than a personal access token: it is scoped to this repository, has
no expiry to renew, and cannot push — the box should never be a source of commits. `attest
install` puts the PATH line at the *top* of `~/.bashrc`, above the guard that returns early for
non-interactive shells, because `ssh host cmd` is exactly such a shell.

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
ssh chris-hp attest update            # from anywhere
ssh chris-hp attest update --web      # just the SPA

cd ~/attest/deploy                    # or on the box itself, the same thing
./rebuild.sh
docker compose logs -f backend
```

**A rebuild does not touch the data.** New code runs its Flyway migrations against the database
that is already there, and `rebuild.sh` blocks on the backend's healthcheck, so a migration that
fails shows up as a failed command with the logs printed — not as a screen that half-loads later.

That is worth more than convenience: this is the first place the forward-only, expand/contract
rule is *enforced* rather than asserted. A migration that is not backward-compatible with the
previous revision, or an edit to one that has already run, fails start-up here. `attest reset
--yes` is the way out, and taking it is the signal that a migration needs rewriting rather than retrying.

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

The extracts have no network source to pull from, so they are copied to the box **once**, and to
a directory outside the checkout — `CREWCOMP_EXTRACT_ROOT` in `.env` points at it. Real crew data
does not belong in a working tree, gitignored or not:

```bash
ssh chris-hp 'mkdir -p ~/attest-extracts && chmod 700 ~/attest-extracts'   # see below
rsync -a ~/shipping/{seed,exceptions.csv} chris-hp:~/attest-extracts/      # once, from the Mac
```

Make the directory first: compose mounts `CREWCOMP_EXTRACT_ROOT` whatever the dataset is, and a
bind-mount source that does not exist is created **by the daemon, owned by root** — so the rsync
would then fail on a directory in your own home that you cannot write to.

After that the switch is a remote command, and the copy stays put:

```bash
ssh chris-hp attest backup                      # if anything in the current database matters
ssh chris-hp attest dataset extracted --yes     # destroys the volume, re-seeds from the extracts
```

Before doing it: narrow the ACL to the people attending the demonstration. Afterwards
`ssh chris-hp attest dataset synthetic --yes`, and delete `~/attest-extracts` on the box rather
than leaving it there — this repository cannot protect data that sits beside it.

### Switching to the portal dataset

The portal snapshot is the Coolibah crew portal's live data — real crew names, employee ids,
certificate expiries — so everything above about the extracts applies verbatim: at rest on the
disk, readable by anyone the ACL admits, reset back and delete when the audience is gone. The
loader needs only `portal-state.json`; refresh the snapshot on the Mac first
(`./scripts/portal-snapshot.sh`), then:

Unlike the extracts, this one has a live source, and the box is on the same tailnet as it — so the
box takes its own snapshot and no laptop is involved:

```bash
ssh chris-hp attest backup                             # if the current database matters
ssh chris-hp attest dataset portal --refresh --yes      # snapshot, then destroy and re-seed
```

`--refresh` runs `scripts/portal-snapshot.sh` on the box first, into `~/coolibah-portal` — outside
the checkout, where the script itself insists it goes. Drop `--refresh` to re-seed from the
snapshot already there; `attest snapshot` takes one without touching the database. Loading a
*newer* snapshot is a reset either way: the seeder refuses a populated database, so whatever
anyone entered through the UI since the last seed goes with it.

The one thing this cannot do by itself is reach a portal that is offline — Matt's machine is a
laptop, and `attest snapshot` says so plainly rather than seeding from nothing. The published matrix's label says which revision the box is showing
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

Verified **on the box, over ssh from a Mac that did nothing but issue the commands** (27 August
2026):

- `attest update` — a `git pull` over the deploy key, both images rebuilt, the stack restarted, and
  `tailscale serve status` reported. The backend **image** build is therefore proven now; it was
  the one thing the note below could not verify.
- `attest dataset portal --yes` — the database volume destroyed and re-seeded from the snapshot
  outside the checkout, 40 people, **and the tailnet node still called `attest` afterwards**, which
  is the property the whole identity section exists for.
- `attest backup`, then `attest restore <dump>` — proven by the restored database carrying the
  06:15, 06:30 and 06:45 scan events from *before* the reset, which a freshly seeded one could not
  have.
- `attest identity --restore <tarball>` — the sidecar's state replaced from a tarball and the node
  coming back on the same name and the same 100.68.76.93. A tarball with no `tailscaled.state` in
  it is refused before anything is removed.
- The identity **preflight**, against a throwaway project with a database volume and no ts-state:
  it refuses to start rather than register `attest-1`.
- `attest env` on the real `.env` — which is how the missing `CREWCOMP_PORTAL_ROOT` was found.
- `attest dataset extracted --yes` with no extracts on the box: refused, and `.env` left alone.
- `attest roles data_steward` and back, each confirmed through `GET /api/v1/session` over the
  tailnet.
- `attest snapshot` with the portal offline: it says so in 10 seconds and changes nothing.

Not verified: the snapshot **success** path from the box (Matt's laptop was off all morning), and
whether the four containers come back after a reboot — `restart: unless-stopped` and an enabled
`docker.service` say they will, and nobody has yet rebooted the box to watch it happen.

Verified earlier, on a Mac against a real PostgreSQL 16 before any of this reached the box:

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

At that time the backend **image** build was the one unverified step, because the Mac's container
VM has 1 GiB and the Kotlin compiler needs more. The box has since built it repeatedly, most
recently through `attest update` — so that caveat is closed.

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

`attest backup` writes a `pg_dump -Fc` **and** a tarball of the sidecar's tailnet identity into
`deploy/backups/` (mode 700), pruning both past a fortnight. From cron on the box:

```
15 2 * * *  /home/<you>/bin/attest backup >/dev/null 2>&1
```

Worth doing precisely because this box is not the dev stack: the value in it is whatever people
have done on it since the last seed, and nothing else in this repository is holding a copy.

The identity half matters for a different reason — see below. A database can be re-seeded from a
dataset; a tailnet identity cannot be re-created at all.

## The tailnet identity

The `attest` device is not configuration, it is state: it lives entirely in the `crewcomp_ts-state`
volume. Destroy that volume and tailscaled registers a **new** device, which takes the name
`attest-1` — so `attest.<tailnet>.ts.net` stops resolving, the certificate is for a name nothing
answers on, and any machine share pointing at the old device is orphaned. It has happened once:
`reset.sh` used to `docker compose down --volumes`, which took the identity along with the
database it meant to destroy.

Four things now stand between that and happening again:

- **`reset.sh` removes `crewcomp_db-data` by name**, and nothing in `attest` passes `--volumes`.
- **`attest backup` saves the identity** as `backups/ts-state-<date>.tar.gz`, and
  `attest identity --restore <file> --yes` puts it back. That turns the identity from
  irreplaceable into merely important.
- **`attest` refuses to start a stack that would rename the node.** If the database volume exists
  and `ts-state` does not, the identity has already been lost — so it stops and tells you to
  restore rather than starting a stack that quietly re-registers.
- **`attest status` asserts the node still calls itself `attest`**, so a broken share is something
  you find rather than something the person you shared it with finds.

Two properties of the node itself are deliberate, and both were verified in the admin console on
27 August 2026:

- **Untagged, and key expiry disabled.** Tagging would disable key expiry automatically, but
  Tailscale is explicit that a tagged machine cannot be shared and that sharing strips tags — and
  being shareable is the whole reason this app has its own node. So it stays untagged and the
  expiry is switched off by hand.
- **A reusable, non-ephemeral auth key.** An ephemeral node is deleted from the tailnet shortly
  after it goes offline, which would lose the name and the share every time the stack stopped.
  The key is only read when there is no state to reuse, but a reusable key's ceiling is 90 days —
  so `.env` records `TS_AUTHKEY_ISSUED` and `attest status` warns past 80.

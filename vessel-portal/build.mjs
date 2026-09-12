/* Stamps a vessel's identity into the portal template.
 *
 *   node build.mjs tsv-coolibah
 *
 * Reads vessels/<id>.json, replaces the template's VESSEL block — the one
 * place the page states whose portal it is — and writes the result to
 * dist/<id>/ beside a copy of the crew list form. Everything else in the
 * template is left byte-for-byte as Matt's portal build produced it: the
 * template is a copy, not a fork, and it is refreshed by re-copying
 * source/index.html from the portal repo, never by editing here.
 */

import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const id = process.argv[2];
if (!id) {
  console.error("Which vessel? node build.mjs <vessel-id>   (see vessels/)");
  process.exit(1);
}

const vessel = JSON.parse(readFileSync(join(HERE, "vessels", `${id}.json`), "utf8"));
const template = readFileSync(join(HERE, "template", "portal.html"), "utf8");

// The VESSEL block is the page's one statement of identity. Matched as a
// block, replaced whole; if the template's shape ever changes this fails
// loudly rather than stamping a half-branded page.
const block = /const VESSEL = \{[\s\S]*?\};/;
if (!block.test(template)) {
  console.error("The template has no VESSEL block — was template/portal.html refreshed from a changed source?");
  process.exit(1);
}
const stamped = template.replace(
  block,
  `const VESSEL = {
  operator: ${JSON.stringify(vessel.operator)},
  name: ${JSON.stringify(vessel.name)},
  nameAccent: ${JSON.stringify(vessel.nameAccent)},
  strapline: ${JSON.stringify(vessel.strapline)},
};`,
);

const out = join(HERE, "dist", id);
mkdirSync(out, { recursive: true });
writeFileSync(join(out, "index.html"), stamped);
copyFileSync(join(HERE, "template", "crew-list-form.html"), join(out, "crew-list-form.html"));
console.log(`${vessel.name} ${vessel.nameAccent} stamped → dist/${id}/ (deploy per worker/DEPLOY.md in the portal repo)`);

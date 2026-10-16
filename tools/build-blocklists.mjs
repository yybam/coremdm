// Builds the DNS filter's blocklists from the hand-maintained hosts file.
//
//   node tools/build-blocklists.mjs
//
// Reads  source/hosts-original.txt
// Writes app/src/main/assets/blocklists/<group>.txt  (one domain per line, lowercase, sorted, unique)
//        tools/needs-review.txt                     (lines we refuse to guess at — NOT blocked)
//
// "*.example.com" means example.com and every subdomain; everything else is an exact match.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const SOURCE = path.join(ROOT, "source", "hosts-original.txt");
const OUT_DIR = path.join(ROOT, "app", "src", "main", "assets", "blocklists");
const REVIEW_FILE = path.join(ROOT, "tools", "needs-review.txt");

const GROUPS = [
  "ads", "analytics", "gifs-images", "social", "video",
  "spotify-music", "google-apple-microsoft", "app-help-pages", "cloud-infra", "other-sites",
];

// ── Cleaning ──────────────────────────────────────────────────────────────────

// Hosts-file IPs, including the "127.0.01" typo. Splitting on these also un-sticks lines like
// "unicornplatform.com127.0.0.1 secure.splitwise.com" and "127.0.0.1encrypted-tbn0.gstatic.com".
const IP_RE = /127\.0\.0\.1|127\.0\.01|0\.0\.0\.0|::1/;
const IP_SPLIT_RE = new RegExp(IP_RE.source, "g");

const DROP_EXACT = new Set([
  "localhost", "ip6-localhost",
  "newsite.com", "test.test.test", "test.bupkah.test", "testing.test", "just.a.test",
]);
const DROP_PATTERNS = [/^just\.[a-z]+\.testing\.final$/, /^just\.testing\.final$/];

// Owner decisions (2026-10-05) for lines that used to land in needs-review.txt. Keyed by the
// entry lowercased, without http(s):// and trailing "/". A string replaces the entry; null skips it.
const CORRECTIONS = new Map([
  ["g.ten toor.com", "g.tenor.com"],
  ["wwww.image-cdn-ak.spotifycdn.com", "www.image-cdn-ak.spotifycdn.com"],
  ["wwww.moovit.com", "www.moovit.com"],
  ["wwww.xlb.uber.com", "www.xlb.uber.com"],
  ["www.video-akpcw-cdn-spotify-com.akamaized.ne", "www.video-akpcw-cdn-spotify-com.akamaized.net"],
  ["video-akpcw-cdn-spotify-com.akamaized.ne", "video-akpcw-cdn-spotify-com.akamaized.net"],
  ["www.google.com/maps", null],          // would block all of Google Search; maps.google.com is listed
  ["about.google/products", "about.google"],
  ["www.pandora.com/static/promo", null], // would block Pandora's whole site; its ad hosts are listed
  ["i.scdn.co/image", null],              // i.scdn.co is already listed
]);
// Matched by prefix: the query string after it is irrelevant to DNS.
const PREFIX_CORRECTIONS = [["t3.gstatic.com/images?", "t3.gstatic.com"]];

function applyCorrection(text) {
  const key = text.toLowerCase().replace(/^https?:\/\//, "").replace(/\/$/, "");
  if (CORRECTIONS.has(key)) return { hit: true, value: CORRECTIONS.get(key) };
  for (const [prefix, value] of PREFIX_CORRECTIONS) if (key.startsWith(prefix)) return { hit: true, value };
  return { hit: false };
}

const LABEL_RE = /^[a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?$/;

function isValidHostname(host) {
  if (host.length > 253 || !host.includes(".")) return false;
  const labels = host.split(".");
  if (/^\d+$/.test(labels[labels.length - 1])) return false; // an IP, not a name
  return labels.every((l) => LABEL_RE.test(l));
}

/**
 * Cleans one entry (the text after an IP). Returns
 *   { domain, wildcard }       — usable
 *   { drop: reason }           — intentionally discarded (localhost, test entries)
 *   { review: reason }         — we won't guess; goes to needs-review.txt
 */
function cleanEntry(raw) {
  let text = raw.trim();
  const fix = applyCorrection(text);
  if (fix.hit) {
    if (fix.value === null) return { drop: "owner decision: skip" };
    text = fix.value;
  }
  if (/\s/.test(text)) return { review: "space inside the domain" };

  let s = text.toLowerCase().replace(/^https?:\/\//, "");

  const cut = s.search(/[/?#]/);
  if (cut !== -1) {
    const rest = s.slice(cut);
    if (rest !== "/") return { review: "has a path or query — not turning it into a bare domain" };
    s = s.slice(0, cut);
  }
  s = s.replace(/:\d+$/, "");

  const wildcard = s.startsWith(".");
  if (wildcard) s = s.slice(1);
  s = s.replace(/\.$/, "");

  if (DROP_EXACT.has(s) || DROP_PATTERNS.some((re) => re.test(s))) return { drop: "localhost/test entry" };
  if (s.startsWith("wwww.")) return { review: 'starts with "wwww." (typo?)' };
  if (!isValidHostname(s)) return { review: "not a valid hostname" };
  if (s.endsWith(".ne")) return { review: 'ends in ".ne" — looks like a cut-off ".net"' };

  return { domain: s, wildcard };
}

// ── Grouping ──────────────────────────────────────────────────────────────────

const under = (d, ...bases) => bases.some((b) => d === b || d.endsWith("." + b));
const labels = (d) => d.split(".");
// Second-level label, e.g. "google" in "lh3.google.com" (good enough for the domains in this file).
const sld = (d) => { const l = labels(d); return l[l.length - 2] ?? ""; };

const AD_LABELS = new Set([
  "ads", "ad", "pagead2", "pubads", "adservices", "adwords", "adclick",
  "googleads", "googleads2", "googleads4", "googleadsense", "gads",
]);
const GIF_IMAGE_RE =
  /gif|giph|tenor|imgur|pinimg|tumblr|gfycat|image|img|pic|clipart|wallpaper|favim|pixabay|wixmp|squarespace-cdn|imgix|cloudinary|narvii|chzbgr|yarn\.co|behance|artstation|dribbble|deviantart|pixilart|monophy|pngsumo|quotesgram|bp\.blogspot\.com|files\.wordpress\.com|^i\d\.wp\.com$/;

// Ordered: the first rule that matches wins.
const DOMAIN_RULES = [
  ["ads", (d) =>
    under(d, "doubleclick.net", "googlesyndication.com", "googleadservices.com", "2mdn.net",
      "adform.net", "serving-sys.com", "pubmatic.com", "tribalfusion.com", "yieldmanager.com") ||
    labels(d).some((l) => AD_LABELS.has(l))],
  ["analytics", (d) => /(^|\.)analytics[.-]/.test(d) || /google-?anal|googleanal/.test(d)],
  ["spotify-music", (d) => under(d, "spotify.com", "scdn.co", "spotifycdn.com", "music.apple.com") || d.includes("spotify")],
  ["video", (d) => under(d, "youtube.com", "youtu.be")],
  ["social", (d) => /facebook|instagram|twitter|tiktok|linkedin|reddit|pinterest/.test(sld(d)) ||
    under(d, "x.com", "redd.it", "licdn.com", "musical.ly", "lnkd.demdex.net")],
  ["cloud-infra", (d) => under(d, "amazonaws.com", "cloudflare.com", "cdn.com", "fbcdn.net", "signalr.net")],
  ["gifs-images", (d) => GIF_IMAGE_RE.test(d)],
  ["google-apple-microsoft", (d) =>
    // "google" alone or joined (googleapis, googleusercontent…) — NOT "google-xxx" look-alike fakes.
    /^google[a-z]*$/.test(sld(d)) || d.endsWith(".goog") || d.endsWith(".google") ||
    under(d, "gstatic.com", "1e100.net", "gvt1.com", "withgoogle.com") ||
    under(d, "apple.com", "microsoft.com", "microsoftonline.com", "onedrive.com", "live.com", "live.net") ||
    d.includes("officeapps")],
  ["app-help-pages", (d) =>
    under(d, "whatsapp.com", "moovit.com", "moovitapp.com", "moovitapp.zendesk.com", "payboxapp.com",
      "max.co.il", "harel-group.co.il", "korenpub.com")],
];

// Comment lines → which group their (otherwise unmatched) lines fall into. Matched by substring.
const SECTION_HINTS = [
  ["General ad blocking", "ads"],
  ["SPOTIFY ADS", "ads"],
  ["Pandora ad blocking", "ads"],
  ["# YouTube", "ads"],
  ["פרסומות גוגל", "ads"],
  ["רשת חברתית חדשה", "social"],
  ["#פייסבוק", "social"],
  ["תמונות בספוטיפיי", "spotify-music"],
  ["תמונות נוספות בספוטיפיי", "spotify-music"],
  ["ג'יפ במקלדת גוגל", "gifs-images"],
  ["#Gifs", "gifs-images"],
  // The "blocked inside an app" sections.
  ["נחסם בתוך", "app-help-pages"],
  ["נחסם באופיס", "app-help-pages"],
  ["אפליקציית", "app-help-pages"],
  ["פאיבוקס", "app-help-pages"],
  ["וואצפ", "app-help-pages"],
  ["מוביט", "app-help-pages"],
  ["משניות קהתי", "app-help-pages"],
  ["במקס", "app-help-pages"],
];

function sectionHint(comment) {
  for (const [needle, group] of SECTION_HINTS) if (comment.includes(needle)) return group;
  return null;
}

function classify(domain, hint) {
  for (const [group, test] of DOMAIN_RULES) if (test(domain)) return { group, by: "domain" };
  if (hint) return { group: hint, by: "section" };
  return { group: "other-sites", by: "default" };
}

// ── Main ──────────────────────────────────────────────────────────────────────

const lines = fs.readFileSync(SOURCE, "utf8").replace(/^﻿/, "").split(/\r?\n/);
if (lines[lines.length - 1] === "") lines.pop();

const groups = Object.fromEntries(GROUPS.map((g) => [g, new Set()]));
const placedBy = new Map(); // entry → { group, by, section }
const review = [];
const dropped = [];
const fixed = [];
let comments = 0, blanks = 0, entries = 0;
let section = null, sectionText = null;

lines.forEach((original, i) => {
  const lineNo = i + 1;
  const line = original.replace(/^"/, "").trim(); // line 1 starts with a stray quote
  if (line === "") { blanks++; return; }
  if (line.startsWith("#")) {
    comments++;
    section = sectionHint(line);
    sectionText = line;
    return;
  }

  const parts = line.split(IP_SPLIT_RE).map((p) => p.trim()).filter(Boolean);
  const ipCount = (line.match(IP_SPLIT_RE) || []).length;
  if (ipCount === 0) fixed.push(`line ${lineNo}: no IP at the start: ${original}`);
  if (ipCount > 1 || /(127\.0\.0\.1|127\.0\.01|0\.0\.0\.0)\S/.test(line)) {
    fixed.push(`line ${lineNo}: split stuck-together line: ${original.trim()}  →  ${parts.join("  |  ")}`);
  }

  for (const part of parts) {
    entries++;
    const r = cleanEntry(part);
    if (r.review) { review.push({ lineNo, original: original.trim(), reason: r.review }); continue; }
    if (r.drop) { dropped.push(`line ${lineNo}: ${part} (${r.drop})`); continue; }
    const entry = (r.wildcard ? "*." : "") + r.domain;
    if (placedBy.has(entry)) continue; // first occurrence decides the group
    const c = classify(r.domain, section);
    groups[c.group].add(entry);
    placedBy.set(entry, { ...c, section: sectionText, lineNo });
  }
});

// ── Write outputs ─────────────────────────────────────────────────────────────

fs.mkdirSync(OUT_DIR, { recursive: true });
for (const g of GROUPS) {
  const body = [...groups[g]].sort().join("\n");
  fs.writeFileSync(path.join(OUT_DIR, `${g}.txt`), body + (body ? "\n" : ""));
}

const reviewText = [
  "# Lines from source/hosts-original.txt that build-blocklists.mjs would not guess at.",
  "# These are NOT blocked. Fix them in the source file, then re-run the script.",
  "",
  ...review.map((r) => `line ${r.lineNo}\t${r.reason}\t${r.original}`),
].join("\n") + "\n";
fs.writeFileSync(REVIEW_FILE, reviewText);

// ── Report ────────────────────────────────────────────────────────────────────

const total = GROUPS.reduce((n, g) => n + groups[g].size, 0);
console.log("=== build-blocklists report ===");
console.log(`lines read:        ${lines.length}  (${comments} comments, ${blanks} blank)`);
console.log(`entries found:     ${entries}`);
console.log(`unique domains:    ${total}`);
console.log("\nper group:");
for (const g of GROUPS) console.log(`  ${g.padEnd(24)} ${groups[g].size}`);
console.log(`\ndropped (${dropped.length}):`);
dropped.forEach((d) => console.log("  " + d));
console.log(`\nfixed (${fixed.length}):`);
fixed.forEach((f) => console.log("  " + f));
const bySection = [...placedBy].filter(([, v]) => v.by === "section");
console.log(`\nplaced by section comment, not by domain rule (${bySection.length}):`);
bySection.forEach(([d, v]) => console.log(`  ${d.padEnd(44)} → ${v.group.padEnd(16)} (${v.section})`));
console.log(`\nother-sites (no rule or section matched) (${groups["other-sites"].size}):`);
console.log("  " + [...groups["other-sites"]].sort().join(", "));
console.log(`\nneeds-review.txt (${review.length}):`);
review.forEach((r) => console.log(`  line ${r.lineNo}: ${r.reason}: ${r.original}`));

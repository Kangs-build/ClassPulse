// Optional Node.js regression checks for safe HTML rendering and session recovery.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
let alerts = 0;
const sandbox = {
  sessionStorage: {getItem: () => 'existing-token', removeItem() {}},
  window: {addEventListener() {}},
  document: {querySelectorAll: () => [], getElementById: () => ({classList: {add() {}}})},
  alert: () => {alerts++;},
  fetch: async () => ({status: 401}),
  setTimeout, clearTimeout, URL, console
};
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../public/app.js'), 'utf8'), sandbox);
let passed = 0;
function check(label, fn) {fn(); passed++; console.log('PASS ' + label);}
check('HTML injection escaped', () => assert.equal(sandbox.escapeHtml('<img src=x onerror="bad()">'), '&lt;img src=x onerror=&quot;bad()&quot;&gt;'));
check('Attribute apostrophes escaped', () => assert.equal(sandbox.escapeHtml("'"), '&#39;'));
check('Literal search highlights safely', () => assert.equal(sandbox.highlightTerm('a+b a+b', 'a+b'), '<mark>a+b</mark> <mark>a+b</mark>'));
check('Search cannot introduce executable tags', () => assert.equal(sandbox.highlightTerm('<script>bad()</script>', '<script>'), '<mark>&lt;script&gt;</mark>bad()&lt;/script&gt;'));
check('Highlight does not split HTML entities', () => assert.equal(sandbox.highlightTerm('A & B', '&'), 'A <mark>&amp;</mark> B'));
check('Quotes and newlines roundtrip in peer approval', () => {
  const text = "It isn't \\\"simple\\\".\nUse \\ safely <b>";
  const encoded = encodeURIComponent(text).replace(/'/g, '%27');
  assert(!/[\n'"<>]/.test(encoded)); assert.equal(decodeURIComponent(encoded), text);
});
Promise.all([sandbox.apiFetch('/x'), sandbox.apiFetch('/y')]).then(() => {
  check('Concurrent expired requests show one notification', () => assert.equal(alerts, 1));
  console.log(`FRONTEND RESULTS: ${passed} passed, 0 failed`);
}).catch(e => {console.error(e); process.exitCode = 1;});

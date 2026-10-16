// Cloudflare Turnstile on the sign-in and password-reset forms (console, organizations, updater).
// Widget-only: the page won't submit until the check passes, which keeps bots off these forms.
// It is not verified server-side — sign-in goes straight from the browser to Firebase Auth.
export const TURNSTILE_SITE_KEY = '0x4AAAAAAFYup-pf1QaUrNHA';

const SCRIPT_URL = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
let apiReady = null;

function loadApi() {
  if (!apiReady) {
    apiReady = new Promise((resolve, reject) => {
      if (window.turnstile) return resolve(window.turnstile);
      const s = document.createElement('script');
      s.src = SCRIPT_URL;
      s.async = true;
      s.onload = () => resolve(window.turnstile);
      s.onerror = () => reject(new Error('Turnstile failed to load'));
      document.head.appendChild(s);
    });
  }
  return apiReady;
}

// ── Responsibility statement, required on every sign-in (same wording as the app's
//    TermsAcceptance.STATEMENT — keep the two in step, and bump the version together). ──
export const TERMS_VERSION = '2026-10-16';

/** Inserts the required responsibility checkbox into `container`; returns the checkbox. */
export function mountConsent(container) {
  const el = typeof container === 'string' ? document.getElementById(container) : container;
  el.innerHTML = `<label class="consent">
    <input type="checkbox" class="consent-box">
    <span>I own the phones I manage with CORE MDM or have permission to manage them, and I take
      <b>full responsibility</b> for them. CORE MDM is provided as is: its makers aren’t responsible for
      any problem, loss or damage — <b>including problems caused by the app itself</b>. I agree to the
      <a href="terms.html" target="_blank" rel="noopener">Terms of Service</a> and
      <a href="privacy.html" target="_blank" rel="noopener">Privacy Policy</a>.</span>
  </label>`;
  return el.querySelector('.consent-box');
}

/**
 * Renders a Turnstile widget into `container` and keeps `buttons` disabled until it passes
 * (and, with `consent`, until that checkbox is ticked).
 * Returns { passed(), reset() }. Call reset() after every attempt (a token is single-use, and a
 * fresh check per try slows down password guessing).
 */
export function guardForm(container, buttons, { onError, consent } = {}) {
  const el = typeof container === 'string' ? document.getElementById(container) : container;
  const btns = buttons.map(b => typeof b === 'string' ? document.getElementById(b) : b).filter(Boolean);
  let token = null;
  let widgetId = null;
  const ok = () => !!token && (!consent || consent.checked);
  const refresh = () => {
    const on = ok();
    btns.forEach(b => {
      b.disabled = !on;
      b.title = on ? '' : (consent && !consent.checked ? 'Tick the responsibility statement first' : 'Complete the check first');
    });
  };
  consent?.addEventListener('change', refresh);
  refresh();

  loadApi().then(ts => {
    widgetId = ts.render(el, {
      sitekey: TURNSTILE_SITE_KEY,
      theme: 'dark',
      callback: t => { token = t; refresh(); },
      'expired-callback': () => { token = null; refresh(); },
      'error-callback': () => { token = null; refresh(); },
    });
  }).catch(err => {
    // Blocked by an extension or offline: say so instead of leaving a dead button.
    onError?.('Couldn’t load the human check. Disable content blockers for this site and reload.');
    console.error(err);
  });

  return {
    passed: ok,
    reset() {
      token = null;
      refresh();
      if (widgetId !== null && window.turnstile) window.turnstile.reset(widgetId);
    },
  };
}

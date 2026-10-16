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

/**
 * Renders a Turnstile widget into `container` and keeps `buttons` disabled until it passes.
 * Returns { passed(), reset() }. Call reset() after every attempt (a token is single-use, and a
 * fresh check per try slows down password guessing).
 */
export function guardForm(container, buttons, { onError } = {}) {
  const el = typeof container === 'string' ? document.getElementById(container) : container;
  const btns = buttons.map(b => typeof b === 'string' ? document.getElementById(b) : b).filter(Boolean);
  let token = null;
  let widgetId = null;
  const setEnabled = on => btns.forEach(b => { b.disabled = !on; b.title = on ? '' : 'Complete the check first'; });
  setEnabled(false);

  loadApi().then(ts => {
    widgetId = ts.render(el, {
      sitekey: TURNSTILE_SITE_KEY,
      theme: 'dark',
      callback: t => { token = t; setEnabled(true); },
      'expired-callback': () => { token = null; setEnabled(false); },
      'error-callback': () => { token = null; setEnabled(false); },
    });
  }).catch(err => {
    // Blocked by an extension or offline: say so instead of leaving a dead button.
    onError?.('Couldn’t load the human check. Disable content blockers for this site and reload.');
    console.error(err);
  });

  return {
    passed: () => !!token,
    reset() {
      token = null;
      setEnabled(false);
      if (widgetId !== null && window.turnstile) window.turnstile.reset(widgetId);
    },
  };
}

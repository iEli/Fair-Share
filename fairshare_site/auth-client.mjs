export const SESSION_KEY = 'fairshare.session'

export function authErrorMessage(status, endpoint) {
  if (status === 429) return 'Too many attempts. Please wait a few minutes and try again.'
  if (status >= 500) return 'We could not reach the account service. Please try again shortly.'
  if (status === 401 || status === 403) return 'Check your email and password. If you just signed up, confirm your email first.'
  if (endpoint === 'signup') return 'We could not create your account. Check your details and use a stronger password, or try logging in.'
  return 'We could not sign you in. Please check your details and try again.'
}

export async function requestAuth(endpoint, payload, { baseUrl = '', fetchImpl = globalThis.fetch } = {}) {
  let response
  try {
    response = await fetchImpl(`${baseUrl.replace(/\/$/, '')}/api/auth/${endpoint}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      credentials: 'omit',
      cache: 'no-store',
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(15000),
    })
  } catch {
    throw new Error('Unable to connect. Check your connection and try again.')
  }
  if (!response.ok) throw new Error(authErrorMessage(response.status, endpoint))
  let session
  try { session = await response.json() } catch {
    throw new Error('The account service returned an unexpected response. Please try again.')
  }
  if (!session?.user?.id || (endpoint === 'login' && (!session.access_token || !session.refresh_token))) {
    throw new Error('The account service returned an incomplete response. Please try again.')
  }
  return session
}

export function saveSession(session, remember, { local = localStorage, tab = sessionStorage } = {}) {
  const saved = {
    user: session.user,
    access_token: session.access_token,
    refresh_token: session.refresh_token,
    expires_at: session.expires_at ?? Math.floor(Date.now() / 1000) + session.expires_in,
  }
  // Keep one active session and never persist passwords or form inputs.
  const destination = remember ? local : tab
  destination.setItem(SESSION_KEY, JSON.stringify(saved))
  ;(remember ? tab : local).removeItem(SESSION_KEY)
}

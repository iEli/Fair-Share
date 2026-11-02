import test from 'node:test'
import assert from 'node:assert/strict'
import { requestAuth, saveSession, SESSION_KEY } from '../../fairshare_site/auth-client.mjs'

const session = { user: { id: 'test-user', email: 'test@example.com' }, access_token: 'access', refresh_token: 'refresh', expires_in: 3600 }
const memory = () => { const data = new Map(); return { getItem: key => data.get(key), setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) } }

test('login sends JSON POST credentials only in the body', async () => {
  let request
  const result = await requestAuth('login', { email: 'test@example.com', password: 'private' }, {
    baseUrl: 'https://api.example.com/',
    fetchImpl: async (url, options) => { request = { url, options }; return { ok: true, json: async () => session } },
  })
  assert.equal(request.url, 'https://api.example.com/api/auth/login')
  assert.equal(request.options.method, 'POST')
  assert.equal(request.options.credentials, 'omit')
  assert.deepEqual(JSON.parse(request.options.body), { email: 'test@example.com', password: 'private' })
  assert.equal(result.access_token, 'access')
})

test('signup without tokens is a confirmation result', async () => {
  const result = await requestAuth('signup', {}, { fetchImpl: async () => ({ ok: true, json: async () => ({ user: session.user, access_token: null }) }) })
  assert.equal(result.access_token, null)
})

test('failed login does not expose raw upstream errors', async () => {
  for (const status of [400, 401, 403, 429, 503]) {
    await assert.rejects(requestAuth('login', {}, { fetchImpl: async () => ({ ok: false, status }) }), /try again|confirm your email/i)
  }
})

test('network and malformed responses are visible failures', async () => {
  await assert.rejects(requestAuth('login', {}, { fetchImpl: async () => { throw new Error('private diagnostic') } }), /Unable to connect/)
  await assert.rejects(requestAuth('login', {}, { fetchImpl: async () => ({ ok: true, json: async () => ({}) }) }), /incomplete response/)
  await assert.rejects(requestAuth('login', {}, { fetchImpl: async () => ({ ok: true, json: async () => { throw new Error('invalid JSON') } }) }), /unexpected response/)
})

test('remember me selects persistent storage and removes stale tab session', () => {
  const local = memory(), tab = memory()
  saveSession(session, false, { local, tab })
  assert.ok(tab.getItem(SESSION_KEY))
  assert.equal(local.getItem(SESSION_KEY), undefined)
  saveSession(session, true, { local, tab })
  assert.ok(local.getItem(SESSION_KEY))
  assert.equal(tab.getItem(SESSION_KEY), undefined)
  saveSession(session, false, { local, tab })
  assert.equal(local.getItem(SESSION_KEY), undefined)
  assert.ok(tab.getItem(SESSION_KEY))
  const saved = JSON.parse(tab.getItem(SESSION_KEY))
  assert.equal(saved.refresh_token, 'refresh')
  assert.ok(saved.expires_at > Date.now() / 1000)
  assert.equal(saved.password, undefined)
})

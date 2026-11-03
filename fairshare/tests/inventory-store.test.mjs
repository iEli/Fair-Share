import test from 'node:test'
import assert from 'node:assert/strict'
import {
  DEFAULT_GUEST_ITEMS,
  GUEST_INVENTORY_KEY,
  loadGuestItems,
  saveGuestItems,
} from '../../fairshare_site/inventory-store.mjs'

const memoryStorage = () => {
  const data = new Map()
  return {
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, value),
  }
}

test('guest inventory starts with editable defaults and saves changes locally', () => {
  const storage = memoryStorage()
  const initial = loadGuestItems(storage)
  assert.equal(initial.length, DEFAULT_GUEST_ITEMS.length)
  assert.notEqual(initial[0], DEFAULT_GUEST_ITEMS[0])

  const updated = initial.map((item, index) =>
    index === 0 ? { ...item, quantity: item.quantity + 1 } : item)
  saveGuestItems(updated, storage)

  assert.equal(JSON.parse(storage.getItem(GUEST_INVENTORY_KEY))[0].quantity, 3)
  assert.equal(loadGuestItems(storage)[0].quantity, 3)
})

test('guest inventory reports malformed or invalid saved data', () => {
  const storage = memoryStorage()
  storage.setItem(GUEST_INVENTORY_KEY, '{')
  assert.throws(() => loadGuestItems(storage), /unreadable/)

  storage.setItem(GUEST_INVENTORY_KEY, JSON.stringify([{ id: 'item', quantity: -1 }]))
  assert.throws(() => loadGuestItems(storage), /invalid/)
})

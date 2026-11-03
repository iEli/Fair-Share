export const GUEST_INVENTORY_KEY = 'fairshare.guest-inventory'

export const DEFAULT_GUEST_ITEMS = [
  { id: 'guest-milk', name: 'Milk', category: 'Grocery', icon: '🥛', quantity: 2 },
  { id: 'guest-toilet-paper', name: 'Toilet Paper', category: 'Grocery', icon: '🧻', quantity: 3 },
  { id: 'guest-eggs', name: 'Eggs', category: 'Grocery', icon: '🥚', quantity: 1 },
  { id: 'guest-bread', name: 'Bread', category: 'Grocery', icon: '🍞', quantity: 10 },
  { id: 'guest-rice', name: 'Rice', category: 'Grocery', icon: '🍚', quantity: 5 },
  { id: 'guest-pasta', name: 'Pasta', category: 'Grocery', icon: '🍝', quantity: 2 },
  { id: 'guest-coffee-beans', name: 'Coffee beans', category: 'Grocery', icon: '☕', quantity: 1 },
  { id: 'guest-bananas', name: 'Bananas', category: 'Grocery', icon: '🍌', quantity: 5 },
  { id: 'guest-cooking-oil', name: 'Cooking oil', category: 'Grocery', icon: '🥫', quantity: 0 },
  { id: 'guest-hand-soap', name: 'Hand soap', category: 'Grocery', icon: '🧼', quantity: 20 },
  { id: 'guest-paper-towels', name: 'Paper towels', category: 'Grocery', icon: '🧻', quantity: 3 },
  { id: 'guest-dish-soap', name: 'Dish soap', category: 'Grocery', icon: '🫧', quantity: 2 },
  { id: 'guest-laundry-detergent', name: 'Laundry detergent', category: 'Grocery', icon: '🪣', quantity: 2 },
  { id: 'guest-trash-bags', name: 'Trash bags', category: 'Grocery', icon: '🗑️', quantity: 0 },
]

export function loadGuestItems(storage = localStorage) {
  const saved = storage.getItem(GUEST_INVENTORY_KEY)
  if (saved === null) return DEFAULT_GUEST_ITEMS.map((item) => ({ ...item }))

  let items
  try {
    items = JSON.parse(saved)
  } catch {
    throw new Error('Saved guest inventory is unreadable. Clear this site’s inventory data to start over.')
  }
  if (!Array.isArray(items) || items.some((item) =>
    !item || typeof item.id !== 'string'
    || typeof item.name !== 'string' || typeof item.category !== 'string'
    || typeof item.icon !== 'string'
    || !Number.isInteger(item.quantity) || item.quantity < 0)) {
    throw new Error('Saved guest inventory is invalid. Clear this site’s inventory data to start over.')
  }
  return items
}

export function saveGuestItems(items, storage = localStorage) {
  storage.setItem(GUEST_INVENTORY_KEY, JSON.stringify(items))
}

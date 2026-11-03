import { SESSION_KEY } from './auth-client.mjs'
import { loadGuestItems, saveGuestItems } from './inventory-store.mjs'

const grid = document.querySelector('#inventory-grid')
const message = document.querySelector('#inventory-message')
const addButton = document.querySelector('#add-inventory-button')
const searchInput = document.querySelector('#inventory-search')
const dialog = document.querySelector('#add-inventory-dialog')
const form = document.querySelector('#add-inventory-form')
const formError = document.querySelector('#inventory-form-error')
const cancelButton = document.querySelector('#cancel-add-item')
const apiBase = document.querySelector('meta[name="fairshare-api-base"]')?.content ?? ''

let accessToken = null
let household = null
let guestMode = false
let items = []

grid.replaceChildren()

function showMessage(text = '') {
  message.textContent = text
  message.hidden = text.length === 0
}

function readSession() {
  for (const storage of [sessionStorage, localStorage]) {
    const saved = storage.getItem(SESSION_KEY)
    if (saved === null) continue
    try {
      return JSON.parse(saved)
    } catch {
      throw new Error('Your saved sign-in session is invalid. Please log in again.')
    }
  }
  return null
}

async function request(path, { method = 'GET', body } = {}) {
  let response
  try {
    response = await fetch(`${apiBase.replace(/\/$/, '')}${path}`, {
      method,
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${accessToken}`,
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      credentials: 'omit',
      cache: 'no-store',
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      signal: AbortSignal.timeout(15000),
    })
  } catch (error) {
    if (error.name === 'TimeoutError' || error.name === 'AbortError') {
      throw new Error('The inventory service took too long to respond. Please try again.')
    }
    throw new Error('Unable to connect to the inventory service. Check your connection and try again.')
  }

  const responseText = await response.text()
  let payload = null
  if (responseText) {
    try {
      payload = JSON.parse(responseText)
    } catch {
      throw new Error('The inventory service returned an unexpected response.')
    }
  }
  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Your sign-in session expired. Please log in again.')
    }
    throw new Error(payload?.detail ?? payload?.message ?? `Inventory request failed (${response.status}).`)
  }
  return payload
}

function makeElement(tag, className, text) {
  const element = document.createElement(tag)
  if (className) element.className = className
  if (text !== undefined) element.textContent = text
  return element
}

function render() {
  const query = searchInput.value.trim().toLocaleLowerCase()
  const filteredItems = items.filter((item) =>
    `${item.name} ${item.category}`.toLocaleLowerCase().includes(query))
  grid.replaceChildren()
  grid.hidden = false

  if (filteredItems.length === 0) {
    const text = items.length === 0
      ? 'No inventory items yet. Use + to add your first item.'
      : 'No items match your search.'
    grid.append(makeElement('p', 'inventory-empty', text))
    return
  }

  for (const item of filteredItems) {
    const status = item.quantity === 0 ? 'out' : item.quantity <= 3 ? 'low' : 'stocked'
    const card = makeElement('article', 'inventory-card')
    card.dataset.status = status

    const top = makeElement('div', 'item-top')
    top.append(makeElement('div', 'item-icon', item.icon || '📦'))
    const copy = makeElement('div', 'item-copy')
    copy.append(makeElement('h2', '', item.name))
    copy.append(makeElement('p', '', `Type: ${item.category}`))
    top.append(copy)
    const count = makeElement('div', 'item-count')
    count.append(makeElement('strong', '', String(item.quantity)))
    count.append(makeElement('span', '', 'STOCK'))
    top.append(count)
    card.append(top)

    const actions = makeElement('div', 'item-actions')
    const increase = makeElement('button', '', '＋')
    increase.type = 'button'
    increase.dataset.itemId = item.id
    increase.dataset.delta = '1'
    increase.setAttribute('aria-label', `Add one ${item.name}`)
    const decrease = makeElement('button', '', '−')
    decrease.type = 'button'
    decrease.dataset.itemId = item.id
    decrease.dataset.delta = '-1'
    decrease.disabled = item.quantity === 0
    decrease.setAttribute('aria-label', `Remove one ${item.name}`)
    actions.append(increase, decrease)
    const shoppingButton = makeElement('button', 'shopping-button', 'Add to shopping list')
    shoppingButton.type = 'button'
    shoppingButton.disabled = true
    shoppingButton.title = 'Shopping list integration is not available yet'
    actions.append(shoppingButton)
    actions.append(makeElement('span', `status-dot ${status}`))
    card.append(actions)
    grid.append(card)
  }
}

function setHouseholdName(name) {
  document.querySelector('#household-name').textContent = name
  document.querySelector('#breadcrumb-household').textContent = name
}

async function initialize() {
  let session
  try {
    session = readSession()
  } catch (error) {
    startGuestMode(`Using guest inventory. ${error.message}`)
    return
  }
  if (!session?.access_token) {
    startGuestMode('Guest mode: inventory is saved only in this browser.')
    return
  }
  accessToken = session.access_token

  try {
    const households = await request('/api/households')
    if (!Array.isArray(households)) {
      throw new Error('The household service returned an unexpected response.')
    }
    if (households.length === 0) {
      household = await request('/api/households', {
        method: 'POST',
        body: { name: 'Theoretical House' },
      })
    } else {
      household = households[0]
    }
    setHouseholdName(household.name)
    const result = await request(`/api/households/${encodeURIComponent(household.id)}/inventory`)
    if (!Array.isArray(result)) {
      throw new Error('The inventory service returned an unexpected response.')
    }
    items = result
    render()
    addButton.disabled = false
    showMessage()
  } catch (error) {
    showMessage(error.message)
  }
}

function startGuestMode(statusMessage) {
  try {
    items = loadGuestItems()
    guestMode = true
    setHouseholdName('My Inventory')
    render()
    addButton.disabled = false
    showMessage(statusMessage)
  } catch (error) {
    showMessage(error.message)
  }
}

grid.addEventListener('click', async (event) => {
  const button = event.target.closest('button[data-item-id]')
  if (!button || (!household && !guestMode)) return

  const cardButtons = button.closest('.item-actions').querySelectorAll('button')
  cardButtons.forEach((cardButton) => { cardButton.disabled = true })
  showMessage()
  if (guestMode) {
    const itemId = button.dataset.itemId
    const delta = Number(button.dataset.delta)
    const item = items.find((existing) => existing.id === itemId)
    const quantity = item.quantity + delta
    const updatedItems = items.map((existing) =>
      existing.id === itemId ? { ...existing, quantity } : existing)
    try {
      saveGuestItems(updatedItems)
      items = updatedItems
      render()
    } catch (error) {
      showMessage(`Could not save guest inventory: ${error.message}`)
      cardButtons.forEach((cardButton) => {
        cardButton.disabled = cardButton.dataset.delta === '-1' && item.quantity === 0
      })
    }
    return
  }
  try {
    const item = await request(
      `/api/households/${encodeURIComponent(household.id)}/inventory/${encodeURIComponent(button.dataset.itemId)}/quantity`,
      { method: 'PATCH', body: { delta: Number(button.dataset.delta) } },
    )
    items = items.map((existing) => existing.id === item.id ? item : existing)
    render()
  } catch (error) {
    showMessage(error.message)
    cardButtons.forEach((cardButton) => {
      cardButton.disabled = cardButton.dataset.delta === '-1'
        && items.find((item) => item.id === button.dataset.itemId)?.quantity === 0
    })
  }
})

searchInput.addEventListener('input', render)

addButton.addEventListener('click', () => {
  form.reset()
  formError.hidden = true
  dialog.showModal()
  form.elements.namedItem('name').focus()
})

cancelButton.addEventListener('click', () => dialog.close())

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  if ((!household && !guestMode) || !form.reportValidity()) return

  const submitButton = form.querySelector('button[type="submit"]')
  const formData = new FormData(form)
  const quantity = Number(formData.get('quantity'))
  const body = {
    name: String(formData.get('name')).trim(),
    category: String(formData.get('category')).trim(),
    icon: String(formData.get('icon')).trim(),
    quantity,
  }
  if (!body.name || !body.category) {
    formError.textContent = 'Name and category are required.'
    formError.hidden = false
    return
  }
  submitButton.disabled = true
  formError.hidden = true
  try {
    if (guestMode) {
      const item = { id: crypto.randomUUID(), ...body, icon: body.icon || '📦' }
      const updatedItems = [...items, item]
      saveGuestItems(updatedItems)
      items = updatedItems
    } else {
      const item = await request(`/api/households/${encodeURIComponent(household.id)}/inventory`, {
        method: 'POST',
        body,
      })
      items.push(item)
    }
    render()
    dialog.close()
    showMessage(guestMode ? 'Guest mode: inventory is saved only in this browser.' : '')
  } catch (error) {
    formError.textContent = error.message
    formError.hidden = false
  } finally {
    submitButton.disabled = false
  }
})

initialize()

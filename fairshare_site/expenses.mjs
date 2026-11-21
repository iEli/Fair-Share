import { SESSION_KEY } from './auth-client.mjs'

const guestKey = 'fairshare.guest-expenses'
const pageSize = 20
const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })
const apiBase = document.querySelector('meta[name="fairshare-api-base"]').content.replace(/\/$/, '')

const message = document.querySelector('#expense-message')
const list = document.querySelector('#expense-list')
const search = document.querySelector('#expense-search')
const filters = document.querySelectorAll('[data-expense-filter]')
const addButton = document.querySelector('#add-expense-button')
const loadButton = document.querySelector('#load-expenses')
const dialog = document.querySelector('#add-expense-dialog')
const form = document.querySelector('#add-expense-form')
const formError = document.querySelector('#expense-form-error')
const cancelButton = document.querySelector('#cancel-expense')
const saveButton = form.querySelector('button[type="submit"]')

const expenses = new Map()
let session
let expensesPath
let guestMode = false
let offset = 0
let filter = 'all'

function showMessage(text, link) {
  message.textContent = text
  if (link) {
    const action = document.createElement('a')
    action.href = link.href
    action.textContent = link.label
    message.append(' ', action)
  }
  message.hidden = !text
}

async function request(path, body) {
  let response
  try {
    response = await fetch(apiBase + path, {
      method: body ? 'POST' : 'GET',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${session.access_token}`,
        ...(body && { 'Content-Type': 'application/json' }),
      },
      credentials: 'omit',
      cache: 'no-store',
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(15000),
    })
  } catch {
    throw new Error('Could not reach the expense service. Check your connection and try again.')
  }
  if (response.status === 401) throw new Error('Your session expired. Please log in again.')
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(data?.detail ?? data?.message ?? `Expense request failed (${response.status}).`)
  }
  if (!data) throw new Error('The expense service returned an unexpected response.')
  return data
}

function element(tag, className, text) {
  const node = document.createElement(tag)
  node.className = className
  node.textContent = text
  return node
}

function payerName(expense) {
  return guestMode || expense.payerUserId === session?.user?.id ? 'You' : 'Household member'
}

function render() {
  const query = search.value.trim().toLocaleLowerCase()
  const visible = [...expenses.values()].filter((expense) => {
    const paid = Number(expense.outstandingAmount) === 0
    const matchesStatus = filter === 'all' || (filter === 'paid' ? paid : !paid)
    return matchesStatus && `${expense.description} ${payerName(expense)}`.toLocaleLowerCase().includes(query)
  })
  visible.sort((left, right) => right.spentOn.localeCompare(left.spentOn)
    || right.createdAt.localeCompare(left.createdAt))

  list.replaceChildren()
  if (visible.length === 0) {
    const empty = expenses.size === 0 ? 'No expenses yet.' : 'No expenses match your filters.'
    list.append(element('p', 'expense-empty', empty))
    return
  }
  for (const expense of visible) {
    const row = element('article', 'purchase-grid purchase-row', '')
    const item = element('div', 'purchase-item', '')
    const details = document.createElement('div')
    details.append(element('h2', '', expense.description), element('p', '', expense.spentOn))
    item.append(details)

    const outstanding = Number(expense.outstandingAmount)
    const status = element('div', 'purchase-status', '')
    status.append(element('strong', '', outstanding > 0 ? 'Unpaid' : 'Paid'))
    if (outstanding > 0) status.append(element('small', '', `${money.format(outstanding)} still owed`))

    row.append(item, element('div', 'purchase-person', payerName(expense)),
      element('div', 'purchase-amount', money.format(expense.amount)), status)
    list.append(row)
  }
}

function selectFilter(value) {
  filter = value
  for (const button of filters) {
    const selected = button.dataset.expenseFilter === filter
    button.classList.toggle('active', selected)
    button.setAttribute('aria-pressed', String(selected))
  }
}

async function loadExpenses() {
  loadButton.disabled = true
  try {
    const page = await request(`${expensesPath}?limit=${pageSize}&offset=${offset}`)
    if (!Array.isArray(page)) throw new Error('The expense service returned an unexpected response.')
    for (const expense of page) expenses.set(expense.id, expense)
    offset += page.length
    loadButton.hidden = page.length < pageSize
    render()
  } finally {
    loadButton.disabled = false
  }
}

async function initialize() {
  try {
    const saved = sessionStorage.getItem(SESSION_KEY) ?? localStorage.getItem(SESSION_KEY)
    session = JSON.parse(saved)
    if (saved !== null && !session?.access_token) throw new Error('Please log in again to load expenses.')

    let name = 'Guest expenses'
    if (session) {
      const households = await request('/api/households')
      if (!Array.isArray(households)) throw new Error('Could not load your households.')
      const household = households[0]
      if (!household) {
        showMessage('You need a household before adding expenses.', { href: 'inventory.html', label: 'Set up a household in Inventory' })
        return
      }
      name = household.name
      expensesPath = `/api/households/${encodeURIComponent(household.id)}/expenses`
      await loadExpenses()
    } else {
      const savedExpenses = JSON.parse(localStorage.getItem(guestKey) ?? '[]')
      for (const expense of savedExpenses) expenses.set(expense.id, expense)
      guestMode = true
      render()
    }

    for (const label of document.querySelectorAll('[data-expense-household]')) label.textContent = name
    document.querySelector('#expense-split-note').textContent = guestMode
      ? 'Guest expenses are saved in this browser.'
      : `You paid. The amount is split equally among members of ${name}.`
    showMessage(guestMode ? 'Guest mode. Expenses are saved in this browser.' : '')
    addButton.disabled = false
  } catch (error) {
    showMessage(error instanceof SyntaxError ? 'Could not read saved expense or session data.' : error.message,
      { href: 'login.html', label: 'Log in' })
  }
}

search.addEventListener('input', render)
for (const button of filters) {
  button.addEventListener('click', () => {
    selectFilter(button.dataset.expenseFilter)
    render()
  })
}

loadButton.addEventListener('click', async () => {
  showMessage('')
  try {
    await loadExpenses()
  } catch (error) {
    showMessage(error.message)
  }
})

addButton.addEventListener('click', () => {
  form.reset()
  formError.hidden = true
  const today = new Date()
  form.elements.spentOn.value = new Date(today.getTime() - today.getTimezoneOffset() * 60000)
    .toISOString().slice(0, 10)
  dialog.showModal()
})
cancelButton.addEventListener('click', () => dialog.close())
dialog.addEventListener('cancel', (event) => {
  if (saveButton.disabled) event.preventDefault()
})

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  if (saveButton.disabled) return
  const fields = new FormData(form)
  const body = {
    description: fields.get('description').trim(),
    amount: Number(fields.get('amount')),
    spentOn: fields.get('spentOn'),
  }
  if (!body.description) {
    formError.textContent = 'Enter an expense description.'
    formError.hidden = false
    return
  }

  saveButton.disabled = true
  cancelButton.disabled = true
  saveButton.textContent = 'Saving…'
  formError.hidden = true
  try {
    let expense
    if (guestMode) {
      expense = { ...body, id: crypto.randomUUID(), createdAt: new Date().toISOString(), outstandingAmount: 0 }
      const saved = JSON.parse(localStorage.getItem(guestKey) ?? '[]')
      localStorage.setItem(guestKey, JSON.stringify([...saved, expense]))
    } else {
      expense = await request(expensesPath, body)
    }
    expenses.set(expense.id, expense)
    search.value = ''
    selectFilter('all')
    render()
    dialog.close()
    showMessage(guestMode ? 'Expense saved in this browser.' : 'Expense added.')
  } catch (error) {
    formError.textContent = error.message
    formError.hidden = false
  } finally {
    saveButton.disabled = false
    cancelButton.disabled = false
    saveButton.textContent = 'Save expense'
  }
})

initialize()

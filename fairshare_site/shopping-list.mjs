const form = document.querySelector('#shopping-add-form')
const input = document.querySelector('#shopping-item-name')
const list = document.querySelector('.shopping-todo-list')
const count = document.querySelector('#shopping-count')
const template = document.querySelector('#shopping-item-template')
const storageKey = 'fairshare.shopping-items'

// Local storage, temporarily solution until database is implemented.
let addedItems = []
try {
  const savedItems = JSON.parse(localStorage.getItem(storageKey) ?? '[]')
  if (Array.isArray(savedItems)) {
    // Older versions saved just the item name.
    addedItems = savedItems
      .map(item => typeof item === 'string' ? { name: item } : item)
      .filter(item => typeof item?.name === 'string' && item.name.trim())
  }
} catch {
  // The list can still work for this visit if browser storage is unavailable.
}

// Fill new rows and update the item count.
function showItem({ name, category, addedBy }) {
  const row = template.content.cloneNode(true)
  row.querySelector('.shopping-item-name').textContent = name
  const details = row.querySelector('small')
  details.textContent = [category, addedBy && `Added by ${addedBy}`].filter(Boolean).join(' · ')
  if (!details.textContent) details.remove()
  list.append(row)
  count.textContent = `${list.children.length} items`
}

addedItems.forEach(showItem)

// Add the items and save updated list in the browser.
form.addEventListener('submit', event => {
  event.preventDefault()
  const name = input.value.trim()
  if (!name) return

  const item = {
    name,
    category: form.elements.category.value,
    addedBy: form.elements['added-by'].value.trim(),
  }
  addedItems.push(item)
  showItem(item)
  try {
    localStorage.setItem(storageKey, JSON.stringify(addedItems))
  } catch {
    document.querySelector('.shopping-list-panel .simple-page-note').textContent =
      'Browser storage is unavailable. New items will last until you leave or refresh this page.'
  }

  form.reset()
  input.focus()
})

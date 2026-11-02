import { requestAuth, saveSession } from './auth-client.mjs'

const form = document.querySelector('form[data-auth]')
if (form) {
  const endpoint = form.dataset.auth
  const message = document.querySelector('#auth-message')
  const button = form.querySelector('button[type="submit"]')
  const password = form.elements.namedItem('password')
  const confirmation = form.elements.namedItem('confirm-password')
  const baseUrl = document.querySelector('meta[name="fairshare-api-base"]')?.content ?? ''
  let busy = false

  function showMessage(text, kind) {
    message.textContent = text
    message.dataset.kind = kind
    message.hidden = false
    message.focus()
  }

  function validateConfirmation() {
    if (confirmation) {
      confirmation.setCustomValidity(confirmation.value === password.value ? '' : 'Passwords must match.')
    }
  }
  confirmation?.addEventListener('input', validateConfirmation)
  password.addEventListener('input', validateConfirmation)

  form.addEventListener('submit', async (event) => {
    event.preventDefault()
    if (busy) return
    validateConfirmation()
    if (!form.reportValidity()) return
    const email = form.elements.namedItem('email').value.trim().toLowerCase()
    const payload = { email, password: password.value }
    if (endpoint === 'signup') {
      payload.firstName = form.elements.namedItem('first-name').value.trim()
      payload.lastName = form.elements.namedItem('last-name').value.trim()
    }
    busy = true
    button.disabled = true
    form.setAttribute('aria-busy', 'true')
    message.hidden = true
    const buttonText = button.textContent
    button.textContent = endpoint === 'signup' ? 'Creating account…' : 'Logging in…'
    try {
      const session = await requestAuth(endpoint, payload, { baseUrl })
      if (session.access_token && session.refresh_token) {
        const remember = form.elements.namedItem('remember')?.checked ?? false
        saveSession(session, remember)
        form.reset()
        window.location.assign('dashboard.html')
      } else {
        // Confirmation-required signup must not pretend the user is logged in.
        form.reset()
        showMessage('Check your email to confirm your account, then return here to log in.', 'success')
      }
    } catch (error) {
      showMessage(error.message, 'error')
    } finally {
      busy = false
      button.disabled = false
      form.removeAttribute('aria-busy')
      button.textContent = buttonText
    }
  })
}

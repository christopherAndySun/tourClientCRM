let audioContext
let unlocked = false
const SOUND_ENABLED_KEY = 'crm_notification_sound_enabled'

export function isNotificationSoundEnabled() {
  return localStorage.getItem(SOUND_ENABLED_KEY) !== 'false'
}

export function setNotificationSoundEnabled(enabled) {
  localStorage.setItem(SOUND_ENABLED_KEY, enabled ? 'true' : 'false')
}

export function setupNotificationSoundUnlock() {
  const unlock = () => {
    try {
      const context = getAudioContext()
      if (context?.state === 'suspended') {
        context.resume()
      }
      unlocked = true
    } catch (error) {
      unlocked = false
    }
  }
  window.addEventListener('pointerdown', unlock, { once: true, passive: true })
  window.addEventListener('keydown', unlock, { once: true })
}

export function playNewDataSound() {
  try {
    if (!isNotificationSoundEnabled()) return false
    const context = getAudioContext()
    if (!context || context.state === 'suspended' || !unlocked) return false

    const startAt = context.currentTime
    playTone(context, startAt, 880, 0.12)
    playTone(context, startAt + 0.16, 1175, 0.14)
    return true
  } catch (error) {
    return false
  }
}

function getAudioContext() {
  if (!audioContext) {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext
    if (!AudioContextClass) return null
    audioContext = new AudioContextClass()
  }
  return audioContext
}

function playTone(context, startAt, frequency, duration) {
  const oscillator = context.createOscillator()
  const gain = context.createGain()
  oscillator.type = 'sine'
  oscillator.frequency.setValueAtTime(frequency, startAt)
  gain.gain.setValueAtTime(0.0001, startAt)
  gain.gain.exponentialRampToValueAtTime(0.16, startAt + 0.02)
  gain.gain.exponentialRampToValueAtTime(0.0001, startAt + duration)
  oscillator.connect(gain)
  gain.connect(context.destination)
  oscillator.start(startAt)
  oscillator.stop(startAt + duration + 0.02)
}

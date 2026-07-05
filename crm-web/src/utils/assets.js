const API_ORIGIN = import.meta.env.VITE_API_ORIGIN || ''

export function resolveAssetUrl(url) {
  if (!url) return ''
  if (url.startsWith('data:') || /^https?:\/\//i.test(url)) return url
  if (!url.startsWith('/')) return url
  return `${API_ORIGIN}${url}`
}

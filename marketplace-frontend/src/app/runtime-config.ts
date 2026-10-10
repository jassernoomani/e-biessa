declare global {
  interface Window { EBIESSA_CONFIG?: { apiBaseUrl?: string }; }
}

export function apiBaseUrl(): string {
  const configured = window.EBIESSA_CONFIG?.apiBaseUrl ?? 'http://localhost:8080/api';
  const url = new URL(configured, window.location.origin);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
    throw new Error('Invalid API URL configuration');
  }
  return url.href.replace(/\/$/, '');
}

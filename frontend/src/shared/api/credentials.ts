interface Credentials {
  username: string
  password: string
}

let credentials: Credentials | undefined

function encodeUtf8(value: string): string {
  const bytes = new TextEncoder().encode(value)
  let binary = ''

  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }

  return btoa(binary)
}

export function setCredentials(username: string, password: string): void {
  const normalizedUsername = username.trim()
  if (normalizedUsername.length === 0 || normalizedUsername.includes(':')) {
    throw new Error('Username must be non-empty and cannot contain a colon')
  }

  credentials = {
    username: normalizedUsername,
    password,
  }
}

export function getAuthorizationHeader(): string | undefined {
  if (!credentials) {
    return undefined
  }

  return `Basic ${encodeUtf8(`${credentials.username}:${credentials.password}`)}`
}

export function clearCredentials(): void {
  credentials = undefined
}

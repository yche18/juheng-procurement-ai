import { describe, expect, it, vi } from 'vitest'

import {
  clearCredentials,
  getAuthorizationHeader,
  setCredentials,
} from './credentials'

describe('credentials', () => {
  it('creates a Basic header in memory and clears it explicitly', () => {
    setCredentials('demo-requester', 'juheng-local')

    expect(getAuthorizationHeader()).toBe(
      `Basic ${btoa('demo-requester:juheng-local')}`,
    )

    clearCredentials()
    expect(getAuthorizationHeader()).toBeUndefined()
  })

  it('does not write credentials to browser storage', () => {
    const localStorageSpy = vi.spyOn(Storage.prototype, 'setItem')

    setCredentials('demo-requester', 'not-persisted')
    getAuthorizationHeader()

    expect(localStorageSpy).not.toHaveBeenCalled()
    expect(window.localStorage).toHaveLength(0)
    expect(window.sessionStorage).toHaveLength(0)
  })

  it('rejects usernames that cannot be represented safely in Basic auth', () => {
    expect(() => setCredentials('', 'password')).toThrow()
    expect(() => setCredentials('invalid:user', 'password')).toThrow()
  })
})

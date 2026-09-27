import { spawn, type ChildProcess } from 'node:child_process'

import type { FullConfig } from '@playwright/test'

const READY_TIMEOUT_MS = 90_000
const RETRY_INTERVAL_MS = 500

function startFrontend(): ChildProcess | null {
  if (process.env.JUHENG_E2E_START_FRONTEND !== 'true') {
    return null
  }

  const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm'
  return spawn(npmCommand, ['run', 'dev', '--', '--host', '127.0.0.1'], {
    env: process.env,
    stdio: 'inherit',
  })
}

async function waitForReady(url: string, acceptedStatuses: number[]) {
  const deadline = Date.now() + READY_TIMEOUT_MS
  let lastFailure = '尚未发出请求'

  while (Date.now() < deadline) {
    try {
      const response = await fetch(url)
      if (acceptedStatuses.includes(response.status)) {
        return
      }
      lastFailure = `HTTP ${response.status}`
    } catch (error) {
      lastFailure = error instanceof Error ? error.message : String(error)
    }

    await new Promise((resolve) => setTimeout(resolve, RETRY_INTERVAL_MS))
  }

  throw new Error(`等待 ${url} 就绪超时：${lastFailure}`)
}

export default async function globalSetup(config: FullConfig) {
  const baseURL = config.projects[0]?.use.baseURL
  if (typeof baseURL !== 'string') {
    throw new Error('Playwright baseURL 未配置')
  }

  const frontendProcess = startFrontend()
  try {
    await waitForReady(new URL('/login', baseURL).toString(), [200])
    await waitForReady(new URL('/api/current-user', baseURL).toString(), [401])
  } catch (error) {
    frontendProcess?.kill()
    throw error
  }

  return () => {
    frontendProcess?.kill()
  }
}

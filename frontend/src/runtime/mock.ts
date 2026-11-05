import type { putFile } from '../api/transport'

export async function startMockRuntime(): Promise<typeof putFile> {
  const [{ startMocking }, { mockPutFile }] = await Promise.all([
    import('../mocks/browser'),
    import('../mocks/upload'),
  ])
  await startMocking()
  return mockPutFile
}

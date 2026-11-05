import type { putFile } from '../api/transport'

export async function startMockRuntime(): Promise<typeof putFile> {
  throw new Error('Mock runtime is not available in this build.')
}

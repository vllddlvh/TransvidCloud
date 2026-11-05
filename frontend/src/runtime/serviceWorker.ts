const MOCK_WORKER_PATH = '/mockServiceWorker.js'

type WorkerLike = Pick<ServiceWorker, 'scriptURL'>

export type RegistrationLike = {
  active: WorkerLike | null
  waiting: WorkerLike | null
  installing: WorkerLike | null
  unregister: () => Promise<boolean>
}

type RegistrationSource = {
  getRegistrations: () => Promise<readonly RegistrationLike[]>
}

export function isMockWorkerRegistration(registration: RegistrationLike): boolean {
  return [registration.active, registration.waiting, registration.installing]
    .some((worker) => worker !== null && new URL(worker.scriptURL).pathname === MOCK_WORKER_PATH)
}

export async function unregisterMockWorkers(source?: RegistrationSource): Promise<number> {
  if (!source) return 0
  const registrations = await source.getRegistrations()
  const stale = registrations.filter(isMockWorkerRegistration)
  await Promise.all(stale.map((registration) => registration.unregister()))
  return stale.length
}

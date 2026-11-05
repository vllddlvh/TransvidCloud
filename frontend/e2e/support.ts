export const primaryOrigin = process.env.E2E_BASE_URL ?? 'http://localhost:5181'

export const auxiliaryOrigins = {
  real: process.env.E2E_REAL_ORIGIN ?? 'http://localhost:5182',
  putFailure: process.env.E2E_PUT_FAILURE_ORIGIN ?? 'http://localhost:5183',
  processFailure: process.env.E2E_PROCESS_FAILURE_ORIGIN ?? 'http://localhost:5184',
}

export const hasLocalAuxiliaryServers = process.env.E2E_BASE_URL === undefined

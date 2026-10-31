export type ApiMode = 'mock' | 'real'

export function resolveApiMode(configured: string | undefined, isProduction: boolean): ApiMode {
  const value = configured || (isProduction ? 'real' : 'mock')
  if (value !== 'mock' && value !== 'real') {
    throw new Error('VITE_API_MODE must be mock or real.')
  }
  return value
}

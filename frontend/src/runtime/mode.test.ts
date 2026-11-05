import { describe, expect, it } from 'vitest'
import { resolveApiMode } from './mode'

describe('resolveApiMode', () => {
  it('defaults to mock for development and real for production', () => {
    expect(resolveApiMode(undefined, false)).toBe('mock')
    expect(resolveApiMode(undefined, true)).toBe('real')
  })

  it('honors an explicit mode', () => {
    expect(resolveApiMode('mock', true)).toBe('mock')
    expect(resolveApiMode('real', false)).toBe('real')
  })

  it('rejects unknown values instead of silently guessing', () => {
    expect(() => resolveApiMode('staging', false)).toThrow('VITE_API_MODE must be mock or real.')
  })
})

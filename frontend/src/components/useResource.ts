import { useEffect, useRef, useState } from 'react'
import { useNotice } from './Notice'

const POLL_INTERVAL = 3000
const MAX_CONSECUTIVE_FAILURES = 3
const STATUS_UNKNOWN = 'Status unknown. Reload to try again.'

export function useResource<T>(load: () => Promise<T>, shouldPoll?: (data: T) => boolean) {
  const [state, setState] = useState<{ data: T | null; loading: boolean; error: string }>({ data: null, loading: true, error: '' })
  const [revision, setRevision] = useState(0)
  const notify = useNotice()
  const shouldPollRef = useRef(shouldPoll)
  shouldPollRef.current = shouldPoll

  useEffect(() => {
    let active = true
    let timer: ReturnType<typeof setTimeout> | undefined
    let running = false
    let failures = 0
    let latestData: T | null = null

    const clearTimer = () => {
      if (timer !== undefined) clearTimeout(timer)
      timer = undefined
    }
    const shouldContinue = () => latestData !== null
      && shouldPollRef.current?.(latestData) === true
      && document.visibilityState === 'visible'

    const schedule = (delay = POLL_INTERVAL) => {
      clearTimer()
      if (!active || !shouldContinue()) return
      timer = setTimeout(() => { void execute(false) }, delay)
    }

    const execute = async (initial: boolean) => {
      if (!active || running) return
      running = true
      clearTimer()
      try {
        const data = await load()
        if (!active) return
        running = false
        failures = 0
        latestData = data
        setState({ data, loading: false, error: '' })
        schedule()
      } catch (error) {
        if (!active) return
        running = false
        const message = error instanceof Error ? error.message : 'Unable to load this resource.'
        if (initial) {
          setState({ data: null, loading: false, error: message })
          notify(message)
          return
        }
        failures += 1
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
          setState((current) => ({ ...current, loading: false, error: STATUS_UNKNOWN }))
          return
        }
        schedule()
      }
    }

    latestData = null
    setState({ data: null, loading: true, error: '' })
    void execute(true)

    const handleVisibility = () => {
      if (document.visibilityState === 'hidden') {
        clearTimer()
      } else if (!running) {
        schedule(0)
      }
    }
    document.addEventListener('visibilitychange', handleVisibility)

    return () => {
      active = false
      clearTimer()
      document.removeEventListener('visibilitychange', handleVisibility)
    }
  }, [load, revision, notify])

  return { ...state, reload: () => setRevision((value) => value + 1) }
}

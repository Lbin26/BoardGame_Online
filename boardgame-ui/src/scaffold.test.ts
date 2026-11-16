import { describe, it, expect } from 'vitest'

import router from './router'
import { pinia } from './store'

describe('scaffold', () => {
  it('exports a router instance', () => {
    expect(router).toBeDefined()
    expect(router.currentRoute).toBeDefined()
  })

  it('exports a pinia instance', () => {
    expect(pinia).toBeDefined()
    expect(pinia.state).toBeDefined()
  })
})

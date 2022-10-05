import {
  mount
} from '@vue/test-utils'
import IndexPage from '@/pages/index.vue'

describe('has index page', () => {
  test('is a Vue instance', () => {
    const wrapper = mount(IndexPage)
    expect(wrapper.vm).toBeTruthy()
  })
})

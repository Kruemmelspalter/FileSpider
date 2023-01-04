export const state = () => ({
  documentCache: JSON.parse(localStorage.getItem('documentCache') || '{}'),
  apiSource: localStorage.getItem('apiSource') || '',
  searchResults: JSON.parse(localStorage.getItem('searchResults') || '[]'),
})

export const getters = {
  tags (state) {
    return [...new Set(Object.values(state.documentCache).map(x => x.tags).flat())]
  },
}

export const mutations = {
  setDocument (state, document) {
    state.documentCache[document.id] = document
  },
  setApiSource (state, apiSource) {
    state.apiSource = apiSource
  },
  setSearchResults (state, results) {
    state.searchResults = results
  },
  removeFromCache (state, id) {
    delete state.documentCache[id]
    state.searchResults = state.searchResults.filter(x => x !== id)
  },
}

export const actions = {
  async fetchDocument ({ state, commit }, id) {
    commit('setDocument', (await this.$axios.get(`${state.apiSource}/document/${id}`)).data)
  },
  async doSearch ({ state, commit }, search) {
    const response = await this.$axios.get(`${state.apiSource}/document/?filter=${search.join(',')}`)
    response.data.forEach((x) => {
      commit('setDocument', x)
    })
    commit('setSearchResults', response.data.sort((x, y) => new Date(y?.modified) - new Date(x?.modified)).map(x => x.id))
  },
  async deleteDocument ({ state, commit }, id) {
    await this.$axios.delete(`${state.apiSource}/document/${id}`)
    commit('removeFromCache', id)
  },
  async removeTag ({ state, dispatch }, { id, tag }) {
    await this.$axios.patch(`${state.apiSource}/document/${id}`, { removeTags: [tag] })
    await dispatch('fetchDocument', id)
  },
  async addTag ({ state, dispatch }, { id, tag }) {
    await this.$axios.patch(`${state.apiSource}/document/${id}`, { addTags: [tag] })
    dispatch('fetchDocument', id)
  },
  async updateTitle ({ state, dispatch }, { id, title }) {
    await this.$axios.patch(`${state.apiSource}/document/${id}`, { title })
    await dispatch('fetchDocument', id)
  },

  async createDocument ({ dispatch, state }, { title, tags, mime, renderer, editor, file, fileExtension }) {
    const formData = new FormData()
    formData.append('title', title)
    formData.append('tags', tags.join(','))
    if (mime !== null) {
      formData.append('mimeType', mime)
    }
    formData.append('renderer', renderer)
    formData.append('editor', editor)
    if (file !== null) {
      formData.append('file', file)
    }
    if (fileExtension !== null) {
      formData.append('fileExtension', fileExtension)
    }
    const response = await this.$axios.post(`${state.apiSource}/document/`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    await dispatch('fetchDocument', response.data)
    return response.data
  },
}

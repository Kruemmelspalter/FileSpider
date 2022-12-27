<template>
  <v-card class="pa-5">
    <v-row
      align="center"
      justify="center"
    >
      <v-card-title class="ma-2" style="font-size: xxx-large">
        FileSpider
      </v-card-title>
    </v-row>
    <v-row
      align="center"
      justify="center"
    >
      <v-btn color="primary" @click="showDocumentCreationDialog">
        Create Document
      </v-btn>
    </v-row>
    <DocumentCreationDialog ref="documentCreationDialog" :api-source="apiSource" />

    <v-form>
      <v-combobox
        v-model="search"
        autofocus
        chips
        class="mx-3 mt-5"
        multiple
        @input="onSearchChange"
        @click:prepend="commitSearch"
      />
    </v-form>
    <v-card v-for="r in searchResults" :key="r.id" class="ma-4" style="width: 20%" @click="redirect(r.id)">
      <v-card-title>{{ r.title }}</v-card-title>
    </v-card>
  </v-card>
</template>

<script>
export default {
  name: 'IndexPage',
  data () {
    return {
      search: [],
      searchResults: [],
      showSearchError: false,
      searchTimeout: null,
      apiSource: localStorage.getItem('apiSource') || '',
    }
  },
  methods: {
    redirect (a) {
      this.$router.push(a)
    },
    showDocumentCreationDialog () {
      this.$refs.documentCreationDialog.show()
    },
    commitSearch () {
      if (this.search.length === 0) {
        return
      }
      if (this.searchTimeout !== null) {
        clearTimeout(this.searchTimeout)
      }
      this.$axios.$get(`${this.apiSource}/document/?filter=${this.search.join(',')}`)
        .then((results) => {
          this.showSearchError = false
          this.searchResults = results
        })
        .catch((_) => {
          this.showSearchError = true
          this.searchResults = []
        })
    },
    onSearchChange () {
      if (this.searchTimeout !== null) {
        clearTimeout(this.searchTimeout)
      }
      this.searchTimeout = setTimeout(this.commitSearch, 1000)
      this.showSearchError = false
    },
  },
}
</script>

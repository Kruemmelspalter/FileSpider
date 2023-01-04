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
    <DocumentCreationDialog ref="documentCreationDialog" />

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
    <v-card
      v-for="r in $store.state.searchResults"
      :key="$store.state.documentCache[r]?.id"
      class="ma-4"
      style="width: 20%"
      @click="redirect($store.state.documentCache[r]?.id)"
    >
      <v-card-title>{{ $store.state.documentCache[r]?.title }}</v-card-title>
    </v-card>
  </v-card>
</template>

<script>
export default {
  name: 'IndexPage',
  data () {
    return {
      search: [],
      showSearchError: false,
      searchTimeout: null,
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
      this.$store.dispatch('doSearch', this.search)
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

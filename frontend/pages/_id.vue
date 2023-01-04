<template>
  <v-app dark>
    <v-navigation-drawer app>
      <v-form
        @submit.prevent="commitSearch"
      >
        <v-combobox
          v-model="search"
          :items="$store.state.tags"
          chips
          class="mx-3 mt-5"
          label="Search"
          multiple
          prepend-icon="mdi-file-search"
          @input="onSearchChange"
          @click:prepend="commitSearch"
        />
        <v-alert v-if="showSearchError" color="error" icon="mdi-alert">
          Search Error
        </v-alert>
      </v-form>
      <v-list>
        <v-list-item
          v-for="r in $store.state.searchResults"
          :key="$store.state.documentCache[r]?.id"
          :to="$store.state.documentCache[r]?.id"
          link
          nuxt
        >
          <v-list-item-content>
            <v-list-item-subtitle>
              {{ formatDate($store.state.documentCache[r]?.modified) }}
            </v-list-item-subtitle>
            <v-list-item-title>{{ $store.state.documentCache[r]?.title }}</v-list-item-title>
            <v-list-item-subtitle>
              <v-chip-group column>
                <v-chip
                  v-for="t in $store.state.documentCache[r]?.tags"
                  :key="t"
                  small
                  @click="search = [t]; commitSearch()"
                >
                  {{ t }}
                </v-chip>
              </v-chip-group>
            </v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
      </v-list>
    </v-navigation-drawer>

    <v-system-bar class="justify-end" color="primary">
      <v-btn v-if="displayingDocument" icon @click="showDocumentCreationDialog">
        <v-icon>mdi-file-document-plus</v-icon>
      </v-btn>
      <DocumentCreationDialog
        ref="documentCreationDialog"
        :initial-tags="$store.state.documentCache[documentID]?.tags"
      />
      <v-btn v-if="displayingDocument" icon @click="launchEditor">
        <v-icon>
          mdi-file-document-edit
        </v-icon>
      </v-btn>
      <v-bottom-sheet v-model="showDeleteConfirmation">
        <template #activator="{on, attrs}">
          <v-btn v-if="displayingDocument" icon v-bind="attrs" v-on="on">
            <v-icon>
              mdi-delete
            </v-icon>
          </v-btn>
        </template>
        <v-sheet
          class="text-center py-5"
        >
          <v-btn color="error" @click="showDeleteConfirmation = false; deleteDocument()">
            Really delete document?
          </v-btn>
        </v-sheet>
      </v-bottom-sheet>

      <v-btn
        v-if="displayingDocument"
        icon
        @click="reload"
      >
        <v-icon>mdi-reload</v-icon>
      </v-btn>
      <v-btn
        v-if="displayingDocument"
        icon
        @click="documentInvert = !documentInvert"
      >
        <v-icon :class="{turned: documentInvert}">
          mdi-circle-half-full
        </v-icon>
      </v-btn>
      <v-btn
        icon
        @click="darkMode = !darkMode"
      >
        <v-icon>mdi-theme-light-dark</v-icon>
      </v-btn>
      {{ time }}
    </v-system-bar>

    <v-main>
      <DocumentDisplay
        v-if="displayingDocument"
        ref="documentDisplay"
        :document-invert="documentInvert"
        :search="commitSearch"
      />
    </v-main>
  </v-app>
</template>

<script>
import DocumentCreationDialog from '@/components/DocumentCreationDialog'
import DocumentDisplay from '@/components/DocumentDisplay'

export default {
  name: 'DocumentViewer',
  components: { DocumentDisplay, DocumentCreationDialog },
  layout: 'blank',
  data () {
    if (this.$route.params.id.match(/[\da-fA-F]{8}\b-[\da-fA-F]{4}\b-[\da-fA-F]{4}\b-[\da-fA-F]{4}\b-[\da-fA-F]{12}/)) {
      this.$store.dispatch('fetchDocument', this.$route.params.id)
    }
    for (const r of this.$store.state.searchResults) {
      this.$store.dispatch('fetchDocument', r)
    }
    return {
      darkMode: true,
      search: JSON.parse(localStorage.getItem('searchTerm') || '[]'),
      time: null,
      interval: null,
      searchTimeout: null,
      showSearchError: false,
      documentInvert: true,
      showDeleteConfirmation: false,
    }
  },
  computed: {
    documentID () {
      return this.$route.params.id
    },
    displayingDocument () {
      return !!this.documentID.match(/[\da-fA-F]{8}\b-[\da-fA-F]{4}\b-[\da-fA-F]{4}\b-[\da-fA-F]{4}\b-[\da-fA-F]{12}/)
    },
  },
  watch: {
    darkMode (newVal, _) {
      this.$vuetify.theme.dark = newVal
    },

  },
  created () {
    this.interval = setInterval(() => {
      this.time = Intl.DateTimeFormat(navigator.language, {
        hour: 'numeric',
        minute: 'numeric',
        second: 'numeric',
      }).format()
    }, 1000)
    if (this.search !== []) {
      this.commitSearch()
    }
  },
  beforeDestroy () {
    // prevent memory leak
    clearInterval(this.interval)

    localStorage.setItem('searchTerm', JSON.stringify(this.search))
    localStorage.setItem('searchResults', JSON.stringify(this.$store.state.searchResults))
    localStorage.setItem('documentCache', JSON.stringify(this.$store.state.documentCache))
  },
  methods: {
    launchEditor () {
      this.$axios.post(`${this.apiSource}/document/${this.documentID}/edit`)
    },
    showDocumentCreationDialog () {
      this.$refs.documentCreationDialog.show()
    },
    formatDate (date) {
      if (date === undefined) {
        return ''
      }
      return Intl.DateTimeFormat(navigator.language,
        { dateStyle: 'short', timeStyle: 'short' },
      ).format(new Date(date))
    },
    reload () {
      this.$refs.documentDisplay.reload()
    },
    commitSearch (x = null) {
      if (x !== null) {
        this.search = [x]
      }
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
    deleteDocument () {
      this.$store.dispatch('deleteDocument', this.documentID)
        .then(() => {
          this.$router.push('/index')
        })
    },
  },
}
</script>

<style scoped>

.turned {
  transform: rotate(180deg);
}

@keyframes rotate {
  0% {
    transform: rotate(0);
  }
  100% {
    transform: rotate(360deg);
  }
}
</style>

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
      <v-btn icon @click="showDocumentCreationDialog">
        <v-icon>mdi-file-document-plus</v-icon>
      </v-btn>
      <DocumentCreationDialog
        ref="documentCreationDialog"
        :initial-tags="$store.state.documentCache[documentID]?.tags"
      />
      <v-btn icon @click="launchEditor">
        <v-icon>
          mdi-file-document-edit
        </v-icon>
      </v-btn>
      <v-bottom-sheet v-model="showDeleteConfirmation">
        <template #activator="{on, attrs}">
          <v-btn icon v-bind="attrs" v-on="on">
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
        icon
        @click="reload"
      >
        <v-icon>mdi-reload</v-icon>
      </v-btn>
      <v-btn
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
      <v-card>
        <v-card-title>
          <v-form style="width: 100%" @submit.prevent="changeTitle(documentTitle)">
            <v-text-field v-model="documentTitle" dense full-width />
          </v-form>
        </v-card-title>
        <v-card-subtitle>
          <span style="user-select: none" @click="copyIdToClipboard" v-text="documentID" />
          <v-snackbar v-model="showIdCopySnackbar">
            Copied to clipboard
            <template #action="{ attrs }">
              <v-btn text v-bind="attrs" @click="showIdCopySnackbar = false">
                Close
              </v-btn>
            </template>
          </v-snackbar>
          <v-chip-group column>
            <v-chip
              v-for="t in $store.state.documentCache[documentID]?.tags"
              :key="t"
              close
              small
              @click="search = [t]; commitSearch()"
              @click:close="removeTag(t)"
            >
              {{ t }}
            </v-chip>
            <v-dialog
              v-model="showTagDialog"
              width="500"
            >
              <template #activator="{ on, attrs }">
                <v-btn icon small v-bind="attrs" v-on="on">
                  <v-icon>mdi-plus-circle</v-icon>
                </v-btn>
              </template>

              <v-card>
                <v-card-title>
                  Add Tag
                </v-card-title>

                <v-form @submit.prevent="addTag(tagToAdd); tagToAdd=''">
                  <v-text-field
                    v-model="tagToAdd"
                    autofocus
                    class="mx-5"
                    label="Tag"
                    required
                  />
                </v-form>
              </v-card>
            </v-dialog>
          </v-chip-group>
        </v-card-subtitle>
        <v-card-text>
          <iframe
            v-show="iframeState === 1"
            id="document-content"
            ref="documentContent"
            :class="{dark: documentInvert}"
            :src="`${$store.state.apiSource}/document/${documentID}/rendered`"
            @load="iframeState = 1"
          >
            <v-alert v-if="iframeState === 2" color="error" icon="mdi-alert">Error loading document</v-alert>
          </iframe>
          <v-alert v-if="iframeState === 0" color="secondary">
            <v-icon class="turning">
              mdi-loading
            </v-icon>
            Loading...
          </v-alert>
          <v-alert v-if="iframeState===2" color="error" icon="mdi-alert">
            Error loading document (timeout)
          </v-alert>
        </v-card-text>
      </v-card>
    </v-main>
  </v-app>
</template>

<!--suppress SillyAssignmentJS -->
<script>
import DocumentCreationDialog from '@/components/DocumentCreationDialog'

export default {
  name: 'DocumentViewer',
  components: { DocumentCreationDialog },
  layout: 'blank',
  data () {
    this.$store.dispatch('fetchDocument', this.$route.params.id)
    for (const r of this.$store.state.searchResults) {
      this.$store.dispatch('fetchDocument', r)
    }
    return {
      iframeState: 0, // 0: loading, 1: loaded, 2: error
      darkMode: true,
      search: JSON.parse(localStorage.getItem('searchTerm') || '[]'),
      time: null,
      interval: null,
      searchTimeout: null,
      showSearchError: false,
      documentInvert: true,
      showTagDialog: false,
      tagToAdd: '',
      documentTitle: this.$store.state.documentCache[this.$route.params.id]?.title,
      showDeleteConfirmation: false,
      showIdCopySnackbar: false,
    }
  },
  computed: {
    documentID () {
      return this.$route.params.id
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
    setTimeout(() => {
      if (this.iframeState === 0) {
        this.iframeState = 2
      }
    }, 5e3)
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
    copyIdToClipboard () {
      navigator.clipboard.writeText(this.documentID)
      this.showIdCopySnackbar = true
      setTimeout(() => {
        this.showIdCopySnackbar = false
      }, 1e3)
    },
    changeTitle (newTitle) {
      this.$store.dispatch('updateTitle', { id: this.documentID, title: newTitle })
    },
    addTag (tag) {
      this.$store.dispatch('addTag', { id: this.documentID, tag })
        .then(() => {
          this.showTagDialog = false
        })
    },
    removeTag (tag) {
      this.$store.dispatch('removeTag', { id: this.documentID, tag })
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
      this.iframeState = 0
      // eslint-disable-next-line no-self-assign
      this.$refs.documentContent.src = this.$refs.documentContent.src
      setTimeout(() => {
        if (this.iframeState === 0) {
          this.iframeState = 2
        }
      }, 5e3)
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
    deleteDocument () {
      this.$store.dispatch('deleteDocument', this.documentID)
    },
  },
}
</script>

<style scoped>
#document-content {
  width: 100%;
  height: 100%;
  min-height: 80vh;
  border: none;
}

.dark {
  filter: invert(80%);
}

.turning {
  animation: 1s linear infinite rotate;
}

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

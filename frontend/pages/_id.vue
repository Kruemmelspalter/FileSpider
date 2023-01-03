<template>
  <v-app dark>
    <v-navigation-drawer v-model="showSidebar" app>
      <v-form
        @submit.prevent="commitSearch"
      >
        <v-combobox
          v-model="search"
          :items="Array.from(tagCache)"
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
          v-for="r in searchResults"
          :key="documentCache[r]?.id"
          :to="documentCache[r]?.id"
          link
          nuxt
        >
          <v-list-item-content>
            <v-list-item-subtitle>
              {{ formatDate(documentCache[r]?.modified) }}
            </v-list-item-subtitle>
            <v-list-item-title>{{ documentCache[r]?.title }}</v-list-item-title>
            <v-list-item-subtitle>
              <v-chip-group column>
                <v-chip v-for="t in documentCache[r]?.tags" :key="t" small @click="search = [t]; commitSearch()">
                  {{ t }}
                </v-chip>
              </v-chip-group>
            </v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
      </v-list>
    </v-navigation-drawer>
    <v-main>
      <v-system-bar color="primary">
        <v-row>
          <v-col>
            <v-btn v-if="showSidebar === false" class="hidden-lg-and-up" icon @click="showSidebar = true">
              <v-icon>mdi-menu</v-icon>
            </v-btn>
          </v-col>
          <v-col class="justify-end" style="display: flex">
            <v-btn icon @click="showDocumentCreationDialog">
              <v-icon>mdi-file-document-plus</v-icon>
            </v-btn>
            <DocumentCreationDialog
              ref="documentCreationDialog"
              :api-source="apiSource"
              :initial-tags="new Set(documentCache[documentID]?.tags)"
              :tag-cache="tagCache"
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
            <span class="pt-2">{{ time }}</span>
          </v-col>
        </v-row>
      </v-system-bar>
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
              v-for="t in documentCache[documentID]?.tags"
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
            :src="`${apiSource}/document/${documentID}/rendered`"
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
    return {
      iframeState: 0, // 0: loading, 1: loaded, 2: error
      darkMode: true,
      search: JSON.parse(localStorage.getItem('searchTerm') || '[]'),
      time: null,
      interval: null,
      searchTimeout: null,
      searchResults: JSON.parse(localStorage.getItem('searchResults') || '[]'),
      showSearchError: false,
      documentInvert: true,
      apiSource: localStorage.getItem('apiSource') || '',
      documentCache: JSON.parse(localStorage.getItem('documentCache') || '{}'),
      showTagDialog: false,
      tagToAdd: '',
      documentTitle: '',
      tagCache: new Set(),
      showDeleteConfirmation: false,
      showIdCopySnackbar: false,
      showSidebar: true,
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

    this.queryDocument(this.documentID)
    for (const r of this.searchResults) {
      this.queryDocument(r)
    }
    if (this.search !== []) {
      this.commitSearch()
    }
  },
  beforeDestroy () {
    // prevent memory leak
    clearInterval(this.interval)

    localStorage.setItem('searchTerm', JSON.stringify(this.search))
    localStorage.setItem('searchResults', JSON.stringify(this.searchResults))
    localStorage.setItem('documentCache', JSON.stringify(this.documentCache))
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
    queryDocument: function (document) {
      this.$axios.$get(`${this.apiSource}/document/${document}`)
        .then((results) => {
          this.addDocumentsToCache([results])
          if (document === this.documentID) {
            this.documentTitle = this.documentCache[this.documentID]?.title
          }
        })
    },
    addDocumentsToCache (documents) {
      for (const d of documents) {
        this.documentCache[d.id] = d
        for (const t of d.tags) {
          this.tagCache.add(t)
        }
      }
    },
    changeTitle (newTitle) {
      this.$axios.$patch(`${this.apiSource}/document/${this.documentID}`, { title: newTitle })
        .then(() => {
          this.queryDocument(this.documentID)
        })
    },
    addTag (tag) {
      this.$axios.$patch(`${this.apiSource}/document/${this.documentID}`, { addTags: [tag] })
        .then(() => {
          this.queryDocument(this.documentID)
          this.showTagDialog = false
        })
    },
    removeTag (tag) {
      this.$axios.$patch(`${this.apiSource}/document/${this.documentID}`, { removeTags: [tag] })
        .then(() => {
          this.queryDocument(this.documentID)
        })
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
      this.$axios.$get(`${this.apiSource}/document/?filter=${this.search.join(',')}`)
        .then((results) => {
          this.showSearchError = false
          this.searchResults = results.sort((x, y) => new Date(y?.modified) - new Date(x?.modified)).map(r => r.id)
          this.addDocumentsToCache(results)
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
    deleteDocument () {
      this.$axios.$delete(`${this.apiSource}/document/${this.documentID}`)
        .then(() => {
          this.$router.push('/')
          delete this.documentCache[this.documentID]
          this.searchResults = this.searchResults.filter(x => x !== this.documentID)
        })
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

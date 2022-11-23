<template>
  <v-app dark>
    <v-navigation-drawer app>
      <v-form
        @submit.prevent="commitSearch"
      >
        <v-text-field
          v-model="search"
          class="mx-3 mt-5"
          label="Search"
          prepend-icon="mdi-file-search"
          @input="onSearchChange"
          @click:prepend="commitSearch"
        />
        <v-alert v-if="showSearchError" color="error" icon="mdi-alert">
          Search Error
        </v-alert>
      </v-form>
      <v-list>
        <v-list-item v-for="r in searchResults" :key="r.id" :to="r.id" link nuxt>
          <v-list-item-content>
            <v-list-item-subtitle>
              {{ formatDate(new Date(r.modified)) }}
            </v-list-item-subtitle>
            <v-list-item-title>{{ r.title }}</v-list-item-title>
            <v-list-item-subtitle>
              <v-chip-group column>
                <v-chip v-for="t in r.tags" :key="t" small @click="search = t; commitSearch()">
                  {{ t }}
                </v-chip>
              </v-chip-group>
            </v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
      </v-list>
    </v-navigation-drawer>

    <v-system-bar class="justify-end" color="primary">
      <v-btn
        icon
        @click="reload()"
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
      <v-card style="height:auto; min-height:50vh">
        <v-card-title>{{ documentData.title }}</v-card-title>
        <v-card-subtitle>
          <v-chip-group column>
            <v-chip
              v-for="t in documentData.tags"
              :key="t"
              close
              small
              @click="search = t; commitSearch()"
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
          <object
            v-show="iframeState === 1"
            id="document-content"
            ref="documentContent"
            :class="{dark: documentInvert}"
            :data="`${apiSource}/document/${documentID}/rendered`"
            @load="iframeState = 1"
          >
            <v-alert color="error" icon="mdi-alert">Error loading document</v-alert>
          </object>
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
export default {
  name: 'DocumentViewer',
  layout: 'blank',
  data () {
    return {
      iframeState: 0, // 0: loading, 1: loaded, 2: error
      darkMode: true,
      search: localStorage.getItem('searchTerm') || '',
      time: null,
      interval: null,
      searchTimeout: null,
      searchResults: JSON.parse(localStorage.getItem('searchResults') || '[]'),
      showSearchError: false,
      documentInvert: true,
      apiSource: 'http://172.31.69.3',
      documentData: {},
      showTagDialog: false,
      tagToAdd: '',
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
    }, 5e3) // TODO adjust timeout?

    this.$axios.$get(`${this.apiSource}/document/${this.documentID}`)
      .then((results) => {
        this.documentData = results
      })
      .catch((_) => {
        this.documentData = null
      })
  },
  beforeDestroy () {
    // prevent memory leak
    clearInterval(this.interval)

    localStorage.setItem('searchTerm', this.search)
    localStorage.setItem('searchResults', JSON.stringify(this.searchResults))
  },
  methods: {
    addTag (tag) {
      this.$axios.$patch(`${this.apiSource}/document/${this.documentID}`, { addTags: [tag] })
        .then(() => {
          this.documentData.tags.push(tag)
          this.showTagDialog = false
        })
    },
    removeTag (tag) {
      this.$axios.$patch(`${this.apiSource}/document/${this.documentID}`, { removeTags: [tag] })
        .then(() => {
          this.documentData.tags = this.documentData.tags.filter(t => t !== tag)
        })
    },
    formatDate (date) {
      return Intl.DateTimeFormat(navigator.language, { dateStyle: 'short', timeStyle: 'short' }).format(date)
    },
    reload () {
      this.iframeState = 0
      // eslint-disable-next-line no-self-assign
      this.$refs.documentContent.data = this.$refs.documentContent.data
    },
    commitSearch () {
      if (this.searchTimeout !== null) {
        clearTimeout(this.searchTimeout)
      }
      this.$axios.$get(`${this.apiSource}/document/?filter=${this.search}`)
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
      this.searchTimeout = setTimeout(this.commitSearch, 3000)
      this.showSearchError = false
    },
  },
}
</script>

<style scoped>
#document-content {
  width: 100%;
  height: 100%;
  min-height: 83vh;
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
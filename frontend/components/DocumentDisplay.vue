<template>
  <v-card style="height: 97%">
    <v-card-title v-if="!fullscreen">
      <v-form style="width: 100%" @submit.prevent="changeTitle(documentTitle)">
        <v-text-field v-model="documentTitle" dense full-width />
      </v-form>
    </v-card-title>
    <v-card-subtitle v-if="!fullscreen">
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
          @click="search(t)"
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

            <v-form class="pa-5" @submit.prevent="addTag(tagToAdd); tagToAdd=''">
              <v-combobox
                v-model="tagToAdd"
                :items="$store.getters.tags"
                autofocus
                label="Tag"
                required
              />
              <v-btn type="submit">
                Submit
              </v-btn>
            </v-form>
          </v-card>
        </v-dialog>
      </v-chip-group>
    </v-card-subtitle>
    <v-card-text :style="fullscreen ? {height: '100%'} : {}">
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
</template>

<!--suppress SillyAssignmentJS -->
<script>
export default {
  name: 'DocumentDisplay',
  props: {
    documentInvert: {
      type: Boolean,
    },
    search: {
      type: Function,
      required: true,
    },
    fullscreen: {
      type: Boolean,
      default: () => false,
    },
  },
  data () {
    this.$store.dispatch('fetchDocument', this.$route.params.id)
      .then(() => {
        this.documentTitle = this.$store.state.documentCache[this.$route.params.id]?.title
      })
    return {
      showIdCopySnackbar: false,
      showTagDialog: false,
      tagToAdd: '',
      iframeState: 0,
      documentTitle: this.$store.state.documentCache[this.$route.params.id]?.title,
    }
  },
  computed: {
    documentID () {
      return this.$route.params.id
    },
  },
  created () {
    setTimeout(() => {
      if (this.iframeState === 0) {
        this.iframeState = 2
      }
    }, 20e3)
  },
  methods: {
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
  },
}
</script>
<style scoped>
#document-content {
  width: 100%;
  height: 100%;
  min-height: 78.3vh;
  border: none;
}

.dark {
  filter: invert(80%);
}

.turning {
  animation: 1s linear infinite rotate;
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

<template>
  <v-app dark>
    <v-navigation-drawer app class="px-3">
      <v-form>
        <v-text-field
          v-model="search"
          label="Search"
          prepend-icon="mdi-file-search"
        />
      </v-form>
      <v-list>
        <v-list-item link>
          a
        </v-list-item>
      </v-list>
    </v-navigation-drawer>

    <v-system-bar class="justify-end" color="primary">
      <v-btn
        icon
        @click="darkMode = !darkMode"
      >
        <v-icon>mdi-theme-light-dark</v-icon>
      </v-btn>
      {{ time }}
    </v-system-bar>

    <v-main>
      <object
        v-show="iframeState === 1"
        :class="{dark: darkMode}"
        :data="`http://172.31.69.3/document/${$route.params.id}/rendered`"
        class="document-content"
        @load="iframeState = 1"
      >
        <v-banner color="error" icon="mdi-error">Error loading document</v-banner>
      </object>
      <v-banner v-if="iframeState === 0" color="secondary">
        <v-icon class="turning">
          mdi-loading
        </v-icon>
        Loading...
      </v-banner>
    </v-main>
  </v-app>
</template>

<script>
export default {
  name: 'DocumentViewer',
  layout: 'blank',
  data () {
    return {
      iframeState: 0, // 0: loading, 1: loaded
      darkMode: localStorage.getItem('lightMode') === null,
      search: '',
      time: null,
      interval: null,
    }
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
  },
  beforeDestroy () {
    // prevent memory leak
    clearInterval(this.interval)
  },
}
</script>

<style scoped>
.document-content {
  width: 100%;
  height: 99vh;
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

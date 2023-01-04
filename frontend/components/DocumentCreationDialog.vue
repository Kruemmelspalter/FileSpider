<template>
  <v-dialog
    v-model="visible"
    width="500"
  >
    <v-card>
      <v-card-title>Add Document</v-card-title>
      <v-form ref="creationForm" class="px-5 pb-3" @submit.prevent="createDocument">
        <v-text-field v-model="creationMeta.title" :rules="[v=>!!v||'Title is required']" autofocus label="Title" />
        <v-combobox
          v-model="creationMeta.tags"
          :items="$store.state.tags"
          :rules="[v => v.length !== 0||'At least one tag is required']"
          chips
          label="Tags"
          multiple
        />
        <v-file-input
          v-model="creationMeta.file"
          :rules="[v=>!!v||!!creationMeta.mime||'Either file or mime type is required']"
          chips
          counter
          label="File (optional)"
          show-size
        />
        <v-text-field
          v-model="creationMeta.mime"
          :rules="[
            v => v !== null || creationMeta.file !== null || 'Either file or mime type is required',
            v => v === null || v.length <= 0 || /^[-a-z]+\/[-a-z]+(?:\+[-a-z]+)?$/.test(v) || 'Invalid mime type',
          ]"
          label="MIME Type (auto by default)"
        />
        <v-text-field v-model="creationMeta.renderer" label="Renderer (mime specific by default)" />
        <v-text-field v-model="creationMeta.editor" label="Editor (mime specific by default)" />
        <v-text-field v-model="creationMeta.fileExtension" label="File Extension (auto from file name by default)" />
        <v-btn type="submit">
          Submit
        </v-btn>
      </v-form>
    </v-card>
  </v-dialog>
</template>
<script>
export default {
  name: 'DocumentCreationDialog',
  props: {
    initialTags: {
      type: Array,
      default: () => [],
    },
  },
  data () {
    return {
      visible: false,
      creationMeta: {
        tags: this.initialTags,
        title: null,
        mime: null,
        renderer: null,
        editor: null,
        file: null,
        fileExtension: null,
      },
    }
  },
  watch: {
    initialTags (old, newVal) {
      if (this.creationMeta.tags === Array.from(old) || this.creationMeta.tags === []) {
        this.creationMeta.tags = Array.from(newVal)
      }
    },
  },
  methods: {
    createDocument () {
      if (!this.$refs.creationForm.validate()) {
        return
      }
      this.$store.dispatch('createDocument', {
        title: this.creationMeta.title,
        tags: this.creationMeta.tags,
        mime: this.creationMeta.mime,
        renderer: this.creationMeta.renderer || 'mime',
        editor: this.creationMeta.editor || 'mime',
        file: this.creationMeta.file,
        fileExtension: this.creationMeta.fileExtension || this.creationMeta.file?.name?.split('.')?.at(-1),
      })
        .then((newId) => {
          this.$router.push(newId)
          this.visible = false
          this.$refs.creationForm.reset()
        })
    },
    show () {
      this.visible = true
    },
  }
  ,
}
</script>
<style scoped>

@keyframes rotate {
  0% {
    transform: rotate(0);
  }
  100% {
    transform: rotate(360deg);
  }
}
</style>

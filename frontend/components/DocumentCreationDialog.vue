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
          :items="Array.from(tagCache)"
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
    tagCache: {
      type: Set,
      default: () => new Set(),
    },
    apiSource: {
      type: String,
      default: '',
    },
    initialTags: {
      type: Set,
      default: () => new Set(),
    },
  },
  data () {
    return {
      visible: false,
      creationMeta: {
        tags: Array.from(this.initialTags),
        title: null,
        mime: null,
        renderer: null,
        editor: null,
        file: null,
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
      const formData = new FormData()
      formData.append('title', this.creationMeta.title)
      formData.append('tags', this.creationMeta.tags.join(','))
      if (this.creationMeta.mime !== null) {
        formData.append('mimeType', this.creationMeta.mime || this.creationMeta.file?.type)
      }
      formData.append('renderer', this.creationMeta.renderer || 'mime')
      formData.append('editor', this.creationMeta.editor || 'mime')
      if (this.creationMeta.file !== null) {
        formData.append('file', this.creationMeta.file)
      }
      this.$axios.$post(`${this.apiSource}/document/`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
        .then((newId) => {
          this.$router.push(newId)
          this.visible = false
          this.$refs.creationForm.reset()
          this.commitSearch()
        })
    },
    show () {
      console.log('showing')
      this.visible = true
    },
  },
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

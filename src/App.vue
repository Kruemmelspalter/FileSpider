<script lang="ts" setup>
import {onMounted, Ref, ref, VNode, watch} from "vue";
import {computedAsync} from "@vueuse/core";

import {invoke} from "@tauri-apps/api";
import {convertFileSrc} from "@tauri-apps/api/tauri";
import {VTextField} from "vuetify/components";
import {readTextFile} from "@tauri-apps/api/fs";
import TauriFileInput from "./components/TauriFileInput.vue";
import {appWindow} from "@tauri-apps/api/window";

// noinspection JSUnusedGlobalSymbols
const vVisible = {
  updated(el: HTMLElement, binding: {
    value: boolean
  }, _vnode: VNode, _prevVnode: VNode) {
    el.style.visibility = binding.value ? "visible" : "hidden";
  }
}

type DocMeta = {
  id: string,
  title: string,
  docType: string,
  created: Date,
  accessed: Date,
  tags: string[],
  extension: string | undefined,
}

const id = ref<string | undefined>(undefined);

const meta = computedAsync<DocMeta | undefined>(async () => {
  if (id.value === undefined) return undefined;

  await invoke('plugin:document|update_accessed', {id: id.value});
  return <DocMeta | undefined>(await invoke('plugin:document|get_meta', {id: id.value})
      .catch(error => {
        addAlert("Error while fetching document meta", <string>error, "error", true, 10000)
        return undefined
      }))
}, undefined);

const rendered = computedAsync<[string, string] | undefined>(async () => {
  if (id.value === undefined) return undefined;
  return <[string, string] | undefined>(await invoke('plugin:document|render', {id: id.value}).catch(error => {
    addAlert("Error while rendering document", <string>error, "error", true, 10000);
  }))
}, undefined);

const fullscreen = ref(false);

function toggleFullscreen() {
  fullscreen.value = !fullscreen.value;
}

const workingTitle = ref<string | undefined>(undefined);

watch(meta, (final: DocMeta | undefined) => {
  workingTitle.value = final?.title
})

watch(workingTitle, async (final: string | undefined) => {
  if (final === undefined || meta.value === undefined) return;
  if (final === meta.value?.title) return;
  if (final.trim() === "") return;
  await invoke('plugin:document|alter_meta', {id: id.value, patch: {"ChangeTitle": final}});
})

function triggerMetaUpdate() {
  let x = id.value;
  id.value = "";
  id.value = x;
}

async function removeTag(tag: string) {
  await invoke('plugin:document|alter_meta', {id: id.value, patch: {"RemoveTag": tag}})
      .then(() => {
        addAlert(undefined, `Tag '${tag}' removed`, "success", true, 1000);
        triggerMetaUpdate();
      })
      .catch(error => {
        addAlert(`Error while removing tag '${tag}'`, <string>error, "error", true, 10000)
      });


}

const plainContent = ref("");
watch(rendered, async () => {
  if (rendered.value === undefined) {
    return;
  }
  switch (rendered.value[1]) {
    case "Plain":
      plainContent.value = await readTextFile(rendered.value?.[0]);
      break;
    case "Pdf":
      if (pdfViewer.value === null) return;
      // noinspection SillyAssignmentJS
      pdfViewer.value.data = pdfViewer.value.data;
      break;
    default:
      break;
  }
});

const sidebarIsOpen = ref(true);
const addTagDialog = ref(false);
const newTag = ref("");

async function addTag() {
  if (newTag.value.trim() === "") return;
  await invoke('plugin:document|alter_meta', {
    id: id.value,
    patch: {"AddTag": newTag.value.trim()}
  })
      .then(() => addAlert(undefined, `Tag '${newTag.value.trim()}' added`, "success", true, 1000))
      .catch(error => {
        addAlert(`Error while adding tag '${newTag.value.trim()}'`, <string>error, "error", true, 10000)
      });


  triggerMetaUpdate();

  newTag.value = "";
  addTagDialog.value = false;
}

const alerts:
    Ref<[string | undefined, string, "error" | "success" | "warning" | "info" | undefined, boolean][]>
    = ref([]);
const deleteSheet = ref(false);


function addAlert(title: string | undefined, content: string, type: "error" | "success" | "warning" | "info" | undefined, closable: boolean, timeout: number | undefined) {
  let val: [string | undefined, string, "error" | "success" | "warning" | "info" | undefined, boolean] = [title, content, type, closable];
  alerts.value.push(val)
  if (timeout !== undefined)
    setTimeout(() => alerts.value.splice(alerts.value.indexOf(val), 1), timeout);
  return val
}

async function deleteDocument() {
  await invoke('plugin:document|delete', {id: id.value})
      .then(() => {
        addAlert(undefined, "Document deleted", "success", true, 1000)
        // TODO redirect to home
      })
      .catch(error =>
          addAlert("Error while deleting document", <string>error, "error", true, 10000)
      );
}

const createDialog = ref(false);
const createTab = ref("create");

const createValid = ref(false);

const createData = ref<{
  title: string,
  tags: string[],
  file: {
    Path: string
  } | {
    Blob: [number]
  } | undefined,
  tagSearch: string,
  docType: "Plain" | "Html" | "Markdown" | "LaTeX" | "XournalPP",
  extension: string,
}>({title: "", tags: [], file: undefined, tagSearch: "", docType: "Plain", extension: ""});

const createSuggestTags = computedAsync<string[]>(async () => {
  if (createData.value.tagSearch.trim() === '') return [];
  return <string[]>(await invoke('plugin:document|get_tags', {crib: createData.value.tagSearch})
      .catch(error =>
          addAlert("Error while fetching tag suggestions", <string>error, "error", true, 10000)
      ) || [])
}, []);

async function createDocument() {
  if (createTab.value === "create") {
    // noinspection ES6MissingAwait
    await (<Promise<string>>invoke('plugin:document|create', {
      title: createData.value.title,
      tags: createData.value.tags,
      docType: createData.value.docType,
      extension: createData.value.extension === "" ? undefined : createData.value.extension,
      file: createData.value.file
    }))
        .then((newId: string) => {
          addAlert(undefined, "Document created", "success", true, 1000)
          id.value = newId;
          createDialog.value = false;
        })
        .catch(error =>
            addAlert("Error while creating document", <string>error, "error", true, 10000)
        );
  } else if (createTab.value === "import") {

    // noinspection ES6MissingAwait
    await (<Promise<string>>invoke('plugin:document|import_pdf', {
      title: createData.value.title,
      tags: createData.value.tags,
      file: createData.value.file
    }))
        .then((newId: string) => {
          addAlert(undefined, "Document imported", "success", true, 1000)
          id.value = newId;
          createDialog.value = false;
        })
        .catch(error =>
            addAlert("Error while importing document", <string>error, "error", true, 10000)
        );
  }
}

const posTagsSearch = ref("");
const posTagsSuggestions = computedAsync<string[]>(async () => {
  if (posTagsSearch.value.trim() === '') return [];
  return <string[]>(await invoke('plugin:document|get_tags', {crib: posTagsSearch.value})
      .catch(error =>
          addAlert("Error while fetching tag suggestions", <string>error, "error", true, 10000)
      ) || [])
}, []);

const negTagsSearch = ref("");
const negTagsSuggestions = computedAsync<string[]>(async () => {
  if (negTagsSearch.value.trim() === '') return [];
  return <string[]>(await invoke('plugin:document|get_tags', {crib: negTagsSearch.value})
      .catch(error =>
          addAlert("Error while fetching tag suggestions", <string>error, "error", true, 10000)
      ) || [])
}, []);

const posTags = ref<string[]>([]);
const negTags = ref<string[]>([]);
const titleCrib = ref<string>("");

const searchValid = ref(false);

const searchResults = ref<DocMeta[]>([]);

const page = ref(0);
const pageLength = ref(10);

const sorting = ref('AccessTime');

async function getSearchResults() {
  if (posTags.value.length !== 0) {

    let res = await invoke('plugin:document|search', {
      posFilter: posTags.value,
      negFilter: negTags.value,
      crib: titleCrib.value,
      page: page.value,
      pageLength: pageLength.value,
      sort: [sorting.value, false]
    })
        .catch(error =>
            addAlert("Error while searching", <string>error, "error", true, 10000)
        );
    searchResults.value = <DocMeta[]>res;
  }
  if (searchResults.value.length == 0 && page.value > 0) {
    page.value = 0;
    await getSearchResults()
  }
}

async function search() {
  page.value = 0;
  await getSearchResults()
}

async function openEditor() {
  await invoke('plugin:document|open_editor', {id: id.value})
      .catch(error =>
          addAlert("Error while opening editor", <string>error, "error", true, 10000)
      );
}

const pdfViewer = ref<HTMLObjectElement | null>(null);

async function showRenderInExplorer() {
  await invoke('plugin:document|show_render_in_explorer', {id: id.value})
      .catch(error =>
          addAlert("Error while opening file explorer", <string>error, "error", true, 10000)
      );
}

function formatDate(date: any): String {
  return Intl.DateTimeFormat(navigator.language,
      {dateStyle: 'short', timeStyle: 'short'},
  ).format(new Date(date));
}

const presets = ref<[{
  name: string,
  tags: [string],
  extension: string | null,
  doc_type: "Plain" | "Html" | "Markdown" | "LaTeX" | "XournalPP" | null,
  file: {Path: string} | {Blob: [number]} | undefined,
}] | null>(null);

onMounted(async () => {
  sidebarIsOpen.value = await appWindow.isMaximized();
  presets.value = <[{
    name: string,
    tags: [string],
    extension: string | null,
    doc_type: "Plain" | "Html" | "Markdown" | "LaTeX" | "XournalPP" | null,
    file: {
      Path: string
    } | {
      Blob: [number]
    } | undefined,
  }] | null>(await invoke('plugin:settings|get_presets')
      .catch(error =>
          addAlert("Error while fetching presets", <string>error, "error", true, 10000)
      ));
})

async function applyPreset(preset: string) {
  let p = presets.value?.find(p => p.name === preset);

  if (p === undefined) return;

  if ((<number>p.tags.length) !== 0) createData.value.tags = createData.value.tags.concat(p.tags);

  if (p.extension !== null) createData.value.extension = p.extension;

  if (p.doc_type !== null) createData.value.docType = p.doc_type;

  if (p.file !== undefined) createData.value.file = p.file;
}

</script>

<template>
  <v-app id="filespider">
    <div class="position-fixed " style="bottom: 2vh; right: 2vh; max-width: 50vw">
      <!--suppress TypeScriptValidateTypes -->
      <v-alert v-for="alert in alerts" key="alert" :closable="alert[3]" :title="alert[0]"
               :type="alert[2]"
               class="my-2">
        <pre v-text="alert[1]"/>
      </v-alert>
    </div>

    <v-navigation-drawer v-model="sidebarIsOpen">
      <v-form v-model="searchValid" class="pa-2" @submit.prevent="search">
        <v-combobox v-model="posTags"
                    v-model:search="posTagsSearch" :items="posTagsSuggestions" :rules="[v => v.length !== 0]" chips
                    clearable density="compact" label="Positive Tags" multiple outlined
                    @update:modelValue="search"/>
        <v-combobox v-model="negTags" v-model:search="negTagsSearch" @update:modelValue="search"
                    :items="negTagsSuggestions" chips clearable density="compact" label="Negative Tags"
                    multiple outlined/>
        <v-text-field v-model="titleCrib" label="Title Crib" outlined @update:modelValue="search"/>
        <v-select v-model="sorting" :items="['AccessTime', 'CreationTime', 'Title']" label="Sorting" outlined
                  @update:modelValue="search"/>
        <v-btn :disabled="!searchValid" color="primary" type="submit">Search</v-btn>
      </v-form>
      <v-divider :thickness="2" class="border-opacity-75"/>
      <v-list>
        <v-list-item
            v-for="r in searchResults"
            :key="r.id"
            @click="id = r.id"
        >
          <v-list-item-subtitle v-text="formatDate(r.accessed)"/>
          <v-list-item-title v-text="r.title"/>

          <v-chip-group>
            <v-chip
                v-for="t in r.tags"
                :key="t"
                small
                @click="posTags = [t]; negTags=[]; titleCrib=''; search()"
            >
              {{ t }}
            </v-chip>
          </v-chip-group>

        </v-list-item>
      </v-list>
      <v-spacer/>
      <div class="my-2 d-flex">
        <v-spacer/>
        <v-select v-model="pageLength" hide-details :items="[10, 25, 50, 100]" dense style="width: 5%;"
                  @change="getSearchResults"/>
        <v-spacer/>
      </div>
      <div class="my-2 d-flex align-center">
        <v-spacer/>

        <v-icon icon="fas fa-angle-left" size="x-large"
                :style="page == 0 ? {'filter': 'contrast(20%)', 'cursor':'default '} : {}"
                @click="page!== 0 ? (()=>{page--; getSearchResults()})() : undefined"/>
        <span class="mx-2">{{ page + 1 }}</span>
        <v-icon icon="fas fa-angle-right" size="x-large" @click="page++; getSearchResults()"/>
        <v-spacer/>
      </div>
    </v-navigation-drawer>

    <v-system-bar class="py-3" color="primary">

      <v-icon v-if="!sidebarIsOpen" class="mx-1" icon="fas fa-bars" @click="sidebarIsOpen = !sidebarIsOpen"/>

      <v-spacer/>

      <v-icon :icon="`fas fa-${fullscreen ? 'compress' : 'expand'}`" class="mx-1" @click="toggleFullscreen"
              v-visible="id != undefined"/>

      <i class="mx-2"/>

      <v-icon class="mx-1" icon="fas fa-file-circle-plus" @click="createDialog = true; createTab='create'"/>
      <v-icon class="mx-1" icon="fas fa-file-import" @click="createDialog = true; createTab='import'"/>

      <i class="mx-2"/>

      <v-icon class="mx-1" icon="fas fa-file-pen" @click="openEditor" v-visible="id !== undefined"/>
      <v-icon class="mx-1" icon="fas fa-rotate-right" @click="triggerMetaUpdate" v-visible="id !== undefined"/>
      <v-icon class="mx-1" icon="fas fa-file-export" @click="showRenderInExplorer" v-visible="id !== undefined"/>
      <v-icon class="mx-1" icon="fas fa-trash" @click="deleteSheet = true;" v-visible="id !== undefined"/>
    </v-system-bar>

    <v-main>
      <v-container fluid style="height: calc(100vh - 24px)">
        <div v-if="!fullscreen">
          <v-text-field v-model="workingTitle" variant="underlined"></v-text-field>
          <v-chip-group>
            <v-chip v-for="t in meta?.tags" :key="t" class="pr-1"
                    @click="posTags = [t]; negTags = []; titleCrib=''; search()">
              {{ t }}
              <v-icon class="ml-2" icon="fas fa-circle-xmark" @click="removeTag(t)"/>
            </v-chip>

            <v-chip class="pa-3" prepend-icon="fas fa-plus"
                    @click="addTagDialog = true">

              <v-text-field v-if="addTagDialog" v-model="newTag" autofocus class="my-2"
                            density="compact" single-line style="width: 200px"
                            variant="underlined" @blur="addTagDialog = false" @keydown.enter="addTag"/>
            </v-chip>
          </v-chip-group>
        </div>

        <v-container :style="{overflow: 'scroll', height: fullscreen ? '100%' : '89%'}" fluid>
          <pre v-if="rendered?.[1] === 'Plain'" v-text="plainContent"/>
          <object v-else-if="rendered?.[1] === 'Pdf'" ref="pdfViewer" :data="convertFileSrc(<string>rendered?.[0])"
                  class="w-100 h-100" type="application/pdf"/>
          <object v-else-if="rendered?.[1] === 'Html' " :data="convertFileSrc(<string>rendered?.[0])" type="text/html"/>
        </v-container>

        <v-bottom-sheet v-model="deleteSheet">
          <v-btn color="error" @click="deleteDocument">
            DELETE
          </v-btn>
        </v-bottom-sheet>

        <v-dialog v-model="createDialog" width="35vw">
          <v-card>
            <v-tabs
                v-model="createTab"
                bg-color="primary"
            >
              <v-tab value="create">Create new</v-tab>
              <v-tab value="import">Import PDF to XOPP</v-tab>
            </v-tabs>
            <v-form v-if="createTab === 'create'" v-model="createValid" class="pa-4">
              <v-text-field v-model="createData.title" :rules="[v => v.trim() !== '']" label="Title"
                            outlined></v-text-field>
              <v-combobox v-model="createData.tags" v-model:search="createData.tagSearch" :items="createSuggestTags"
                          :rules="[v => v.length !== 0]"
                          chips clearable density="compact" label="Tags" multiple
                          outlined></v-combobox>
              <v-select v-model="createData.docType" :items="['Plain', 'Markdown', 'LaTeX', 'XournalPP']"
                        label="Document type"
                        outlined/>
              <v-combobox v-model="createData.extension" :items="['xopp', 'tex', 'md', 'txt', 'html']"
                          label="Extension (without leading dot)"
                          outlined/>
              <tauri-file-input v-model="createData.file" btn-text="Choose File"/>
              <br>
              <v-select @update:model-value="n => n ? applyPreset(n): {}" :items="presets?.map(p => p.name)"/>
            </v-form>
            <v-form v-else-if="createTab === 'import'" v-model="createValid" class="pa-4">
              <v-text-field v-model="createData.title" :rules="[v => v.trim() !== '']" label="Title"
                            outlined></v-text-field>
              <v-combobox v-model="createData.tags" v-model:search="createData.tagSearch" :items="createSuggestTags"
                          :rules="[v => v.length !== 0]" chips
                          clearable label="Tags" multiple
                          outlined></v-combobox>
              <tauri-file-input v-model="createData.file" btn-text="Choose File"/>
            </v-form>
            <v-card-actions>
              <v-spacer/>
              <v-btn :disabled="!createValid || (createData.file === undefined && createTab === 'import')"
                     @click="createDocument">{{
                  createTab !== 'import' ? 'Create' : 'Import'
                }}
              </v-btn>
            </v-card-actions>
          </v-card>
        </v-dialog>
      </v-container>
    </v-main>
  </v-app>
</template>

<style scoped></style>

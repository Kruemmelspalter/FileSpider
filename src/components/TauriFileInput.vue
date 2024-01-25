<script lang="ts" setup>

import {PropType, ref, computed} from 'vue'
import {dialog} from "@tauri-apps/api";


const props = defineProps<{
  btnText?: string,
  modelValue?: { Path: string } | { Blob: [number] } | "None"
}>()
const dialogOpen = ref(false);

const emit = defineEmits(['update:model-value']);

async function openFileChooser() {
  if (dialogOpen.value) return;
  dialogOpen.value = true;
  const res = await dialog.open();
  dialogOpen.value = false;
  if (typeof res === 'string') {
    emit('update:model-value', {Path: res});
  } else {
    emit('update:model-value', "None");
  }
}

const displayString = computed(() => {

  if ((props.modelValue as any)?.Path !== undefined) {
    return (props.modelValue as {Path: string}).Path.split('/').pop();
  }
  if ((props.modelValue as any)?.Blob !== undefined) {
    return `blob (${(props.modelValue as {Blob:[number]})?.Blob.length} bytes)`;
  }
  return '';
})

</script>

<template>
  <div>
    <v-btn :disabled="dialogOpen" class="ma-1" @click="openFileChooser">
      {{ btnText }}
    </v-btn>
    <span v-if="modelValue" class="ma-1" v-text="displayString"/>
    <v-icon v-if="modelValue != 'None'" class="ma-1" icon="fas fa-xmark-circle" @click="$emit('update:model-value', 'None')"/>
  </div>
</template>

<style scoped>

</style>

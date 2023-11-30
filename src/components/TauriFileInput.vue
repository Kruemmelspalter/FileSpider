<script lang="ts" setup>

import {PropType, ref} from 'vue'
import {dialog} from "@tauri-apps/api";


defineProps({
  btnText: {
    type: String,
    default: 'Open File'
  },
  modelValue: {
    type: String as PropType<string | undefined>,
    default: undefined,
  }
})
const dialogOpen = ref(false);

const emit = defineEmits(['update:model-value']);

async function openFileChooser() {
  if (dialogOpen.value) return;
  dialogOpen.value = true;
  const res = await dialog.open();
  dialogOpen.value = false;
  if (typeof res === 'string') {
    emit('update:model-value', res);
  } else {
    emit('update:model-value', undefined);
  }
}

</script>

<template>
  <div>
    <v-btn :disabled="dialogOpen" class="ma-1" @click="openFileChooser">
      {{ btnText }}
    </v-btn>
    <span v-if="modelValue" class="ma-1" v-text="modelValue?.split('/')?.pop()"/>
    <v-icon v-if="modelValue" class="ma-1" icon="fas fa-xmark-circle" @click="$emit('update:model-value', undefined)"/>
  </div>
</template>

<style scoped>

</style>
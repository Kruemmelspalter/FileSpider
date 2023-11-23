import {createApp} from "vue";
import "./styles.css";
import App from "./App.vue";
import {library} from '@fortawesome/fontawesome-svg-core'
import {FontAwesomeIcon} from '@fortawesome/vue-fontawesome'
import {fas} from '@fortawesome/free-solid-svg-icons'
import {far} from '@fortawesome/free-regular-svg-icons'
import {aliases, fa} from 'vuetify/iconsets/fa-svg'

// Vuetify
import 'vuetify/styles'
import {createVuetify} from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'

const vuetify = createVuetify({
    components,
    directives,
    theme: {defaultTheme: 'dark'},
    icons: {
        defaultSet: 'fa',
        aliases,
        sets: {
            fa,
        },
    }
})

library.add(fas) // Include needed solid icons
library.add(far) // Include needed regular icons

createApp(App).component('font-awesome-icon', FontAwesomeIcon).use(vuetify).mount('#app')

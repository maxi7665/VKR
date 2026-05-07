import 'vuetify/styles'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'

export default createVuetify({
  components,
  directives,
  theme: {
    defaultTheme: 'lynceus',
    themes: {
      lynceus: {
        dark: false,
        colors: {
          primary: '#2e7d32',
          secondary: '#66bb6a',
          background: '#e8f5e9',
          surface: '#ffffff',
          info: '#4db6ac',
          success: '#81c784',
          warning: '#ffb300',
          error: '#d32f2f'
        }
      }
    }
  }
})

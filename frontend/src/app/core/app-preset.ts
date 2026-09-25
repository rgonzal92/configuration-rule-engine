import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

const SHADES = [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950];

/** Points every shade of a semantic color at the same shade of one of Aura's palettes. */
function scale(palette: string): Record<number, string> {
  return Object.fromEntries(SHADES.map((shade) => [shade, `{${palette}.${shade}}`]));
}

/**
 * Aura restyled as a flat, dense workbench: an ink-blue primary on warm stone surfaces, a 4px
 * radius, and cards drawn with a border instead of a shadow. Primary, muted text, message, and
 * outlined danger colors are darker in the light theme and lighter in the dark theme than Aura's,
 * so text passes the 4.5:1 contrast WCAG AA requires on either surface.
 */
export const AppPreset = definePreset(Aura, {
  primitive: {
    borderRadius: { md: '4px', lg: '4px', xl: '4px' },
  },
  semantic: {
    primary: {
      ...scale('blue'),
      color: 'light-dark({primary.800}, {primary.300})',
      contrastColor: 'light-dark(#ffffff, {surface.950})',
      hoverColor: 'light-dark({primary.900}, {primary.200})',
      activeColor: 'light-dark({primary.950}, {primary.100})',
    },
    surface: { 0: '#ffffff', ...scale('stone') },
    text: {
      mutedColor: 'light-dark({surface.600}, {surface.400})',
    },
  },
  components: {
    button: {
      outlined: {
        danger: { color: 'light-dark({red.700}, {red.400})' },
      },
    },
    card: {
      root: { shadow: 'none' },
      body: { padding: '1rem', gap: '0.75rem' },
    },
    message: {
      error: { color: 'light-dark({red.700}, {red.300})' },
      success: { color: 'light-dark({green.800}, {green.300})' },
    },
    chip: {
      root: { borderRadius: '{border.radius.sm}', paddingX: '0.375rem', paddingY: '0.125rem' },
    },
  },
});

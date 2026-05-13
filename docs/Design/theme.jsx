// theme.jsx — kawaii pastel tokens for ChibiClaw
// Soft, rounded, friendly. Light + dark modes share accent hues.
// State machine colors are the visual anchor of the whole product.

const CC_ACCENTS = {
  rose:    { hue: 350, name: 'Rose'    },
  peach:   { hue: 25,  name: 'Peach'   },
  mint:    { hue: 160, name: 'Mint'    },
  lilac:   { hue: 285, name: 'Lilac'   },
  sky:     { hue: 220, name: 'Sky'     },
};

// State machine — the soul of the app.
// IDLE = abu-abu (resting), PLANNING = sky (thinking),
// EXECUTING = mint (acting, pulses), ERROR = rose (alert),
// WAITING = peach (paused), COMPLETE = lilac (success glow)
const CC_STATES = {
  IDLE:      { label: 'Idle',       hue: 240, chroma: 0.012, lightness: 0.78, glyph: '·',  desc: 'Menunggu perintah' },
  PLANNING:  { label: 'Planning',   hue: 220, chroma: 0.09,  lightness: 0.78, glyph: '~',  desc: 'Menyusun rencana' },
  EXECUTING: { label: 'Executing',  hue: 160, chroma: 0.10,  lightness: 0.78, glyph: '▸',  desc: 'Sedang menjalankan' },
  WAITING:   { label: 'Waiting',    hue: 60,  chroma: 0.12,  lightness: 0.82, glyph: '?',  desc: 'Menunggu approval' },
  ERROR:     { label: 'Error',      hue: 15,  chroma: 0.12,  lightness: 0.74, glyph: '!',  desc: 'Ada masalah' },
  COMPLETE:  { label: 'Complete',   hue: 295, chroma: 0.09,  lightness: 0.80, glyph: '✓',  desc: 'Selesai' },
};

function ccStateColor(state, opts = {}) {
  const s = CC_STATES[state] || CC_STATES.IDLE;
  const l = opts.l ?? s.lightness;
  const c = opts.c ?? s.chroma;
  const a = opts.a;
  if (a !== undefined) return `oklch(${l} ${c} ${s.hue} / ${a})`;
  return `oklch(${l} ${c} ${s.hue})`;
}

// Theme builder — given { dark, accentHue }, returns CSS variable map
function ccTheme({ dark = false, accentHue = 350, density = 'cozy' } = {}) {
  // Subtly-toned: warm cream in light, deep aubergine in dark
  const bg       = dark ? 'oklch(0.18 0.012 320)' : 'oklch(0.985 0.006 80)';
  const surface  = dark ? 'oklch(0.23 0.014 320)' : 'oklch(1 0 0)';
  const surface2 = dark ? 'oklch(0.27 0.016 320)' : 'oklch(0.97 0.008 60)';
  const border   = dark ? 'oklch(0.32 0.02 320)'  : 'oklch(0.92 0.01 60)';
  const text     = dark ? 'oklch(0.96 0.008 80)'  : 'oklch(0.22 0.014 320)';
  const textDim  = dark ? 'oklch(0.72 0.012 320)' : 'oklch(0.52 0.014 320)';
  const textMuted= dark ? 'oklch(0.55 0.012 320)' : 'oklch(0.68 0.012 320)';

  const accent   = `oklch(${dark ? 0.78 : 0.72} 0.11 ${accentHue})`;
  const accentSoft = `oklch(${dark ? 0.32 : 0.94} ${dark ? 0.05 : 0.04} ${accentHue})`;
  const accentInk  = dark ? 'oklch(0.18 0.012 320)' : 'oklch(0.28 0.05 ' + accentHue + ')';

  const radius = density === 'roomy' ? 28 : density === 'tight' ? 14 : 22;

  return {
    bg, surface, surface2, border, text, textDim, textMuted,
    accent, accentSoft, accentInk,
    radius,
    radiusSm: Math.round(radius * 0.55),
    radiusLg: Math.round(radius * 1.4),
    // Soft shadows for cards, never harsh
    shadowSm: dark ? '0 1px 2px rgba(0,0,0,0.4)' : '0 1px 2px rgba(180,140,160,0.08), 0 0 0 1px rgba(0,0,0,0.02)',
    shadowMd: dark ? '0 8px 24px rgba(0,0,0,0.35)' : '0 4px 16px rgba(180,140,160,0.12), 0 1px 2px rgba(180,140,160,0.06)',
    shadowGlow: (state) => `0 0 0 4px ${ccStateColor(state, { a: 0.18 })}, 0 8px 32px ${ccStateColor(state, { a: 0.25 })}`,
    font: '"Plus Jakarta Sans", "Quicksand", system-ui, -apple-system, sans-serif',
    fontMono: '"JetBrains Mono", "Fira Code", ui-monospace, monospace',
  };
}

// Tiny utility — squircle-ish blob shape via border-radius
const CC_BLOB = '64% 36% 58% 42% / 52% 60% 40% 48%';
const CC_BLOB_2 = '40% 60% 55% 45% / 60% 50% 50% 40%';

// Sparkle — pure CSS, used sparingly as kawaii accent
function CCSparkle({ size = 12, color = 'currentColor', style = {} }) {
  return (
    <svg width={size} height={size} viewBox="0 0 12 12" style={style}>
      <path d="M6 0 L7 5 L12 6 L7 7 L6 12 L5 7 L0 6 L5 5 Z" fill={color} />
    </svg>
  );
}

// Kawaii placeholder for "where mascot would go" — soft blob, no face,
// labeled monospace caption. User said "tidak ada mascot", so we just use
// the blob shape as a visual anchor.
function CCBlob({ size = 80, hue = 350, style = {} }) {
  return (
    <div style={{
      width: size, height: size,
      borderRadius: CC_BLOB,
      background: `radial-gradient(circle at 30% 30%, oklch(0.92 0.05 ${hue}), oklch(0.78 0.10 ${hue}))`,
      boxShadow: `inset -4px -6px 12px oklch(0.65 0.10 ${hue} / 0.35), inset 4px 6px 12px oklch(0.98 0.02 ${hue} / 0.6)`,
      ...style,
    }} />
  );
}

Object.assign(window, { CC_ACCENTS, CC_STATES, ccStateColor, ccTheme, CC_BLOB, CC_BLOB_2, CCSparkle, CCBlob });

// frame.jsx — kawaii Android frame for ChibiClaw
// Custom; the M3 default is too teal-corporate. Soft rounded, themed.

function CCStatusBar({ theme, dark }) {
  const c = theme.text;
  return (
    <div style={{
      height: 36, display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '0 22px', position: 'relative', flexShrink: 0,
      fontFamily: theme.font,
    }}>
      <span style={{ fontSize: 13, fontWeight: 600, color: c, letterSpacing: 0.2 }}>9:41</span>
      <div style={{
        position: 'absolute', left: '50%', top: 8, transform: 'translateX(-50%)',
        width: 18, height: 18, borderRadius: 100, background: '#0a0a0a',
      }} />
      <div style={{ display: 'flex', alignItems: 'center', gap: 5, color: c }}>
        <svg width="13" height="11" viewBox="0 0 13 11" fill="currentColor"><path d="M6.5 10.5L0 4a9.4 9.4 0 0113 0L6.5 10.5z"/></svg>
        <svg width="14" height="10" viewBox="0 0 14 10" fill="currentColor"><rect x="0" y="6" width="3" height="4" rx="0.5"/><rect x="4" y="4" width="3" height="6" rx="0.5"/><rect x="8" y="2" width="3" height="8" rx="0.5"/><rect x="12" y="0" width="2" height="10" rx="0.5" opacity="0.4"/></svg>
        <svg width="20" height="10" viewBox="0 0 20 10" fill="none" stroke="currentColor" strokeWidth="1"><rect x="0.5" y="0.5" width="16" height="9" rx="2"/><rect x="2" y="2" width="9" height="6" rx="1" fill="currentColor"/><rect x="17" y="3" width="2" height="4" rx="0.5" fill="currentColor"/></svg>
      </div>
    </div>
  );
}

function CCNavBar({ theme }) {
  return (
    <div style={{ height: 22, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
      <div style={{ width: 110, height: 4, borderRadius: 2, background: theme.textDim, opacity: 0.5 }} />
    </div>
  );
}

// Phone frame — exposes a content area sized for 412×892ish
function CCPhone({ theme, dark, children, label, contentStyle = {} }) {
  return (
    <div style={{
      width: 380, height: 780,
      borderRadius: 44, padding: 6,
      background: dark ? 'oklch(0.12 0.012 320)' : 'oklch(0.32 0.014 320)',
      boxShadow: '0 1px 2px rgba(0,0,0,0.2), 0 24px 60px rgba(60,40,80,0.18)',
      boxSizing: 'border-box',
    }}>
      <div style={{
        width: '100%', height: '100%', borderRadius: 38, overflow: 'hidden',
        background: theme.bg, display: 'flex', flexDirection: 'column',
        boxSizing: 'border-box',
        color: theme.text, fontFamily: theme.font,
      }}>
        <CCStatusBar theme={theme} dark={dark} />
        <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column', ...contentStyle }}>
          {children}
        </div>
        <CCNavBar theme={theme} />
      </div>
    </div>
  );
}

// Pill button — a kawaii staple
function CCPill({ children, theme, variant = 'soft', size = 'md', icon, onClick, style = {} }) {
  const sizes = {
    sm: { padding: '6px 12px', fontSize: 12, height: 28 },
    md: { padding: '10px 18px', fontSize: 14, height: 40 },
    lg: { padding: '14px 22px', fontSize: 15, height: 50 },
  };
  const variants = {
    solid: { background: theme.accent, color: theme.dark ? theme.bg : 'white' },
    soft:  { background: theme.accentSoft, color: theme.accentInk },
    ghost: { background: 'transparent', color: theme.text, border: `1px solid ${theme.border}` },
  };
  return (
    <button onClick={onClick} style={{
      ...sizes[size], ...variants[variant],
      borderRadius: 999, border: variants[variant].border || 'none',
      fontFamily: theme.font, fontWeight: 600,
      cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 8,
      ...style,
    }}>
      {icon}
      {children}
    </button>
  );
}

// Card — soft white surface with rounded corners
function CCCard({ children, theme, padding = 18, style = {} }) {
  return (
    <div style={{
      background: theme.surface,
      borderRadius: theme.radius,
      padding,
      boxShadow: theme.shadowSm,
      ...style,
    }}>{children}</div>
  );
}

// State chip — small pill that shows a state with its color
function CCStateChip({ state, theme, size = 'sm' }) {
  const s = CC_STATES[state];
  const sizes = {
    xs: { padding: '2px 8px', fontSize: 10, dotSize: 6, gap: 5 },
    sm: { padding: '4px 10px', fontSize: 11, dotSize: 6, gap: 6 },
    md: { padding: '6px 14px', fontSize: 13, dotSize: 8, gap: 8 },
  };
  const sz = sizes[size];
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: sz.gap,
      padding: sz.padding, borderRadius: 999,
      background: ccStateColor(state, { a: 0.18 }),
      color: theme.text,
      fontSize: sz.fontSize, fontWeight: 600, letterSpacing: 0.3,
      textTransform: 'uppercase', fontFamily: theme.font,
    }}>
      <span style={{
        width: sz.dotSize, height: sz.dotSize, borderRadius: '50%',
        background: ccStateColor(state),
        boxShadow: state === 'EXECUTING' ? `0 0 8px ${ccStateColor(state)}` : 'none',
        animation: state === 'EXECUTING' ? 'cc-pulse 1.4s ease-in-out infinite' : 'none',
      }} />
      {s.label}
    </span>
  );
}

Object.assign(window, { CCStatusBar, CCNavBar, CCPhone, CCPill, CCCard, CCStateChip });

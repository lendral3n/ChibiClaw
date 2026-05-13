// dashboards.jsx — 6 dashboard variations exploring different state-machine
// visualizations & execution log treatments. Each is a self-contained <Phone>
// content; they all consume the same `t` (theme) + `state` props.

// ─────────────────────────────────────────────────────────────
// V1 — Soft Orb. Big squishy state blob, checklist log.
// ─────────────────────────────────────────────────────────────
function DashV1({ t, state, dark }) {
  const s = CC_STATES[state];
  const steps = [
    { n: 1, text: 'Mencari kontak Budi', done: true },
    { n: 2, text: 'Membuka WhatsApp', done: true },
    { n: 3, text: 'Mengetik pesan', active: state === 'EXECUTING' },
    { n: 4, text: 'Mengirim pesan' },
  ];
  return (
    <div style={{ flex: 1, padding: '12px 20px 16px', overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontSize: 13, color: t.textDim }}>Halo, Aulia</div>
          <div style={{ fontSize: 22, fontWeight: 700, letterSpacing: -0.4 }}>ChibiClaw</div>
        </div>
        <div style={{ width: 38, height: 38, borderRadius: 12, background: t.accentSoft, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <CCSparkle size={16} color={t.accentInk} />
        </div>
      </div>

      {/* HERO: state orb */}
      <div style={{
        background: t.surface, borderRadius: t.radiusLg, padding: '24px 18px',
        boxShadow: t.shadowSm, position: 'relative', overflow: 'hidden',
      }}>
        <div style={{
          position: 'absolute', inset: 0,
          background: `radial-gradient(circle at 50% 0%, ${ccStateColor(state, { a: 0.18 })}, transparent 70%)`,
          pointerEvents: 'none',
        }} />
        <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14 }}>
          <div style={{
            width: 96, height: 96, borderRadius: CC_BLOB,
            background: `radial-gradient(circle at 35% 30%, ${ccStateColor(state, { l: 0.92, c: 0.04 })}, ${ccStateColor(state)})`,
            boxShadow: `inset -6px -8px 20px ${ccStateColor(state, { l: 0.55, c: 0.10, a: 0.5 })}, 0 0 0 6px ${ccStateColor(state, { a: 0.12 })}, 0 12px 32px ${ccStateColor(state, { a: 0.35 })}`,
            animation: state === 'EXECUTING' ? 'cc-blob 4s ease-in-out infinite, cc-breathe 2s ease-in-out infinite' : 'cc-blob 8s ease-in-out infinite',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: 'white', fontSize: 32, fontWeight: 700,
          }}>{s.glyph}</div>
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 1.5, color: ccStateColor(state, { l: dark ? 0.78 : 0.5, c: 0.10 }), textTransform: 'uppercase' }}>{s.label}</div>
            <div style={{ fontSize: 14, color: t.textDim, marginTop: 2 }}>{s.desc}</div>
          </div>
        </div>
      </div>

      {/* Quick stats */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8 }}>
        {[
          { k: '12', v: 'Tasks', hue: 350 },
          { k: '94%', v: 'Success', hue: 160 },
          { k: 'E2B', v: 'Model', hue: 220 },
        ].map((x) => (
          <div key={x.v} style={{ background: t.surface, borderRadius: t.radius, padding: '12px 10px', textAlign: 'center', boxShadow: t.shadowSm }}>
            <div style={{ fontSize: 18, fontWeight: 700, color: `oklch(${dark ? 0.85 : 0.55} 0.10 ${x.hue})` }}>{x.k}</div>
            <div style={{ fontSize: 10, color: t.textDim, marginTop: 1, letterSpacing: 0.5, textTransform: 'uppercase', fontWeight: 600 }}>{x.v}</div>
          </div>
        ))}
      </div>

      {/* Execution log — checklist */}
      <div style={{ background: t.surface, borderRadius: t.radius, padding: 16, boxShadow: t.shadowSm }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
          <div style={{ fontSize: 13, fontWeight: 700 }}>Eksekusi</div>
          <div style={{ fontSize: 11, color: t.textDim, fontFamily: t.fontMono }}>3/4</div>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {steps.map((step) => {
            const stateColor = step.done ? ccStateColor('COMPLETE') : step.active ? ccStateColor('EXECUTING') : t.border;
            return (
              <div key={step.n} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <div style={{
                  width: 22, height: 22, borderRadius: '50%',
                  background: step.done || step.active ? ccStateColor(step.done ? 'COMPLETE' : 'EXECUTING', { a: 0.2 }) : 'transparent',
                  border: step.done || step.active ? 'none' : `1.5px dashed ${t.border}`,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  color: stateColor, fontSize: 11, fontWeight: 700,
                }}>
                  {step.done ? '✓' : step.active ? <span style={{ animation: 'cc-spin 1.6s linear infinite', display: 'inline-block' }}>◔</span> : step.n}
                </div>
                <div style={{ flex: 1, fontSize: 13, color: step.done ? t.textDim : t.text, textDecoration: step.done ? 'line-through' : 'none' }}>{step.text}</div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// V2 — Equalizer Wave. Animated horizontal bars; terminal log.
// ─────────────────────────────────────────────────────────────
function DashV2({ t, state, dark }) {
  const s = CC_STATES[state];
  const bars = 24;
  return (
    <div style={{ flex: 1, padding: '12px 20px 16px', overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ fontSize: 22, fontWeight: 700, letterSpacing: -0.4, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span>chibiclaw <span style={{ color: t.accent }}>·</span></span>
        <span style={{ fontSize: 11, fontFamily: t.fontMono, color: t.textDim, fontWeight: 500 }}>v0.4.2</span>
      </div>

      {/* HERO: equalizer */}
      <div style={{
        background: t.surface, borderRadius: t.radiusLg, padding: '20px 18px',
        boxShadow: t.shadowSm,
        background: `linear-gradient(135deg, ${ccStateColor(state, { a: 0.10 })}, ${t.surface} 60%)`,
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 10 }}>
          <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 1.5, color: ccStateColor(state, { l: dark ? 0.85 : 0.45, c: 0.10 }), textTransform: 'uppercase', fontFamily: t.fontMono }}>{s.label}</div>
          <div style={{ fontSize: 11, fontFamily: t.fontMono, color: t.textDim }}>00:04.2</div>
        </div>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 3, height: 56 }}>
          {Array.from({ length: bars }).map((_, i) => {
            const seed = (Math.sin(i * 1.7) + 1) / 2;
            const baseH = state === 'IDLE' ? 8 : state === 'EXECUTING' ? 14 + seed * 38 : 12 + seed * 22;
            return (
              <div key={i} style={{
                flex: 1, background: ccStateColor(state),
                height: baseH, borderRadius: 3,
                opacity: state === 'IDLE' ? 0.3 : 0.5 + seed * 0.5,
                animation: state === 'EXECUTING' ? `cc-bar 1.${(i * 7) % 9}s ease-in-out infinite alternate` : 'none',
                animationDelay: `${i * 0.04}s`,
              }} />
            );
          })}
        </div>
        <div style={{ marginTop: 10, fontSize: 13, color: t.textDim }}>{s.desc}…</div>
      </div>

      {/* Quick stats — minimal row */}
      <div style={{ display: 'flex', gap: 16, padding: '0 4px' }}>
        {[['12', 'tasks'], ['94%', 'sukses'], ['1.2s', 'avg']].map(([k, v]) => (
          <div key={v}>
            <div style={{ fontSize: 18, fontWeight: 700, fontFamily: t.fontMono }}>{k}</div>
            <div style={{ fontSize: 10, color: t.textDim, letterSpacing: 0.5, textTransform: 'uppercase' }}>{v}</div>
          </div>
        ))}
      </div>

      {/* Terminal log */}
      <div style={{
        background: dark ? 'oklch(0.13 0.012 320)' : 'oklch(0.20 0.014 320)',
        borderRadius: t.radius, padding: '14px 16px', flex: 1,
        fontFamily: t.fontMono, fontSize: 11, lineHeight: 1.6, color: 'oklch(0.85 0.01 80)',
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8, color: 'oklch(0.6 0.01 80)' }}>
          <span>~/chibiclaw/log</span><span>•••</span>
        </div>
        {[
          ['$', 'kirim pesan ke Budi: meeting jam 3'],
          ['[1/4]', '✓ kontak resolved → +62 812-3456-7890', 'COMPLETE'],
          ['[2/4]', '✓ whatsapp opened (380ms)', 'COMPLETE'],
          ['[3/4]', '▸ typing message…', 'EXECUTING'],
          ['[4/4]', '· awaiting send', 'IDLE'],
        ].map(([n, txt, st], i) => (
          <div key={i} style={{ display: 'flex', gap: 8 }}>
            <span style={{ color: st ? ccStateColor(st, { l: 0.78 }) : 'oklch(0.6 0.05 220)', minWidth: 36 }}>{n}</span>
            <span style={{ flex: 1 }}>{txt}</span>
          </div>
        ))}
        <div style={{ height: 12, width: 7, background: ccStateColor(state, { l: 0.78 }), display: 'inline-block', marginTop: 4, animation: 'cc-blink 1s steps(2) infinite' }} />
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// V3 — Progress Ring. Circular state ring with progress fill;
//     timeline log with connectors.
// ─────────────────────────────────────────────────────────────
function DashV3({ t, state, dark }) {
  const s = CC_STATES[state];
  const progress = state === 'IDLE' ? 0 : state === 'PLANNING' ? 0.15 : state === 'EXECUTING' ? 0.6 : state === 'COMPLETE' ? 1 : 0.45;
  const r = 56, c = 2 * Math.PI * r;
  const steps = [
    { text: 'Mencari kontak Budi', state: 'COMPLETE', time: '0.4s' },
    { text: 'Membuka WhatsApp', state: 'COMPLETE', time: '0.4s' },
    { text: 'Mengetik pesan', state: state === 'EXECUTING' ? 'EXECUTING' : 'IDLE', time: '…' },
    { text: 'Mengirim pesan', state: 'IDLE', time: '—' },
  ];
  return (
    <div style={{ flex: 1, padding: '12px 20px 16px', overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ fontSize: 22, fontWeight: 700, letterSpacing: -0.4 }}>Hari ini</div>
        <CCStateChip state={state} theme={t} size="sm" />
      </div>

      {/* HERO: ring */}
      <div style={{ background: t.surface, borderRadius: t.radiusLg, padding: 18, boxShadow: t.shadowSm, display: 'flex', alignItems: 'center', gap: 16 }}>
        <div style={{ position: 'relative', width: 130, height: 130, flexShrink: 0 }}>
          <svg width="130" height="130" viewBox="0 0 130 130" style={{ position: 'absolute' }}>
            <circle cx="65" cy="65" r={r} fill="none" stroke={t.surface2} strokeWidth="10" />
            <circle cx="65" cy="65" r={r} fill="none" stroke={ccStateColor(state)} strokeWidth="10"
              strokeLinecap="round" strokeDasharray={c} strokeDashoffset={c * (1 - progress)}
              transform="rotate(-90 65 65)"
              style={{ transition: 'stroke-dashoffset 0.6s ease' }} />
          </svg>
          <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
            <div style={{ fontSize: 28, fontWeight: 700, fontFamily: t.fontMono, color: ccStateColor(state, { l: dark ? 0.85 : 0.45 }) }}>{Math.round(progress * 100)}<span style={{ fontSize: 14 }}>%</span></div>
            <div style={{ fontSize: 10, color: t.textDim, letterSpacing: 1, textTransform: 'uppercase', fontWeight: 600 }}>step 3/4</div>
          </div>
        </div>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 6 }}>
          <div style={{ fontSize: 11, color: t.textDim, fontWeight: 600, textTransform: 'uppercase', letterSpacing: 1 }}>Task aktif</div>
          <div style={{ fontSize: 15, fontWeight: 600, lineHeight: 1.3 }}>Kirim pesan ke Budi: meeting jam 3</div>
          <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
            <CCPill theme={t} size="sm" variant="solid">Stop</CCPill>
            <CCPill theme={t} size="sm" variant="ghost">Detail</CCPill>
          </div>
        </div>
      </div>

      {/* Timeline log */}
      <div style={{ background: t.surface, borderRadius: t.radius, padding: 16, boxShadow: t.shadowSm }}>
        <div style={{ fontSize: 11, color: t.textDim, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 1, marginBottom: 14 }}>Timeline</div>
        <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', gap: 14 }}>
          {steps.map((step, i) => (
            <div key={i} style={{ display: 'flex', gap: 12, alignItems: 'flex-start', position: 'relative' }}>
              <div style={{ position: 'relative', width: 22, flexShrink: 0 }}>
                <div style={{
                  width: 22, height: 22, borderRadius: '50%',
                  background: ccStateColor(step.state, { a: step.state === 'IDLE' ? 0 : 0.25 }),
                  border: step.state === 'IDLE' ? `2px solid ${t.border}` : 'none',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  color: ccStateColor(step.state, { l: dark ? 0.85 : 0.45 }),
                  fontSize: 11, fontWeight: 700,
                  boxShadow: step.state === 'EXECUTING' ? `0 0 0 4px ${ccStateColor(step.state, { a: 0.15 })}` : 'none',
                }}>{step.state === 'COMPLETE' ? '✓' : step.state === 'EXECUTING' ? <span style={{ animation: 'cc-spin 1.6s linear infinite', display: 'inline-block' }}>◔</span> : ''}</div>
                {i < steps.length - 1 && <div style={{ position: 'absolute', left: 10, top: 24, bottom: -16, width: 2, background: step.state === 'COMPLETE' ? ccStateColor('COMPLETE', { a: 0.4 }) : t.border, borderRadius: 1 }} />}
              </div>
              <div style={{ flex: 1, paddingTop: 1 }}>
                <div style={{ fontSize: 13, fontWeight: 500, color: step.state === 'IDLE' ? t.textDim : t.text }}>{step.text}</div>
                <div style={{ fontSize: 10, color: t.textMuted, fontFamily: t.fontMono, marginTop: 1 }}>{step.time}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// V4 — Pixel Heart. CRT/retro aesthetic; chat-bubble log.
// ─────────────────────────────────────────────────────────────
function DashV4({ t, state, dark }) {
  const s = CC_STATES[state];
  // 7×7 pixel state glyph, hand-coded grid
  const heart = [
    [0,1,1,0,1,1,0],
    [1,1,1,1,1,1,1],
    [1,1,1,1,1,1,1],
    [1,1,1,1,1,1,1],
    [0,1,1,1,1,1,0],
    [0,0,1,1,1,0,0],
    [0,0,0,1,0,0,0],
  ];
  return (
    <div style={{ flex: 1, padding: '12px 20px 16px', overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontFamily: t.fontMono }}>
        <div style={{ fontSize: 18, fontWeight: 700, letterSpacing: 1 }}>CHIBI<span style={{ color: t.accent }}>::</span>CLAW</div>
        <div style={{ fontSize: 10, color: t.textDim, letterSpacing: 1 }}>[ONLINE]</div>
      </div>

      {/* HERO: pixel heart in CRT box */}
      <div style={{
        background: dark ? 'oklch(0.10 0.012 320)' : 'oklch(0.18 0.014 320)',
        borderRadius: t.radius,
        padding: '24px 18px',
        position: 'relative', overflow: 'hidden',
        boxShadow: `inset 0 0 60px ${ccStateColor(state, { a: 0.3 })}`,
      }}>
        {/* scanlines */}
        <div style={{ position: 'absolute', inset: 0, background: 'repeating-linear-gradient(0deg, transparent 0, transparent 2px, rgba(0,0,0,0.18) 3px)', pointerEvents: 'none' }} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 18, position: 'relative' }}>
          {/* pixel heart, color = state */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 8px)', gap: 1 }}>
            {heart.flat().map((on, i) => (
              <div key={i} style={{
                width: 8, height: 8,
                background: on ? ccStateColor(state, { l: 0.78, c: 0.14 }) : 'transparent',
                boxShadow: on ? `0 0 4px ${ccStateColor(state, { l: 0.78, c: 0.14 })}` : 'none',
                animation: on && state === 'EXECUTING' ? 'cc-flicker 0.8s steps(2) infinite' : 'none',
              }} />
            ))}
          </div>
          <div style={{ fontFamily: t.fontMono, color: 'oklch(0.92 0.01 80)' }}>
            <div style={{ fontSize: 10, color: ccStateColor(state, { l: 0.78, c: 0.14 }), letterSpacing: 2, fontWeight: 700 }}>&gt; {s.label.toUpperCase()}</div>
            <div style={{ fontSize: 12, opacity: 0.8, marginTop: 4 }}>{s.desc}</div>
            <div style={{ fontSize: 10, opacity: 0.5, marginTop: 8 }}>cycles: 0x{((Math.random() * 0xfff) | 0).toString(16).padStart(3, '0')}</div>
          </div>
        </div>
      </div>

      {/* Chat-bubble log */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 8, padding: '4px 0' }}>
        <div style={{ fontSize: 10, color: t.textDim, fontWeight: 700, fontFamily: t.fontMono, letterSpacing: 1, padding: '0 4px' }}># EXEC LOG</div>
        {[
          { side: 'l', text: 'kirim pesan ke Budi', state: null, who: 'kamu' },
          { side: 'r', text: '✓ kontak ditemukan', state: 'COMPLETE' },
          { side: 'r', text: '✓ wa terbuka', state: 'COMPLETE' },
          { side: 'r', text: '▸ mengetik pesan…', state: 'EXECUTING' },
        ].map((m, i) => (
          <div key={i} style={{ display: 'flex', justifyContent: m.side === 'l' ? 'flex-start' : 'flex-end' }}>
            <div style={{
              maxWidth: '78%', padding: '8px 13px', borderRadius: 16,
              borderBottomRightRadius: m.side === 'r' ? 4 : 16,
              borderBottomLeftRadius: m.side === 'l' ? 4 : 16,
              background: m.side === 'l' ? t.surface : (m.state ? ccStateColor(m.state, { a: 0.2 }) : t.accentSoft),
              color: m.side === 'l' ? t.text : (m.state ? ccStateColor(m.state, { l: dark ? 0.85 : 0.4 }) : t.accentInk),
              fontSize: 13, fontFamily: t.fontMono,
              boxShadow: t.shadowSm,
            }}>{m.text}</div>
          </div>
        ))}
      </div>

      {/* stats footer */}
      <div style={{ display: 'flex', gap: 12, fontFamily: t.fontMono, fontSize: 10, color: t.textDim, justifyContent: 'space-between' }}>
        <span>tasks:12</span><span>ok:94%</span><span>mem:48%</span><span>e2b</span>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// V5 — State Constellation. Big floating petals representing each state;
//     active state glows, others dim. Cards-stack log.
// ─────────────────────────────────────────────────────────────
function DashV5({ t, state, dark }) {
  const orbits = ['IDLE', 'PLANNING', 'EXECUTING', 'WAITING', 'ERROR'];
  const N = orbits.length;
  return (
    <div style={{ flex: 1, padding: '12px 20px 16px', overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div>
        <div style={{ fontSize: 13, color: t.textDim }}>Selamat siang</div>
        <div style={{ fontSize: 22, fontWeight: 700, letterSpacing: -0.4 }}>Apa yang bisa dibantu?</div>
      </div>

      {/* HERO: constellation */}
      <div style={{
        background: t.surface, borderRadius: t.radiusLg,
        height: 220, position: 'relative', overflow: 'hidden',
        boxShadow: t.shadowSm,
      }}>
        {/* faint orbit ring */}
        <svg viewBox="0 0 320 220" style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }}>
          <ellipse cx="160" cy="115" rx="120" ry="68" fill="none" stroke={t.border} strokeWidth="1" strokeDasharray="2 4" />
        </svg>
        {orbits.map((st, i) => {
          const angle = (i / N) * Math.PI * 2 - Math.PI / 2;
          const cx = 160 + Math.cos(angle) * 120;
          const cy = 115 + Math.sin(angle) * 68;
          const active = st === state;
          return (
            <div key={st} style={{
              position: 'absolute', left: cx, top: cy, transform: 'translate(-50%, -50%)',
              transition: 'all 0.5s ease',
            }}>
              <div style={{
                width: active ? 56 : 30, height: active ? 56 : 30,
                borderRadius: CC_BLOB_2,
                background: active
                  ? `radial-gradient(circle at 30% 30%, ${ccStateColor(st, { l: 0.92, c: 0.04 })}, ${ccStateColor(st)})`
                  : ccStateColor(st, { a: 0.25 }),
                boxShadow: active ? `0 0 0 6px ${ccStateColor(st, { a: 0.18 })}, 0 8px 24px ${ccStateColor(st, { a: 0.4 })}` : 'none',
                animation: active ? 'cc-blob 6s ease-in-out infinite, cc-breathe 2s ease-in-out infinite' : 'none',
              }} />
              <div style={{
                fontSize: 9, fontWeight: 700, letterSpacing: 1.2,
                color: active ? t.text : t.textMuted,
                textAlign: 'center', marginTop: 4,
                opacity: active ? 1 : 0.6,
              }}>{CC_STATES[st].label.toUpperCase()}</div>
            </div>
          );
        })}
        <div style={{
          position: 'absolute', left: '50%', top: '50%', transform: 'translate(-50%, -50%)',
          textAlign: 'center', pointerEvents: 'none',
        }}>
          <div style={{ fontSize: 11, color: t.textDim, fontWeight: 600, letterSpacing: 1 }}>STATE</div>
          <div style={{ fontSize: 16, fontWeight: 700, color: ccStateColor(state, { l: dark ? 0.85 : 0.4, c: 0.08 }) }}>{CC_STATES[state].label}</div>
        </div>
      </div>

      {/* Recent tasks — card stack */}
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
          <div style={{ fontSize: 13, fontWeight: 700 }}>Tugas terakhir</div>
          <div style={{ fontSize: 12, color: t.accent, fontWeight: 600 }}>Lihat semua</div>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {[
            { t: 'Kirim pesan ke Budi', state: 'EXECUTING', time: 'sekarang', sub: '3 dari 4 langkah' },
            { t: 'Setel alarm 06:30', state: 'COMPLETE', time: '5m', sub: 'berhasil' },
            { t: 'Cari tiket KAI Jkt-Bdg', state: 'WAITING', time: '12m', sub: 'butuh approval' },
            { t: 'Kirim email ke tim', state: 'COMPLETE', time: '1h', sub: 'berhasil' },
          ].map((task, i) => (
            <div key={i} style={{ background: t.surface, borderRadius: t.radius, padding: 12, boxShadow: t.shadowSm, display: 'flex', alignItems: 'center', gap: 12 }}>
              <div style={{ width: 8, height: 32, borderRadius: 4, background: ccStateColor(task.state) }} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13, fontWeight: 600, whiteSpace: 'nowrap', textOverflow: 'ellipsis', overflow: 'hidden' }}>{task.t}</div>
                <div style={{ fontSize: 11, color: t.textDim, marginTop: 1 }}>{task.sub}</div>
              </div>
              <div style={{ fontSize: 10, color: t.textMuted, fontFamily: t.fontMono }}>{task.time}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// V6 — Voice-First. Big mic affordance + waveform; minimal status.
// ─────────────────────────────────────────────────────────────
function DashV6({ t, state, dark }) {
  const s = CC_STATES[state];
  return (
    <div style={{ flex: 1, padding: '12px 20px 16px', overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <CCStateChip state={state} theme={t} size="sm" />
        <div style={{ display: 'flex', gap: 6 }}>
          <div style={{ width: 32, height: 32, borderRadius: 12, background: t.surface, boxShadow: t.shadowSm, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14 }}>⚙</div>
        </div>
      </div>

      {/* HERO: voice mic */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 14, padding: '20px 0' }}>
        <div style={{ fontSize: 13, color: t.textDim, textAlign: 'center', maxWidth: 240 }}>{s.desc}</div>
        <div style={{ position: 'relative', width: 180, height: 180, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {/* concentric breathing rings */}
          {[1, 2, 3].map((r) => (
            <div key={r} style={{
              position: 'absolute',
              width: 80 + r * 32, height: 80 + r * 32,
              borderRadius: '50%',
              background: ccStateColor(state, { a: 0.06 + r * 0.02 }),
              animation: state === 'EXECUTING' ? `cc-ring 2.${r}s ease-in-out infinite` : 'none',
              animationDelay: `${r * 0.2}s`,
            }} />
          ))}
          <div style={{
            width: 100, height: 100, borderRadius: '50%',
            background: `radial-gradient(circle at 30% 30%, ${ccStateColor(state, { l: 0.92, c: 0.04 })}, ${ccStateColor(state)})`,
            boxShadow: `0 0 0 8px ${ccStateColor(state, { a: 0.15 })}, 0 16px 40px ${ccStateColor(state, { a: 0.4 })}`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            cursor: 'pointer',
            position: 'relative', zIndex: 2,
            animation: state === 'EXECUTING' ? 'cc-breathe 1.6s ease-in-out infinite' : 'none',
          }}>
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <rect x="9" y="3" width="6" height="12" rx="3" />
              <path d="M5 11a7 7 0 0014 0" />
              <path d="M12 18v3" />
            </svg>
          </div>
        </div>
        <div style={{ fontSize: 22, fontWeight: 700, letterSpacing: -0.4, textAlign: 'center', color: t.text }}>"{state === 'IDLE' ? 'Tap atau bilang Hai Fuu' : state === 'EXECUTING' ? 'Mengirim pesan ke Budi…' : 'Mendengarkan…'}"</div>
        {/* mini wave */}
        <div style={{ display: 'flex', gap: 3, alignItems: 'center', height: 24 }}>
          {Array.from({ length: 14 }).map((_, i) => {
            const seed = (Math.sin(i * 2.1) + 1) / 2;
            return (
              <div key={i} style={{
                width: 3, borderRadius: 2,
                background: ccStateColor(state, { l: dark ? 0.82 : 0.6, c: 0.08 }),
                height: 4 + seed * 18,
                opacity: state === 'IDLE' ? 0.3 : 0.7,
                animation: state === 'EXECUTING' ? `cc-bar 0.8s ease-in-out infinite alternate` : 'none',
                animationDelay: `${i * 0.06}s`,
              }} />
            );
          })}
        </div>
      </div>

      {/* tiny stats strip */}
      <div style={{
        background: t.surface, borderRadius: 999,
        padding: '10px 16px', boxShadow: t.shadowSm,
        display: 'flex', justifyContent: 'space-around', alignItems: 'center', fontSize: 12,
      }}>
        <span><b style={{ fontWeight: 700 }}>12</b> <span style={{ color: t.textDim }}>tasks</span></span>
        <span style={{ color: t.border }}>·</span>
        <span><b style={{ fontWeight: 700 }}>94%</b> <span style={{ color: t.textDim }}>sukses</span></span>
        <span style={{ color: t.border }}>·</span>
        <span><b style={{ fontWeight: 700 }}>E2B</b></span>
      </div>
    </div>
  );
}

Object.assign(window, { DashV1, DashV2, DashV3, DashV4, DashV5, DashV6 });

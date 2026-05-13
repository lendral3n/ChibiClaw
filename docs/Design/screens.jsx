// screens.jsx — supporting screens: Setup wizard, AI settings, Safety,
// Skills, Persona, Chat/Voice, Floating Overlay (mini + expanded).

// ─────────────────────────────────────────────────────────────
// SETUP WIZARD — step 3 of 8 shown (Test AI)
// ─────────────────────────────────────────────────────────────
function ScreenSetup({ t, dark }) {
  const steps = [
    { i: 1, label: 'Welcome', done: true },
    { i: 2, label: 'Download Model', done: true },
    { i: 3, label: 'Test AI', active: true },
    { i: 4, label: 'Accessibility', },
    { i: 5, label: 'Notifications' },
    { i: 6, label: 'Shizuku' },
    { i: 7, label: 'Whitelist' },
    { i: 8, label: 'Done' },
  ];
  return (
    <div style={{ flex: 1, padding: '12px 22px 20px', overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
      {/* progress dots */}
      <div style={{ display: 'flex', gap: 5, padding: '4px 0 18px' }}>
        {steps.map((s) => (
          <div key={s.i} style={{
            flex: 1, height: 4, borderRadius: 2,
            background: s.done ? ccStateColor('COMPLETE') : s.active ? t.accent : t.border,
          }} />
        ))}
      </div>

      <div style={{ fontSize: 11, color: t.textDim, fontWeight: 700, letterSpacing: 1.5, textTransform: 'uppercase' }}>Langkah 3 dari 8</div>
      <div style={{ fontSize: 26, fontWeight: 700, letterSpacing: -0.5, marginTop: 4, lineHeight: 1.15 }}>Mari coba dulu otaknya</div>
      <div style={{ fontSize: 14, color: t.textDim, marginTop: 6, lineHeight: 1.5 }}>Kita kirim prompt singkat ke Gemma untuk pastikan model jalan di HP-mu.</div>

      {/* Test card */}
      <div style={{ background: t.surface, borderRadius: t.radiusLg, padding: 18, boxShadow: t.shadowSm, marginTop: 16 }}>
        <div style={{ fontSize: 11, fontWeight: 700, color: t.textDim, letterSpacing: 1, textTransform: 'uppercase', marginBottom: 10 }}>Prompt</div>
        <div style={{
          background: t.surface2, borderRadius: t.radiusSm, padding: '10px 12px',
          fontFamily: t.fontMono, fontSize: 12, color: t.text, lineHeight: 1.5,
        }}>"Halo Fuu, sebut nama kamu dan satu hal yang kamu suka."</div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 14 }}>
          <CCStateChip state="EXECUTING" theme={t} size="sm" />
          <div style={{ fontSize: 12, color: t.textDim, fontFamily: t.fontMono }}>0.4s · 12 tok/s</div>
        </div>

        <div style={{
          marginTop: 12, padding: '12px 14px',
          background: ccStateColor('EXECUTING', { a: 0.10 }),
          borderRadius: t.radiusSm,
          fontSize: 13, lineHeight: 1.5, color: t.text,
          border: `1px solid ${ccStateColor('EXECUTING', { a: 0.25 })}`,
        }}>
          "Halo! Aku Fuu. Aku suka membantu kamu menyelesaikan tugas-tugas kecil yang bikin hari jadi lebih lega ✿"
          <span style={{ display: 'inline-block', width: 6, height: 12, background: ccStateColor('EXECUTING'), marginLeft: 4, animation: 'cc-blink 1s steps(2) infinite', verticalAlign: 'middle' }} />
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 14, fontSize: 11, color: t.textMuted, fontFamily: t.fontMono }}>
          <span>gemma-2b-it · q4_0</span>
          <span>1.4 GB</span>
        </div>
      </div>

      <div style={{ flex: 1 }} />

      <div style={{ display: 'flex', gap: 10, marginTop: 18 }}>
        <CCPill theme={t} variant="ghost" size="lg" style={{ flex: 1, justifyContent: 'center' }}>Kembali</CCPill>
        <CCPill theme={t} variant="solid" size="lg" style={{ flex: 2, justifyContent: 'center' }}>Lanjut →</CCPill>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// AI SETTINGS
// ─────────────────────────────────────────────────────────────
function ScreenAISettings({ t, dark }) {
  return (
    <div style={{ flex: 1, padding: '8px 20px 16px', overflow: 'auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '4px 0 16px' }}>
        <div style={{ width: 32, height: 32, borderRadius: 10, background: t.surface, boxShadow: t.shadowSm, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16 }}>‹</div>
        <div style={{ fontSize: 17, fontWeight: 700 }}>AI Settings</div>
      </div>

      {/* Model picker */}
      <div style={{ fontSize: 11, color: t.textDim, fontWeight: 700, letterSpacing: 1, textTransform: 'uppercase', marginBottom: 8 }}>Pilih Model</div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 18 }}>
        {[
          { k: 'E2B', desc: 'Cepat · 1.4 GB · 12 tok/s', tag: 'Fast', selected: true },
          { k: 'E4B', desc: 'Pintar · 2.5 GB · 6 tok/s', tag: 'Smart' },
          { k: 'AUTO', desc: 'Pilih otomatis sesuai task', tag: 'Recommended' },
        ].map((m) => (
          <div key={m.k} style={{
            background: t.surface, borderRadius: t.radius, padding: 14,
            boxShadow: t.shadowSm,
            border: m.selected ? `2px solid ${t.accent}` : `2px solid transparent`,
            display: 'flex', alignItems: 'center', gap: 12,
          }}>
            <div style={{
              width: 44, height: 44, borderRadius: 14,
              background: m.selected ? t.accentSoft : t.surface2,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 13, fontFamily: t.fontMono, fontWeight: 700,
              color: m.selected ? t.accentInk : t.textDim,
            }}>{m.k}</div>
            <div style={{ flex: 1 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <div style={{ fontSize: 14, fontWeight: 600 }}>{m.tag}</div>
                {m.selected && <span style={{ fontSize: 9, padding: '2px 6px', borderRadius: 6, background: ccStateColor('COMPLETE', { a: 0.2 }), color: ccStateColor('COMPLETE', { l: dark ? 0.85 : 0.4 }), fontWeight: 700, letterSpacing: 0.5 }}>AKTIF</span>}
              </div>
              <div style={{ fontSize: 11, color: t.textDim, marginTop: 1, fontFamily: t.fontMono }}>{m.desc}</div>
            </div>
            <div style={{
              width: 22, height: 22, borderRadius: '50%',
              border: `2px solid ${m.selected ? t.accent : t.border}`,
              background: m.selected ? t.accent : 'transparent',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              color: 'white', fontSize: 12,
            }}>{m.selected ? '✓' : ''}</div>
          </div>
        ))}
      </div>

      {/* Performance slider */}
      <div style={{ fontSize: 11, color: t.textDim, fontWeight: 700, letterSpacing: 1, textTransform: 'uppercase', marginBottom: 8 }}>Performa</div>
      <div style={{ background: t.surface, borderRadius: t.radius, padding: 16, boxShadow: t.shadowSm, marginBottom: 18 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, fontWeight: 600, marginBottom: 10 }}>
          <span>Kecepatan</span>
          <span style={{ color: t.textDim }}>Kualitas</span>
        </div>
        <div style={{ height: 8, background: t.surface2, borderRadius: 4, position: 'relative' }}>
          <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: '60%', background: `linear-gradient(90deg, ${ccStateColor('PLANNING')}, ${ccStateColor('EXECUTING')})`, borderRadius: 4 }} />
          <div style={{ position: 'absolute', left: '60%', top: '50%', transform: 'translate(-50%, -50%)', width: 22, height: 22, background: 'white', borderRadius: '50%', boxShadow: '0 2px 8px rgba(0,0,0,0.2), 0 0 0 4px ' + ccStateColor('EXECUTING', { a: 0.2 }) }} />
        </div>
        <div style={{ fontSize: 11, color: t.textDim, marginTop: 12, fontFamily: t.fontMono }}>q4_0 → q4_K_M · medium quant</div>
      </div>

      {/* Context window */}
      <div style={{ fontSize: 11, color: t.textDim, fontWeight: 700, letterSpacing: 1, textTransform: 'uppercase', marginBottom: 8 }}>Konteks</div>
      <div style={{ background: t.surface, borderRadius: t.radius, padding: 16, boxShadow: t.shadowSm }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 }}>
          <span style={{ fontSize: 13, fontWeight: 600 }}>Token usage</span>
          <span style={{ fontSize: 12, color: t.textDim, fontFamily: t.fontMono }}>2,148 / 4,096</span>
        </div>
        <div style={{ display: 'flex', gap: 2, height: 22, borderRadius: 6, overflow: 'hidden', background: t.surface2 }}>
          <div style={{ flex: '0 0 30%', background: ccStateColor('PLANNING', { a: 0.6 }) }} />
          <div style={{ flex: '0 0 22%', background: ccStateColor('EXECUTING', { a: 0.6 }) }} />
          <div style={{ flex: '0 0 0.5%', background: 'transparent' }} />
        </div>
        <div style={{ display: 'flex', gap: 12, fontSize: 10, color: t.textDim, marginTop: 8 }}>
          <span>● system</span><span>● task</span><span style={{ marginLeft: 'auto' }}>52% used</span>
        </div>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// SAFETY SETTINGS
// ─────────────────────────────────────────────────────────────
function ScreenSafety({ t, dark }) {
  const apps = [
    { name: 'WhatsApp', sub: 'Default · ask once', on: true },
    { name: 'Gmail', sub: 'Default · ask once', on: true },
    { name: 'Instagram', sub: 'Locked · always ask', on: true },
    { name: 'Banking apps', sub: 'Blocked', on: false },
    { name: 'Phone Dialer', sub: 'Default', on: true },
  ];
  return (
    <div style={{ flex: 1, padding: '8px 20px 16px', overflow: 'auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '4px 0 16px' }}>
        <div style={{ width: 32, height: 32, borderRadius: 10, background: t.surface, boxShadow: t.shadowSm, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16 }}>‹</div>
        <div style={{ fontSize: 17, fontWeight: 700 }}>Keamanan</div>
      </div>

      {/* Approval banner */}
      <div style={{
        background: ccStateColor('WAITING', { a: 0.15 }), borderRadius: t.radius, padding: 14,
        border: `1px solid ${ccStateColor('WAITING', { a: 0.3 })}`, marginBottom: 16,
        display: 'flex', alignItems: 'center', gap: 12,
      }}>
        <div style={{ width: 36, height: 36, borderRadius: 12, background: ccStateColor('WAITING', { a: 0.3 }), display: 'flex', alignItems: 'center', justifyContent: 'center', color: ccStateColor('WAITING', { l: dark ? 0.85 : 0.35 }), fontWeight: 700, fontSize: 18 }}>!</div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 13, fontWeight: 600 }}>Mode aman aktif</div>
          <div style={{ fontSize: 11, color: t.textDim, marginTop: 2 }}>Tindakan sensitif selalu konfirmasi dulu</div>
        </div>
      </div>

      <div style={{ fontSize: 11, color: t.textDim, fontWeight: 700, letterSpacing: 1, textTransform: 'uppercase', marginBottom: 8 }}>Whitelist Aplikasi</div>
      <div style={{ background: t.surface, borderRadius: t.radius, boxShadow: t.shadowSm, marginBottom: 16, overflow: 'hidden' }}>
        {apps.map((app, i) => (
          <div key={app.name} style={{
            display: 'flex', alignItems: 'center', gap: 12, padding: '13px 14px',
            borderTop: i ? `1px solid ${t.border}` : 'none',
          }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: t.surface2, fontSize: 11, fontWeight: 700, color: t.textDim, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>{app.name.slice(0, 2)}</div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 13, fontWeight: 600 }}>{app.name}</div>
              <div style={{ fontSize: 11, color: t.textDim }}>{app.sub}</div>
            </div>
            <div style={{
              width: 40, height: 24, borderRadius: 12,
              background: app.on ? t.accent : t.border,
              padding: 2, display: 'flex', alignItems: 'center',
              justifyContent: app.on ? 'flex-end' : 'flex-start',
              transition: 'all 0.2s',
            }}>
              <div style={{ width: 20, height: 20, borderRadius: '50%', background: 'white', boxShadow: '0 1px 3px rgba(0,0,0,0.2)' }} />
            </div>
          </div>
        ))}
      </div>

      <div style={{ fontSize: 11, color: t.textDim, fontWeight: 700, letterSpacing: 1, textTransform: 'uppercase', marginBottom: 8 }}>Approval Policy</div>
      <div style={{ background: t.surface, borderRadius: t.radius, padding: 16, boxShadow: t.shadowSm }}>
        <div style={{ display: 'flex', gap: 6, padding: 4, borderRadius: 999, background: t.surface2 }}>
          {['Auto', 'Ask sensitive', 'Always ask'].map((opt, i) => (
            <div key={opt} style={{
              flex: 1, padding: '8px 4px', borderRadius: 999, textAlign: 'center',
              fontSize: 12, fontWeight: 600,
              background: i === 1 ? t.surface : 'transparent',
              color: i === 1 ? t.text : t.textDim,
              boxShadow: i === 1 ? t.shadowSm : 'none',
            }}>{opt}</div>
          ))}
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: 14, marginTop: 12, borderTop: `1px solid ${t.border}` }}>
          <div>
            <div style={{ fontSize: 13, fontWeight: 600 }}>Auto-detect data sensitif</div>
            <div style={{ fontSize: 11, color: t.textDim }}>OTP, nomor kartu, password</div>
          </div>
          <div style={{ width: 40, height: 24, borderRadius: 12, background: t.accent, padding: 2, display: 'flex', justifyContent: 'flex-end' }}>
            <div style={{ width: 20, height: 20, borderRadius: '50%', background: 'white', boxShadow: '0 1px 3px rgba(0,0,0,0.2)' }} />
          </div>
        </div>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// SKILL EDITOR
// ─────────────────────────────────────────────────────────────
function ScreenSkills({ t, dark }) {
  const skills = [
    { name: 'send_whatsapp', cat: 'Built-in', sev: 'low', on: true },
    { name: 'set_alarm', cat: 'Built-in', sev: 'low', on: true },
    { name: 'send_email', cat: 'Built-in', sev: 'med', on: true },
    { name: 'pay_qris', cat: 'Built-in', sev: 'high', on: false },
    { name: 'order_gojek', cat: 'Custom', sev: 'med', on: true },
    { name: 'morning_brief', cat: 'Custom', sev: 'low', on: true },
  ];
  const sevColor = (s) => s === 'high' ? 'ERROR' : s === 'med' ? 'WAITING' : 'COMPLETE';
  return (
    <div style={{ flex: 1, padding: '8px 20px 16px', overflow: 'auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '4px 0 14px' }}>
        <div style={{ width: 32, height: 32, borderRadius: 10, background: t.surface, boxShadow: t.shadowSm, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16 }}>‹</div>
        <div style={{ flex: 1, fontSize: 17, fontWeight: 700 }}>Skills</div>
        <CCPill theme={t} variant="solid" size="sm" icon={<span>+</span>}>Buat</CCPill>
      </div>

      {/* tabs */}
      <div style={{ display: 'flex', gap: 6, padding: 4, borderRadius: 999, background: t.surface2, marginBottom: 14 }}>
        {[`Semua · ${skills.length}`, `Built-in · 4`, `Custom · 2`].map((tab, i) => (
          <div key={tab} style={{
            flex: 1, padding: '8px 4px', borderRadius: 999, textAlign: 'center',
            fontSize: 12, fontWeight: 600,
            background: i === 0 ? t.surface : 'transparent',
            color: i === 0 ? t.text : t.textDim,
            boxShadow: i === 0 ? t.shadowSm : 'none',
          }}>{tab}</div>
        ))}
      </div>

      {/* skill list */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
        {skills.map((sk) => (
          <div key={sk.name} style={{
            background: t.surface, borderRadius: t.radius, padding: '12px 14px',
            boxShadow: t.shadowSm,
            display: 'flex', alignItems: 'center', gap: 12,
            opacity: sk.on ? 1 : 0.55,
          }}>
            <div style={{ width: 6, height: 32, borderRadius: 3, background: ccStateColor(sevColor(sk.sev)) }} />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 700, fontFamily: t.fontMono }}>{sk.name}</div>
              <div style={{ display: 'flex', gap: 8, fontSize: 10, color: t.textDim, marginTop: 2 }}>
                <span>{sk.cat}</span>
                <span>·</span>
                <span style={{ color: ccStateColor(sevColor(sk.sev), { l: dark ? 0.82 : 0.45 }), fontWeight: 700 }}>{sk.sev.toUpperCase()}</span>
              </div>
            </div>
            <div style={{
              width: 36, height: 22, borderRadius: 11,
              background: sk.on ? t.accent : t.border,
              padding: 2, display: 'flex',
              justifyContent: sk.on ? 'flex-end' : 'flex-start',
            }}>
              <div style={{ width: 18, height: 18, borderRadius: '50%', background: 'white', boxShadow: '0 1px 3px rgba(0,0,0,0.2)' }} />
            </div>
          </div>
        ))}
      </div>

      {/* JSON editor preview */}
      <div style={{ background: dark ? 'oklch(0.13 0.012 320)' : 'oklch(0.20 0.014 320)', borderRadius: t.radius, padding: '12px 14px', fontFamily: t.fontMono, fontSize: 10.5, lineHeight: 1.65, color: 'oklch(0.85 0.01 80)' }}>
        <div style={{ color: 'oklch(0.6 0.01 80)', fontSize: 10, marginBottom: 6 }}># morning_brief.json</div>
        <div><span style={{ color: 'oklch(0.78 0.10 285)' }}>"name"</span>: <span style={{ color: 'oklch(0.82 0.10 60)' }}>"morning_brief"</span>,</div>
        <div><span style={{ color: 'oklch(0.78 0.10 285)' }}>"trigger"</span>: <span style={{ color: 'oklch(0.82 0.10 60)' }}>"06:30"</span>,</div>
        <div><span style={{ color: 'oklch(0.78 0.10 285)' }}>"steps"</span>: [</div>
        <div style={{ paddingLeft: 14 }}><span style={{ color: 'oklch(0.82 0.10 60)' }}>"baca cuaca"</span>,</div>
        <div style={{ paddingLeft: 14 }}><span style={{ color: 'oklch(0.82 0.10 60)' }}>"baca kalender"</span></div>
        <div>]</div>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// PERSONA EDITOR
// ─────────────────────────────────────────────────────────────
function ScreenPersona({ t, dark }) {
  return (
    <div style={{ flex: 1, padding: '8px 20px 16px', overflow: 'auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '4px 0 14px' }}>
        <div style={{ width: 32, height: 32, borderRadius: 10, background: t.surface, boxShadow: t.shadowSm, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16 }}>‹</div>
        <div style={{ flex: 1, fontSize: 17, fontWeight: 700 }}>Persona — Fuu</div>
        <div style={{ fontSize: 11, color: ccStateColor('COMPLETE', { l: dark ? 0.85 : 0.4 }), fontWeight: 700, letterSpacing: 0.5 }}>SAVED</div>
      </div>

      {/* persona avatar = blob */}
      <div style={{
        background: t.surface, borderRadius: t.radiusLg, padding: 18, boxShadow: t.shadowSm,
        display: 'flex', alignItems: 'center', gap: 14, marginBottom: 14,
      }}>
        <CCBlob size={72} hue={350} />
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 18, fontWeight: 700, letterSpacing: -0.3 }}>Fuu</div>
          <div style={{ fontSize: 12, color: t.textDim, marginTop: 2 }}>Asisten yang lembut & to-the-point</div>
          <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
            <span style={{ fontSize: 10, padding: '3px 8px', borderRadius: 999, background: t.accentSoft, color: t.accentInk, fontWeight: 600 }}>kawaii</span>
            <span style={{ fontSize: 10, padding: '3px 8px', borderRadius: 999, background: t.surface2, color: t.textDim, fontWeight: 600 }}>id</span>
            <span style={{ fontSize: 10, padding: '3px 8px', borderRadius: 999, background: t.surface2, color: t.textDim, fontWeight: 600 }}>casual</span>
          </div>
        </div>
      </div>

      {/* System prompt editor */}
      <div style={{ fontSize: 11, color: t.textDim, fontWeight: 700, letterSpacing: 1, textTransform: 'uppercase', marginBottom: 8 }}>System prompt · Fuu.md</div>
      <div style={{ background: dark ? 'oklch(0.13 0.012 320)' : 'oklch(0.20 0.014 320)', borderRadius: t.radius, padding: '14px 16px', fontFamily: t.fontMono, fontSize: 11.5, lineHeight: 1.6, color: 'oklch(0.88 0.01 80)', marginBottom: 14 }}>
        <div style={{ color: 'oklch(0.78 0.10 285)' }}># Identity</div>
        <div>Kamu adalah Fuu, asisten ChibiClaw.</div>
        <div>&nbsp;</div>
        <div style={{ color: 'oklch(0.78 0.10 285)' }}># Tone</div>
        <div>- Lembut, jelas, hindari basa-basi.</div>
        <div>- Selalu konfirmasi sebelum tindakan</div>
        <div>&nbsp;&nbsp;sensitif (OTP, transfer, hapus).</div>
        <div>&nbsp;</div>
        <div style={{ color: 'oklch(0.78 0.10 285)' }}># Language</div>
        <div>- Default: Bahasa Indonesia kasual.</div>
        <div style={{ color: ccStateColor('EXECUTING', { l: 0.78 }) }}>- Use English jika user write in EN_</div>
      </div>

      {/* Voice & language */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {[
          { k: 'Voice', v: 'Fuu (soft)', icon: '♪' },
          { k: 'Bahasa', v: 'Indonesia', icon: '🌐' },
          { k: 'Response length', v: 'Padat', icon: '≡' },
        ].map((row) => (
          <div key={row.k} style={{ background: t.surface, borderRadius: t.radius, padding: '12px 14px', boxShadow: t.shadowSm, display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ width: 32, height: 32, borderRadius: 10, background: t.accentSoft, color: t.accentInk, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14 }}>{row.icon}</div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 600 }}>{row.k}</div>
              <div style={{ fontSize: 11, color: t.textDim }}>{row.v}</div>
            </div>
            <div style={{ color: t.textDim, fontSize: 14 }}>›</div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// CHAT / VOICE INTERFACE
// ─────────────────────────────────────────────────────────────
function ScreenChat({ t, dark }) {
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
      {/* header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 20px 12px', borderBottom: `1px solid ${t.border}` }}>
        <CCBlob size={36} hue={350} />
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 14, fontWeight: 700 }}>Fuu</div>
          <div style={{ fontSize: 10, color: ccStateColor('COMPLETE', { l: dark ? 0.85 : 0.45 }), fontWeight: 600, display: 'flex', alignItems: 'center', gap: 4 }}>
            <span style={{ width: 5, height: 5, borderRadius: '50%', background: ccStateColor('COMPLETE') }} />
            online · E2B
          </div>
        </div>
        <div style={{ fontSize: 18, color: t.textDim }}>⋯</div>
      </div>

      {/* messages */}
      <div style={{ flex: 1, padding: '14px 18px', overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ textAlign: 'center', fontSize: 10, color: t.textMuted, fontFamily: t.fontMono, letterSpacing: 1 }}>HARI INI · 14:32</div>

        {/* user */}
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <div style={{ maxWidth: '78%', padding: '10px 14px', borderRadius: 18, borderBottomRightRadius: 6, background: t.accent, color: 'white', fontSize: 14, lineHeight: 1.4 }}>Kirim pesan ke Budi: ngajak meeting jam 3 sore</div>
        </div>

        {/* fuu — planning */}
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
          <CCBlob size={26} hue={350} />
          <div style={{ maxWidth: '78%', padding: '10px 14px', borderRadius: 18, borderBottomLeftRadius: 6, background: t.surface, color: t.text, fontSize: 14, lineHeight: 1.4, boxShadow: t.shadowSm }}>
            Oke, aku susun rencana dulu ya~
          </div>
        </div>

        {/* inline execution status card */}
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
          <div style={{ width: 26 }} />
          <div style={{
            maxWidth: '90%', padding: '12px 14px', borderRadius: 16,
            background: ccStateColor('EXECUTING', { a: 0.10 }),
            border: `1px solid ${ccStateColor('EXECUTING', { a: 0.25 })}`,
            fontSize: 12, lineHeight: 1.6, fontFamily: t.fontMono,
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
              <span style={{ fontWeight: 700, color: ccStateColor('EXECUTING', { l: dark ? 0.85 : 0.4 }), letterSpacing: 1, textTransform: 'uppercase', fontSize: 10 }}>EXECUTING</span>
              <span style={{ color: t.textDim, fontSize: 10 }}>3/4 · 1.2s</span>
            </div>
            <div style={{ color: ccStateColor('COMPLETE', { l: dark ? 0.85 : 0.4 }) }}>✓ kontak Budi resolved</div>
            <div style={{ color: ccStateColor('COMPLETE', { l: dark ? 0.85 : 0.4 }) }}>✓ WhatsApp dibuka</div>
            <div style={{ color: ccStateColor('EXECUTING', { l: dark ? 0.85 : 0.4 }) }}>▸ mengetik pesan…</div>
            <div style={{ color: t.textDim }}>· awaiting send</div>
          </div>
        </div>

        {/* fuu — typing dots */}
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
          <CCBlob size={26} hue={350} />
          <div style={{ padding: '12px 14px', borderRadius: 18, borderBottomLeftRadius: 6, background: t.surface, boxShadow: t.shadowSm, display: 'flex', gap: 4 }}>
            {[0, 1, 2].map((d) => (
              <div key={d} style={{ width: 6, height: 6, borderRadius: '50%', background: t.textDim, animation: 'cc-bounce 1.4s ease-in-out infinite', animationDelay: `${d * 0.16}s` }} />
            ))}
          </div>
        </div>
      </div>

      {/* voice/text input bar */}
      <div style={{ padding: '10px 18px 16px', borderTop: `1px solid ${t.border}` }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 10,
          background: t.surface, borderRadius: 999, padding: '6px 8px 6px 16px',
          boxShadow: t.shadowSm,
        }}>
          <span style={{ flex: 1, fontSize: 13, color: t.textMuted }}>Ketik atau tekan mic…</span>
          {/* mini wave */}
          <div style={{ display: 'flex', gap: 2, alignItems: 'center', height: 16 }}>
            {[6, 12, 8, 14, 6].map((h, i) => (
              <div key={i} style={{ width: 2, height: h, borderRadius: 1, background: t.textDim, opacity: 0.5 }} />
            ))}
          </div>
          <div style={{
            width: 36, height: 36, borderRadius: '50%',
            background: `radial-gradient(circle at 30% 30%, ${ccStateColor('EXECUTING', { l: 0.92, c: 0.04 })}, ${ccStateColor('EXECUTING')})`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: `0 0 0 4px ${ccStateColor('EXECUTING', { a: 0.2 })}`,
          }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <rect x="9" y="3" width="6" height="12" rx="3" />
              <path d="M5 11a7 7 0 0014 0" />
            </svg>
          </div>
        </div>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// FLOATING OVERLAY — shown over Android home screen mock
// Two states: collapsed bubble + expanded bubble
// ─────────────────────────────────────────────────────────────
function ScreenOverlay({ t, dark, expanded = false }) {
  // Faux Android home behind it
  const homeApps = [
    'Camera', 'Photos', 'WhatsApp', 'Maps',
    'Chrome', 'Gmail', 'YouTube', 'Calendar',
    'Music', 'Settings', 'Files', 'Clock',
  ];
  return (
    <div style={{ flex: 1, position: 'relative', overflow: 'hidden', background: `linear-gradient(180deg, oklch(${dark ? 0.22 : 0.94} 0.05 280), oklch(${dark ? 0.18 : 0.92} 0.04 350))` }}>
      {/* faux clock widget */}
      <div style={{ textAlign: 'center', padding: '24px 0 8px', color: dark ? 'white' : 'oklch(0.25 0.02 280)' }}>
        <div style={{ fontSize: 56, fontWeight: 300, letterSpacing: -2, lineHeight: 1 }}>14:32</div>
        <div style={{ fontSize: 12, opacity: 0.7, marginTop: 4 }}>Sabtu, 10 Mei</div>
      </div>

      {/* faux app grid */}
      <div style={{ padding: '16px 24px 0', display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 18 }}>
        {homeApps.map((a) => (
          <div key={a} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
            <div style={{ width: 48, height: 48, borderRadius: 14, background: `oklch(${0.6 + ((a.length * 13) % 10) * 0.03} 0.06 ${(a.charCodeAt(0) * 7) % 360})` }} />
            <div style={{ fontSize: 9, color: dark ? 'rgba(255,255,255,0.85)' : 'oklch(0.25 0.02 280)' }}>{a}</div>
          </div>
        ))}
      </div>

      {/* dock */}
      <div style={{ position: 'absolute', bottom: 22, left: 24, right: 24, height: 64, borderRadius: 22, background: dark ? 'rgba(255,255,255,0.1)' : 'rgba(255,255,255,0.5)', backdropFilter: 'blur(12px)' }} />

      {/* THE OVERLAY BUBBLE */}
      {!expanded && (
        <div style={{
          position: 'absolute', top: 110, right: 16,
          width: 56, height: 56, borderRadius: 22,
          background: t.surface,
          boxShadow: t.shadowMd + ', ' + t.shadowGlow('EXECUTING'),
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          flexDirection: 'column', gap: 2,
        }}>
          <div style={{
            width: 22, height: 22, borderRadius: CC_BLOB,
            background: `radial-gradient(circle at 30% 30%, ${ccStateColor('EXECUTING', { l: 0.92, c: 0.04 })}, ${ccStateColor('EXECUTING')})`,
            animation: 'cc-blob 4s ease-in-out infinite, cc-breathe 1.6s ease-in-out infinite',
          }} />
          <div style={{ fontSize: 8, fontFamily: t.fontMono, color: t.textDim, fontWeight: 700, letterSpacing: 0.5 }}>3/4</div>
        </div>
      )}

      {expanded && (
        <div style={{
          position: 'absolute', top: 90, left: 16, right: 16,
          background: t.surface, borderRadius: t.radiusLg,
          boxShadow: t.shadowMd + ', ' + t.shadowGlow('EXECUTING'),
          padding: 16,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
            <div style={{
              width: 36, height: 36, borderRadius: CC_BLOB,
              background: `radial-gradient(circle at 30% 30%, ${ccStateColor('EXECUTING', { l: 0.92, c: 0.04 })}, ${ccStateColor('EXECUTING')})`,
              animation: 'cc-blob 4s ease-in-out infinite, cc-breathe 1.6s ease-in-out infinite',
            }} />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 1, color: ccStateColor('EXECUTING', { l: dark ? 0.85 : 0.4 }) }}>EXECUTING · 3/4</div>
              <div style={{ fontSize: 13, fontWeight: 600 }}>Mengetik pesan ke Budi…</div>
            </div>
            <div style={{ width: 28, height: 28, borderRadius: 14, background: t.surface2, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, color: t.textDim }}>×</div>
          </div>

          {/* mini timeline */}
          <div style={{ display: 'flex', gap: 4, alignItems: 'center', marginBottom: 12 }}>
            {[1, 2, 3, 4].map((n) => (
              <div key={n} style={{ flex: 1, height: 4, borderRadius: 2, background: n <= 2 ? ccStateColor('COMPLETE') : n === 3 ? ccStateColor('EXECUTING') : t.border }} />
            ))}
          </div>

          <div style={{ display: 'flex', gap: 8 }}>
            <CCPill theme={t} variant="solid" size="md" style={{ flex: 1, justifyContent: 'center', background: ccStateColor('ERROR'), color: 'white' }}>■ Stop</CCPill>
            <CCPill theme={t} variant="ghost" size="md" style={{ flex: 1, justifyContent: 'center' }}>Detail</CCPill>
          </div>
          <div style={{ fontSize: 10, color: t.textMuted, textAlign: 'center', marginTop: 10, fontFamily: t.fontMono }}>tahan lama untuk kill switch</div>
        </div>
      )}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// SETUP WIZARD — STEP 1 · Welcome
// ─────────────────────────────────────────────────────────────
function ScreenSetupWelcome({ t, dark }) {
  return (
    <div style={{ flex: 1, padding: '12px 22px 20px', overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', gap: 5, padding: '4px 0 18px' }}>
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} style={{ flex: 1, height: 4, borderRadius: 2, background: i === 0 ? t.accent : t.border }} />
        ))}
      </div>

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center', gap: 18 }}>
        {/* Stack of blobs as visual anchor */}
        <div style={{ position: 'relative', width: 160, height: 140 }}>
          <div style={{ position: 'absolute', left: 10, top: 20, transform: 'rotate(-8deg)' }}><CCBlob size={70} hue={220} /></div>
          <div style={{ position: 'absolute', right: 6, top: 8, transform: 'rotate(12deg)' }}><CCBlob size={60} hue={160} /></div>
          <div style={{ position: 'absolute', left: 50, bottom: 0 }}><CCBlob size={84} hue={350} /></div>
          <div style={{ position: 'absolute', top: 30, right: 22, color: t.accent }}><CCSparkle size={14} /></div>
          <div style={{ position: 'absolute', bottom: 20, left: 16, color: ccStateColor('PLANNING') }}><CCSparkle size={10} /></div>
        </div>

        <div>
          <div style={{ fontSize: 30, fontWeight: 800, letterSpacing: -0.6, lineHeight: 1.1 }}>Halo, kenalin —<br/>aku <span style={{ color: t.accent }}>Fuu</span>.</div>
          <div style={{ fontSize: 14, color: t.textDim, marginTop: 14, lineHeight: 1.5, maxWidth: 290, marginInline: 'auto' }}>
            Asisten on-device buat HP-mu. Ngerti suara, ngeksekusi task, dan{' '}
            <b style={{ color: t.text }}>nggak kirim apa-apa ke cloud</b>.
          </div>
        </div>

        <div style={{ display: 'flex', gap: 10, fontSize: 11, color: t.textDim, fontFamily: t.fontMono, marginTop: 4 }}>
          <span>·on-device</span><span>·private</span><span>·offline-first</span>
        </div>
      </div>

      <CCPill theme={t} variant="solid" size="lg" style={{ width: '100%', justifyContent: 'center', marginTop: 18 }}>Mulai setup →</CCPill>
      <div style={{ textAlign: 'center', fontSize: 11, color: t.textMuted, marginTop: 10 }}>~3 menit · butuh ruang 2.5 GB</div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// SETUP WIZARD — STEP 2 · Download model
// ─────────────────────────────────────────────────────────────
function ScreenSetupDownload({ t, dark }) {
  return (
    <div style={{ flex: 1, padding: '12px 22px 20px', overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', gap: 5, padding: '4px 0 18px' }}>
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} style={{ flex: 1, height: 4, borderRadius: 2, background: i === 0 ? ccStateColor('COMPLETE') : i === 1 ? t.accent : t.border }} />
        ))}
      </div>

      <div style={{ fontSize: 11, color: t.textDim, fontWeight: 700, letterSpacing: 1.5, textTransform: 'uppercase' }}>Langkah 2 dari 8</div>
      <div style={{ fontSize: 26, fontWeight: 700, letterSpacing: -0.5, marginTop: 4, lineHeight: 1.15 }}>Unduh otak Fuu</div>
      <div style={{ fontSize: 14, color: t.textDim, marginTop: 6, lineHeight: 1.5 }}>Sekali aja. Setelah ini Fuu jalan tanpa internet.</div>

      {/* Big progress ring */}
      <div style={{ background: t.surface, borderRadius: t.radiusLg, padding: 22, boxShadow: t.shadowSm, marginTop: 18 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 18 }}>
          <div style={{ position: 'relative', width: 110, height: 110, flexShrink: 0 }}>
            <svg width="110" height="110" viewBox="0 0 110 110">
              <circle cx="55" cy="55" r="46" fill="none" stroke={t.surface2} strokeWidth="9" />
              <circle cx="55" cy="55" r="46" fill="none" stroke={ccStateColor('EXECUTING')} strokeWidth="9"
                strokeLinecap="round" strokeDasharray={2 * Math.PI * 46} strokeDashoffset={2 * Math.PI * 46 * 0.42}
                transform="rotate(-90 55 55)" />
            </svg>
            <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
              <div style={{ fontSize: 24, fontWeight: 700, fontFamily: t.fontMono, color: ccStateColor('EXECUTING', { l: dark ? 0.85 : 0.4 }) }}>58<span style={{ fontSize: 12 }}>%</span></div>
              <div style={{ fontSize: 9, color: t.textDim, letterSpacing: 0.8, textTransform: 'uppercase', fontWeight: 600 }}>1.4/2.5 GB</div>
            </div>
          </div>
          <div style={{ flex: 1, fontFamily: t.fontMono, fontSize: 11, lineHeight: 1.6 }}>
            <div style={{ color: ccStateColor('COMPLETE', { l: dark ? 0.85 : 0.45 }) }}>✓ tokenizer.json</div>
            <div style={{ color: ccStateColor('COMPLETE', { l: dark ? 0.85 : 0.45 }) }}>✓ config.json</div>
            <div style={{ color: ccStateColor('EXECUTING', { l: dark ? 0.85 : 0.4 }) }}>▸ model.gguf</div>
            <div style={{ color: t.textDim }}>· vocab.bin</div>
          </div>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 14, fontSize: 11, color: t.textDim, fontFamily: t.fontMono, paddingTop: 10, borderTop: `1px solid ${t.border}` }}>
          <span>3.2 MB/s</span><span>~5 menit</span><span>WiFi</span>
        </div>
      </div>

      <div style={{ marginTop: 14, padding: 12, background: ccStateColor('PLANNING', { a: 0.10 }), borderRadius: t.radius, fontSize: 12, color: t.text, lineHeight: 1.5 }}>
        💡 Tetap colokin charger. Bisa lanjut di latar belakang.
      </div>

      <div style={{ flex: 1 }} />

      <div style={{ display: 'flex', gap: 10, marginTop: 18 }}>
        <CCPill theme={t} variant="ghost" size="lg" style={{ flex: 1, justifyContent: 'center' }}>Pause</CCPill>
        <CCPill theme={t} variant="solid" size="lg" style={{ flex: 2, justifyContent: 'center' }}>Lanjut sambil unduh →</CCPill>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// SETUP WIZARD — STEP 8 · Done
// ─────────────────────────────────────────────────────────────
function ScreenSetupDone({ t, dark }) {
  return (
    <div style={{ flex: 1, padding: '12px 22px 20px', overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', gap: 5, padding: '4px 0 18px' }}>
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} style={{ flex: 1, height: 4, borderRadius: 2, background: ccStateColor('COMPLETE') }} />
        ))}
      </div>

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center', gap: 16 }}>
        {/* Big complete check inside a state blob */}
        <div style={{ position: 'relative' }}>
          <div style={{
            width: 130, height: 130, borderRadius: CC_BLOB,
            background: `radial-gradient(circle at 35% 30%, ${ccStateColor('COMPLETE', { l: 0.92, c: 0.04 })}, ${ccStateColor('COMPLETE')})`,
            boxShadow: `0 0 0 10px ${ccStateColor('COMPLETE', { a: 0.15 })}, 0 16px 40px ${ccStateColor('COMPLETE', { a: 0.4 })}`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: 'white', fontSize: 60, fontWeight: 700,
            animation: 'cc-blob 8s ease-in-out infinite',
          }}>✓</div>
          <div style={{ position: 'absolute', top: -4, right: -4, color: t.accent }}><CCSparkle size={20} /></div>
          <div style={{ position: 'absolute', bottom: 0, left: -8, color: ccStateColor('PLANNING') }}><CCSparkle size={14} /></div>
          <div style={{ position: 'absolute', top: 30, left: -20, color: ccStateColor('WAITING') }}><CCSparkle size={10} /></div>
        </div>

        <div>
          <div style={{ fontSize: 28, fontWeight: 800, letterSpacing: -0.5 }}>Siap dipakai!</div>
          <div style={{ fontSize: 14, color: t.textDim, marginTop: 8, lineHeight: 1.5, maxWidth: 280, marginInline: 'auto' }}>
            Coba bilang <i>"Hai Fuu, setel alarm jam 7"</i> atau ketuk dashboard untuk eksplor.
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%', marginTop: 8 }}>
          {[
            ['Model', 'E2B · 1.4 GB · ready'],
            ['Accessibility', 'aktif'],
            ['Whitelist', '5 apps'],
          ].map(([k, v]) => (
            <div key={k} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 14px', background: t.surface, borderRadius: 999, boxShadow: t.shadowSm, fontSize: 12 }}>
              <span style={{ color: t.textDim }}>{k}</span>
              <span style={{ fontWeight: 600, fontFamily: t.fontMono }}>{v}</span>
            </div>
          ))}
        </div>
      </div>

      <CCPill theme={t} variant="solid" size="lg" style={{ width: '100%', justifyContent: 'center', marginTop: 18 }}>Buka dashboard ✿</CCPill>
    </div>
  );
}

Object.assign(window, { ScreenSetup, ScreenSetupWelcome, ScreenSetupDownload, ScreenSetupDone, ScreenAISettings, ScreenSafety, ScreenSkills, ScreenPersona, ScreenChat, ScreenOverlay, ScreenApproval, ScreenError, ScreenEmpty, ScreenHistory, ScreenNotification });

// ─────────────────────────────────────────────────────────────
// TASK HISTORY — past tasks, groupable, re-runnable
// ─────────────────────────────────────────────────────────────
function ScreenHistory({ t, dark }) {
  const groups = [
    {
      label: 'HARI INI', items: [
        { name: 'Kirim WA ke Budi', state: 'EXECUTING', time: '14:32', dur: '3.2s · 3/4', sub: 'mengetik pesan…' },
        { name: 'Cari resep nasi goreng', state: 'COMPLETE', time: '13:18', dur: '1.8s', sub: '5 hasil ditemukan' },
        { name: 'Transfer 50rb ke Mama', state: 'WAITING', time: '11:04', dur: 'paused', sub: 'butuh approval kamu' },
      ]
    },
    {
      label: 'KEMARIN', items: [
        { name: 'Setel alarm 06:30', state: 'COMPLETE', time: '22:14', dur: '0.6s', sub: 'aktif' },
        { name: 'Order Gojek ke kantor', state: 'ERROR', time: '07:48', dur: '12s timeout', sub: 'driver tidak ditemukan' },
        { name: 'Morning brief', state: 'COMPLETE', time: '06:30', dur: '4.1s', sub: 'cuaca, kalender, 3 berita' },
      ]
    },
  ];
  return (
    <div style={{ flex: 1, padding: '8px 20px 16px', overflow: 'auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '4px 0 14px' }}>
        <div style={{ width: 32, height: 32, borderRadius: 10, background: t.surface, boxShadow: t.shadowSm, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16 }}>‹</div>
        <div style={{ flex: 1, fontSize: 17, fontWeight: 700 }}>Riwayat</div>
        <div style={{ width: 32, height: 32, borderRadius: 10, background: t.surface, boxShadow: t.shadowSm, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, color: t.textDim }}>⌕</div>
      </div>

      {/* Stats summary */}
      <div style={{ background: t.surface, borderRadius: t.radius, padding: 14, boxShadow: t.shadowSm, marginBottom: 16, display: 'flex' }}>
        {[
          { k: '24', v: 'minggu ini', state: 'PLANNING' },
          { k: '94%', v: 'sukses', state: 'COMPLETE' },
          { k: '1.4s', v: 'rata-rata', state: 'EXECUTING' },
          { k: '2', v: 'error', state: 'ERROR' },
        ].map((s, i, arr) => (
          <div key={s.v} style={{ flex: 1, textAlign: 'center', borderRight: i < arr.length - 1 ? `1px solid ${t.border}` : 'none' }}>
            <div style={{ fontSize: 18, fontWeight: 700, color: ccStateColor(s.state, { l: dark ? 0.85 : 0.45 }), fontFamily: t.fontMono }}>{s.k}</div>
            <div style={{ fontSize: 9, color: t.textDim, letterSpacing: 0.5, textTransform: 'uppercase', fontWeight: 600, marginTop: 2 }}>{s.v}</div>
          </div>
        ))}
      </div>

      {/* Grouped task list */}
      {groups.map((g) => (
        <div key={g.label} style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 10, color: t.textDim, fontWeight: 700, letterSpacing: 1.2, padding: '0 4px 8px', display: 'flex', justifyContent: 'space-between' }}>
            <span>{g.label}</span>
            <span style={{ fontFamily: t.fontMono, fontWeight: 500 }}>{g.items.length}</span>
          </div>
          <div style={{ background: t.surface, borderRadius: t.radius, boxShadow: t.shadowSm, overflow: 'hidden' }}>
            {g.items.map((task, i) => (
              <div key={task.name} style={{
                display: 'flex', alignItems: 'center', gap: 10, padding: '12px 14px',
                borderTop: i ? `1px solid ${t.border}` : 'none',
              }}>
                <div style={{ width: 6, height: 32, borderRadius: 3, background: ccStateColor(task.state) }} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>{task.name}</div>
                  <div style={{ fontSize: 11, color: t.textDim, marginTop: 1 }}>{task.sub}</div>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontSize: 11, fontFamily: t.fontMono, color: t.textDim }}>{task.time}</div>
                  <div style={{ fontSize: 9, fontFamily: t.fontMono, color: ccStateColor(task.state, { l: dark ? 0.82 : 0.45 }), fontWeight: 700, marginTop: 1 }}>{task.dur}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// NOTIFICATION — how Fuu appears in Android notification shade
// ─────────────────────────────────────────────────────────────
function ScreenNotification({ t, dark }) {
  return (
    <div style={{ flex: 1, position: 'relative', overflow: 'hidden', background: `linear-gradient(180deg, oklch(${dark ? 0.16 : 0.92} 0.04 280), oklch(${dark ? 0.12 : 0.88} 0.05 350))` }}>
      {/* big clock */}
      <div style={{ textAlign: 'center', padding: '20px 0 6px', color: dark ? 'white' : 'oklch(0.25 0.02 280)' }}>
        <div style={{ fontSize: 48, fontWeight: 300, letterSpacing: -1.5, lineHeight: 1, fontVariantNumeric: 'tabular-nums' }}>14:32</div>
        <div style={{ fontSize: 11, opacity: 0.7, marginTop: 2 }}>Sabtu, 10 Mei</div>
      </div>

      {/* quick settings strip */}
      <div style={{ display: 'flex', gap: 6, padding: '0 16px 12px', justifyContent: 'space-between' }}>
        {['📶', '✈', '🔆', '🔊', '🌙', '🎵'].map((ic) => (
          <div key={ic} style={{ width: 36, height: 36, borderRadius: 12, background: dark ? 'rgba(255,255,255,0.12)' : 'rgba(255,255,255,0.6)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14 }}>{ic}</div>
        ))}
      </div>

      {/* notification stack */}
      <div style={{ padding: '0 12px', display: 'flex', flexDirection: 'column', gap: 8 }}>
        {/* ChibiClaw — active task */}
        <div style={{
          background: t.surface, borderRadius: 22, padding: '12px 14px',
          boxShadow: t.shadowMd + ', 0 0 0 2px ' + ccStateColor('EXECUTING', { a: 0.3 }),
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 11, color: t.textDim, marginBottom: 6 }}>
            <div style={{ width: 16, height: 16, borderRadius: 5, background: t.accent, color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 9, fontWeight: 800 }}>C</div>
            <span style={{ fontWeight: 600, color: t.text }}>ChibiClaw</span>
            <span>· sekarang</span>
            <span style={{
              marginLeft: 'auto', fontSize: 9, fontWeight: 700, letterSpacing: 1,
              padding: '2px 7px', borderRadius: 999,
              background: ccStateColor('EXECUTING', { a: 0.2 }),
              color: ccStateColor('EXECUTING', { l: dark ? 0.85 : 0.4 }),
            }}>EXECUTING</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{
              width: 36, height: 36, borderRadius: CC_BLOB,
              background: `radial-gradient(circle at 30% 30%, ${ccStateColor('EXECUTING', { l: 0.92, c: 0.04 })}, ${ccStateColor('EXECUTING')})`,
              animation: 'cc-blob 4s ease-in-out infinite, cc-breathe 1.6s ease-in-out infinite',
              flexShrink: 0,
            }} />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 600 }}>Mengirim pesan ke Budi</div>
              <div style={{ fontSize: 11, color: t.textDim, marginTop: 2 }}>3 dari 4 langkah · 1.2s</div>
              <div style={{ display: 'flex', gap: 3, marginTop: 6 }}>
                {[1, 2, 3, 4].map((n) => (
                  <div key={n} style={{ flex: 1, height: 3, borderRadius: 2, background: n <= 2 ? ccStateColor('COMPLETE') : n === 3 ? ccStateColor('EXECUTING') : t.border }} />
                ))}
              </div>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 6, marginTop: 10, paddingTop: 10, borderTop: `1px solid ${t.border}` }}>
            <div style={{ flex: 1, padding: '6px 0', textAlign: 'center', fontSize: 12, fontWeight: 600, color: ccStateColor('ERROR', { l: dark ? 0.85 : 0.45 }) }}>■ Stop</div>
            <div style={{ flex: 1, padding: '6px 0', textAlign: 'center', fontSize: 12, fontWeight: 600, color: t.text }}>Detail</div>
            <div style={{ flex: 1, padding: '6px 0', textAlign: 'center', fontSize: 12, fontWeight: 600, color: t.textDim }}>Mute</div>
          </div>
        </div>

        {/* Approval notification */}
        <div style={{
          background: t.surface, borderRadius: 22, padding: '12px 14px',
          boxShadow: t.shadowSm + ', 0 0 0 2px ' + ccStateColor('WAITING', { a: 0.3 }),
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 11, color: t.textDim, marginBottom: 6 }}>
            <div style={{ width: 16, height: 16, borderRadius: 5, background: t.accent, color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 9, fontWeight: 800 }}>C</div>
            <span style={{ fontWeight: 600, color: t.text }}>ChibiClaw</span>
            <span>· 2m</span>
            <span style={{ marginLeft: 'auto', fontSize: 9, fontWeight: 700, letterSpacing: 1, padding: '2px 7px', borderRadius: 999, background: ccStateColor('WAITING', { a: 0.22 }), color: ccStateColor('WAITING', { l: dark ? 0.85 : 0.35 }) }}>WAITING</span>
          </div>
          <div style={{ fontSize: 13, fontWeight: 600 }}>Transfer Rp 50.000 ke Mama?</div>
          <div style={{ fontSize: 11, color: t.textDim, marginTop: 2 }}>Tahan untuk lihat detail, atau swipe ke approve</div>
        </div>

        {/* Other app */}
        <div style={{ background: dark ? 'rgba(255,255,255,0.08)' : 'rgba(255,255,255,0.7)', backdropFilter: 'blur(8px)', borderRadius: 22, padding: '10px 14px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 11, color: dark ? 'rgba(255,255,255,0.7)' : 'rgba(0,0,0,0.55)', marginBottom: 4 }}>
            <div style={{ width: 14, height: 14, borderRadius: 4, background: 'oklch(0.62 0.18 145)' }} />
            <span style={{ fontWeight: 600, color: dark ? 'white' : 'oklch(0.25 0.02 280)' }}>WhatsApp</span>
            <span>· 5m</span>
          </div>
          <div style={{ fontSize: 12, color: dark ? 'white' : 'oklch(0.25 0.02 280)' }}>Budi: oke, sampai ketemu</div>
        </div>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// APPROVAL MODAL — sensitive action confirmation (WAITING state)
// Shows transparently what Fuu is about to do before doing it.
// ─────────────────────────────────────────────────────────────
function ScreenApproval({ t, dark }) {
  return (
    <div style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
      {/* dimmed dashboard behind */}
      <div style={{ position: 'absolute', inset: 0, padding: '12px 20px', opacity: 0.4, filter: 'blur(2px)', pointerEvents: 'none' }}>
        <div style={{ fontSize: 22, fontWeight: 700, marginBottom: 12 }}>ChibiClaw</div>
        <div style={{ background: t.surface, borderRadius: t.radiusLg, height: 180, boxShadow: t.shadowSm }} />
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8, marginTop: 12 }}>
          <div style={{ background: t.surface, borderRadius: t.radius, height: 60 }} />
          <div style={{ background: t.surface, borderRadius: t.radius, height: 60 }} />
          <div style={{ background: t.surface, borderRadius: t.radius, height: 60 }} />
        </div>
      </div>
      {/* scrim */}
      <div style={{ position: 'absolute', inset: 0, background: dark ? 'rgba(8,4,12,0.6)' : 'rgba(40,20,40,0.4)', backdropFilter: 'blur(4px)' }} />

      {/* Bottom sheet modal */}
      <div style={{
        position: 'absolute', left: 0, right: 0, bottom: 0,
        background: t.surface, borderTopLeftRadius: 32, borderTopRightRadius: 32,
        padding: '14px 20px 24px', boxShadow: '0 -8px 32px rgba(0,0,0,0.18)',
      }}>
        <div style={{ width: 38, height: 4, borderRadius: 2, background: t.border, margin: '0 auto 14px' }} />

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
          <div style={{ width: 40, height: 40, borderRadius: 14, background: ccStateColor('WAITING', { a: 0.22 }), color: ccStateColor('WAITING', { l: dark ? 0.85 : 0.35 }), display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 22, fontWeight: 700 }}>!</div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 1.5, color: ccStateColor('WAITING', { l: dark ? 0.85 : 0.4 }), textTransform: 'uppercase' }}>BUTUH KONFIRMASI</div>
            <div style={{ fontSize: 18, fontWeight: 700, letterSpacing: -0.3 }}>Fuu mau lakuin ini:</div>
          </div>
        </div>

        {/* The action card — transparent + specific */}
        <div style={{ background: t.surface2, borderRadius: t.radius, padding: 14, marginBottom: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
            <div style={{ width: 32, height: 32, borderRadius: 9, background: 'oklch(0.62 0.18 145)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 700 }}>WA</div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 600 }}>Kirim WhatsApp</div>
              <div style={{ fontSize: 11, color: t.textDim, fontFamily: t.fontMono }}>send_whatsapp · LOW</div>
            </div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 12, lineHeight: 1.5 }}>
            <div style={{ display: 'flex', gap: 8 }}>
              <span style={{ color: t.textDim, minWidth: 54 }}>Ke</span>
              <span style={{ fontWeight: 600 }}>Budi Santoso <span style={{ color: t.textDim, fontFamily: t.fontMono, fontWeight: 400 }}>(+62 812-3456-7890)</span></span>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <span style={{ color: t.textDim, minWidth: 54 }}>Pesan</span>
              <span style={{ fontStyle: 'italic', flex: 1 }}>"Halo Bud, bisa meeting jam 3 sore hari ini?"</span>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 11, color: t.textDim, marginBottom: 14, padding: '0 4px' }}>
          <input type="checkbox" style={{ accentColor: t.accent, width: 14, height: 14 }} />
          <span>Selalu allow untuk Budi · pesan singkat</span>
        </div>

        <div style={{ display: 'flex', gap: 8 }}>
          <CCPill theme={t} variant="ghost" size="lg" style={{ flex: 1, justifyContent: 'center' }}>Batal</CCPill>
          <CCPill theme={t} variant="solid" size="lg" style={{ flex: 2, justifyContent: 'center', background: ccStateColor('COMPLETE'), color: 'white' }}>Kirim ✓</CCPill>
        </div>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// ERROR STATE — recovery flow
// ─────────────────────────────────────────────────────────────
function ScreenError({ t, dark }) {
  return (
    <div style={{ flex: 1, padding: '12px 20px 16px', overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ fontSize: 22, fontWeight: 700, letterSpacing: -0.4 }}>Hmm…</div>
        <CCStateChip state="ERROR" theme={t} size="sm" />
      </div>

      {/* Error hero */}
      <div style={{
        background: t.surface, borderRadius: t.radiusLg, padding: 22, boxShadow: t.shadowSm,
        background: `linear-gradient(135deg, ${ccStateColor('ERROR', { a: 0.10 })}, ${t.surface} 60%)`,
        display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: 12,
      }}>
        {/* sad blob */}
        <div style={{ position: 'relative' }}>
          <div style={{
            width: 88, height: 88, borderRadius: CC_BLOB,
            background: `radial-gradient(circle at 35% 30%, ${ccStateColor('ERROR', { l: 0.92, c: 0.04 })}, ${ccStateColor('ERROR')})`,
            boxShadow: `0 0 0 8px ${ccStateColor('ERROR', { a: 0.12 })}, 0 12px 28px ${ccStateColor('ERROR', { a: 0.35 })}`,
            animation: 'cc-blob 6s ease-in-out infinite',
            transform: 'rotate(-8deg)',
          }} />
          {/* tear */}
          <div style={{ position: 'absolute', left: 32, bottom: -4, width: 8, height: 12, borderRadius: '50% 50% 50% 50% / 60% 60% 40% 40%', background: ccStateColor('PLANNING') }} />
        </div>
        <div>
          <div style={{ fontSize: 18, fontWeight: 700, letterSpacing: -0.3 }}>Gagal kirim pesan</div>
          <div style={{ fontSize: 13, color: t.textDim, marginTop: 4, maxWidth: 260, lineHeight: 1.45 }}>WhatsApp ngga merespon dalam 8 detik. Mungkin lagi loading.</div>
        </div>
      </div>

      {/* Diagnostic */}
      <div style={{ background: t.surface, borderRadius: t.radius, padding: 14, boxShadow: t.shadowSm }}>
        <div style={{ fontSize: 11, color: t.textDim, fontWeight: 700, letterSpacing: 1, textTransform: 'uppercase', marginBottom: 10 }}>Apa yang terjadi</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 12, fontFamily: t.fontMono }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <span style={{ color: ccStateColor('COMPLETE', { l: dark ? 0.85 : 0.4 }) }}>✓</span>
            <span>kontak Budi resolved</span>
            <span style={{ marginLeft: 'auto', color: t.textMuted, fontSize: 10 }}>0.4s</span>
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <span style={{ color: ccStateColor('COMPLETE', { l: dark ? 0.85 : 0.4 }) }}>✓</span>
            <span>WhatsApp dibuka</span>
            <span style={{ marginLeft: 'auto', color: t.textMuted, fontSize: 10 }}>0.4s</span>
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <span style={{ color: ccStateColor('ERROR', { l: dark ? 0.85 : 0.4 }) }}>✗</span>
            <span style={{ color: ccStateColor('ERROR', { l: dark ? 0.85 : 0.4 }) }}>typing field nggak ketemu</span>
            <span style={{ marginLeft: 'auto', color: t.textMuted, fontSize: 10 }}>8.0s timeout</span>
          </div>
        </div>
        <div style={{ marginTop: 12, paddingTop: 12, borderTop: `1px solid ${t.border}`, fontSize: 11, color: t.textDim, fontFamily: t.fontMono, lineHeight: 1.5 }}>
          <span style={{ color: ccStateColor('ERROR', { l: dark ? 0.85 : 0.45 }) }}>err:</span> selector "input[contenteditable='true']" not found in chat_view
        </div>
      </div>

      {/* Recovery actions */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {[
          { icon: '↻', title: 'Coba lagi', sub: 'tunggu 2 detik dulu', primary: true },
          { icon: '✎', title: 'Edit pesan & kirim manual', sub: 'WA terbuka di hp-mu' },
          { icon: '⌫', title: 'Batalin task', sub: 'simpan ke history' },
        ].map((r) => (
          <div key={r.title} style={{
            background: t.surface, borderRadius: t.radius, padding: '12px 14px',
            boxShadow: t.shadowSm,
            border: r.primary ? `2px solid ${t.accent}` : '2px solid transparent',
            display: 'flex', alignItems: 'center', gap: 12,
          }}>
            <div style={{
              width: 36, height: 36, borderRadius: 12,
              background: r.primary ? t.accentSoft : t.surface2,
              color: r.primary ? t.accentInk : t.textDim,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 17, fontWeight: 700,
            }}>{r.icon}</div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 600 }}>{r.title}</div>
              <div style={{ fontSize: 11, color: t.textDim }}>{r.sub}</div>
            </div>
            <div style={{ color: t.textDim, fontSize: 14 }}>›</div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// EMPTY STATE — first-time dashboard
// ─────────────────────────────────────────────────────────────
function ScreenEmpty({ t, dark }) {
  const examples = [
    'Setel alarm jam 6:30',
    'Kirim WA ke Budi: meeting jam 3',
    'Cari resep nasi goreng',
    'Buka kalender besok',
    'Ringkas email terbaru',
  ];
  return (
    <div style={{ flex: 1, padding: '12px 20px 16px', overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontSize: 13, color: t.textDim }}>Selamat datang!</div>
          <div style={{ fontSize: 22, fontWeight: 700, letterSpacing: -0.4 }}>Yuk mulai 🌱</div>
        </div>
        <CCStateChip state="IDLE" theme={t} size="sm" />
      </div>

      {/* Welcome card with the IDLE blob */}
      <div style={{ background: t.surface, borderRadius: t.radiusLg, padding: 20, boxShadow: t.shadowSm, textAlign: 'center' }}>
        <div style={{ position: 'relative', width: 100, height: 100, margin: '0 auto 12px' }}>
          <CCBlob size={100} hue={285} />
          <div style={{ position: 'absolute', top: -2, right: 6, color: t.accent }}><CCSparkle size={14} /></div>
          <div style={{ position: 'absolute', bottom: 6, left: 0, color: ccStateColor('PLANNING') }}><CCSparkle size={10} /></div>
        </div>
        <div style={{ fontSize: 15, fontWeight: 600 }}>Belum ada task</div>
        <div style={{ fontSize: 12, color: t.textDim, marginTop: 4, lineHeight: 1.4 }}>Coba tap salah satu contoh di bawah,<br/>atau tahan mic untuk bicara.</div>
      </div>

      {/* Example prompts */}
      <div>
        <div style={{ fontSize: 11, color: t.textDim, fontWeight: 700, letterSpacing: 1, textTransform: 'uppercase', marginBottom: 8 }}>Contoh perintah</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {examples.map((ex, i) => (
            <div key={ex} style={{
              background: t.surface, borderRadius: 999, padding: '10px 14px',
              boxShadow: t.shadowSm, display: 'flex', alignItems: 'center', gap: 10,
              fontSize: 13,
            }}>
              <div style={{ width: 22, height: 22, borderRadius: '50%', background: `oklch(0.92 0.06 ${(i * 60 + 200) % 360})`, fontSize: 11, fontFamily: t.fontMono, color: t.text, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700 }}>{i + 1}</div>
              <span style={{ flex: 1 }}>"{ex}"</span>
              <span style={{ color: t.textMuted, fontSize: 16 }}>↗</span>
            </div>
          ))}
        </div>
      </div>

      <div style={{ display: 'flex', gap: 8, padding: '12px 14px', background: ccStateColor('PLANNING', { a: 0.10 }), borderRadius: t.radius, fontSize: 12, lineHeight: 1.5 }}>
        <span style={{ fontSize: 16 }}>💡</span>
        <span><b>Tip:</b> Bilang <i>"Hai Fuu"</i> kapan pun buat aktif dari layar mana aja.</span>
      </div>
    </div>
  );
}

// app.jsx — main canvas: 6 dashboards + 7 supporting screens, all wrapped
// in CCPhone, organized into DCSections. Tweaks panel controls global theme,
// state cycle, density, and accent.

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "dark": false,
  "accent": "rose",
  "density": "cozy",
  "state": "EXECUTING"
}/*EDITMODE-END*/;

function App() {
  const [tweaks, setTweak] = window.useTweaks(TWEAK_DEFAULTS);

  const accentHue = CC_ACCENTS[tweaks.accent]?.hue ?? 350;
  const t = React.useMemo(
    () => ({ ...ccTheme({ dark: tweaks.dark, accentHue, density: tweaks.density }), dark: tweaks.dark }),
    [tweaks.dark, accentHue, tweaks.density]
  );
  const dark = tweaks.dark;
  const state = tweaks.state;

  // Each dashboard / screen wrapped in <CCPhone>
  const phone = (Comp, extraProps = {}) => (
    <CCPhone theme={t} dark={dark} contentStyle={{ background: t.bg }}>
      <Comp t={t} dark={dark} state={state} {...extraProps} />
    </CCPhone>
  );

  const PHONE_W = 380, PHONE_H = 780;
  const ART_W = PHONE_W + 20, ART_H = PHONE_H + 20;

  return (
    <>
      <DesignCanvas>
        <DCSection id="dashboards" title="Dashboard" subtitle="6 variasi state-machine + execution log. Cycle state-nya dari Tweaks panel.">
          <DCArtboard id="v1" label="V1 · Soft Orb" width={ART_W} height={ART_H}>
            <ArtPad>{phone(DashV1)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="v2" label="V2 · Equalizer" width={ART_W} height={ART_H}>
            <ArtPad>{phone(DashV2)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="v3" label="V3 · Progress Ring" width={ART_W} height={ART_H}>
            <ArtPad>{phone(DashV3)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="v4" label="V4 · Pixel Heart" width={ART_W} height={ART_H}>
            <ArtPad>{phone(DashV4)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="v5" label="V5 · Constellation" width={ART_W} height={ART_H}>
            <ArtPad>{phone(DashV5)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="v6" label="V6 · Voice First" width={ART_W} height={ART_H}>
            <ArtPad>{phone(DashV6)}</ArtPad>
          </DCArtboard>
        </DCSection>

        <DCSection id="onboarding" title="Setup Wizard" subtitle="8-step onboarding flow — kunci momen ditampilkan">
          <DCArtboard id="setup-welcome" label="1 · Welcome" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenSetupWelcome)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="setup-download" label="2 · Download" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenSetupDownload)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="setup-test" label="3 · Test AI" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenSetup)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="setup-done" label="8 · Done" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenSetupDone)}</ArtPad>
          </DCArtboard>
        </DCSection>

        <DCSection id="settings" title="Settings & Editor Screens">
          <DCArtboard id="ai" label="AI Settings" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenAISettings)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="safety" label="Safety" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenSafety)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="skills" label="Skill Editor" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenSkills)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="persona" label="Persona · Fuu" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenPersona)}</ArtPad>
          </DCArtboard>
        </DCSection>

        <DCSection id="states" title="Critical states" subtitle="Approval modal, error recovery, empty state — bukan happy path">
          <DCArtboard id="approval" label="Approval modal" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenApproval)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="error" label="Error · recovery" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenError)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="empty" label="Empty · first run" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenEmpty)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="history" label="Task history" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenHistory)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="notif" label="Notif shade" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenNotification)}</ArtPad>
          </DCArtboard>
        </DCSection>

        <DCSection id="conversation" title="Conversational" subtitle="Chat / voice + always-on overlay">
          <DCArtboard id="chat" label="Chat / Voice" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenChat)}</ArtPad>
          </DCArtboard>
          <DCArtboard id="overlay-collapsed" label="Overlay · bubble" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenOverlay, { expanded: false })}</ArtPad>
          </DCArtboard>
          <DCArtboard id="overlay-expanded" label="Overlay · expanded" width={ART_W} height={ART_H}>
            <ArtPad>{phone(ScreenOverlay, { expanded: true })}</ArtPad>
          </DCArtboard>
        </DCSection>
      </DesignCanvas>

      <window.TweaksPanel title="Tweaks">
        <window.TweakSection title="Theme">
          <window.TweakRadio
            label="Mode"
            value={tweaks.dark ? 'dark' : 'light'}
            options={['light', 'dark']}
            onChange={(v) => setTweak('dark', v === 'dark')}
          />
          <window.TweakColor
            label="Accent"
            value={tweaks.accent}
            options={Object.keys(CC_ACCENTS).map((k) => ({ value: k, color: `oklch(0.72 0.11 ${CC_ACCENTS[k].hue})` }))}
            onChange={(v) => setTweak('accent', v)}
          />
          <window.TweakSelect
            label="Density"
            value={tweaks.density}
            options={['tight', 'cozy', 'roomy']}
            onChange={(v) => setTweak('density', v)}
          />
        </window.TweakSection>

        <window.TweakSection title="State machine" subtitle="Affects all dashboards">
          <window.TweakSelect
            label="Active state"
            value={tweaks.state}
            options={Object.keys(CC_STATES)}
            onChange={(v) => setTweak('state', v)}
          />
          <window.TweakButton onClick={() => {
            const keys = Object.keys(CC_STATES);
            const next = keys[(keys.indexOf(tweaks.state) + 1) % keys.length];
            setTweak('state', next);
          }}>Cycle next →</window.TweakButton>
        </window.TweakSection>
      </window.TweaksPanel>
    </>
  );
}

// ArtPad — centers a phone within its artboard with a soft canvas backdrop
function ArtPad({ children }) {
  return (
    <div style={{
      width: '100%', height: '100%',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'oklch(0.97 0.005 70)',
    }}>{children}</div>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);

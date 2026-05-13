# 07 — Cost Analysis

**Audience**: Founder, financial decision maker
**Last updated**: 2026-05-10
**Currency**: USD primary, IDR untuk konteks Indonesia (asumsi $1 = Rp 16,000)

---

## Reading This Doc

3 perspektif cost:
1. **Development cost** — total effort + tools untuk build v4
2. **Operational cost** — infrastructure ongoing kalau publish
3. **User cost** — apa yang user bayar (BYOK = user yang pegang)

---

## 1. Development Cost

### 1.1 Time investment (jika value time)

| Phase | Hours (est) | @$50/hour (Indonesia rate) | @$25/hour (developer self) |
|-------|-------------|----------------------------|----------------------------|
| Phase 1: Foundation | 80 | $4,000 | $2,000 |
| Phase 2: Voice | 80 | $4,000 | $2,000 |
| Phase 3: Vision + Polish | 80 | $4,000 | $2,000 |
| Beta + Stabilization | 80 | $4,000 | $2,000 |
| **Total** | **320h** | **$16,000 (~Rp 256M)** | **$8,000 (~Rp 128M)** |

Untuk personal project, time invested = "free" tapi opportunity cost nyata.

### 1.2 Tooling & subscriptions

#### Free tier sufficient (Phase 1-3)
- GitHub (private repos free)
- Android Studio
- Gradle
- Open source libraries (LiteRT-LM, openWakeWord, Whisper.cpp, Piper, Silero VAD)
- Hugging Face (model downloads free)

#### Paid (optional / dev convenience)
- **Claude Code subscription** (kalau pakai untuk dev assistance): $20/bulan Pro tier. Bisa ChatGPT Plus alternatif.
- **JetBrains All Products Pack**: $649/year (kalau IntelliJ Ultimate diperlukan; gratis untuk Android Studio cuma).
- **Picovoice** (kalau switch dari openWakeWord ke Porcupine): $99/year commercial.

**Recommended for v4 dev**: $20/bulan dev assistant subscription. Total: ~$80 untuk 4 bulan dev.

### 1.3 Spike + experiment cost

| Spike | What it costs |
|-------|---------------|
| MediaPipe vs LiteRT (Week 1) | $0 (both free) |
| Whisper local vs cloud (Week 5) | ~$5 untuk testing OpenAI Whisper API + Deepgram credits |
| Piper vs ElevenLabs (Week 6) | ~$10 untuk ElevenLabs trial + OpenAI TTS testing |
| Embedding model (Week 9) | $0 (both free open source) |
| **Total spike testing** | **~$15** |

### 1.4 Beta tester compensation (optional)

Kalau bayar tester:
- 5 tester × Rp 100,000 = Rp 500,000 (~$31)
- Atau gift card Steam/PlayStation ~$10 each = $50

Kalau personal network: $0.

### 1.5 Development total

**Cash out (4 bulan)**: $50-150 (subscription + spikes + tester gifts)
**Time investment**: 320h ($8k-16k opportunity cost depending on rate)

**Bottom line**: Cash budget < $200 untuk solo dev v4.

---

## 2. Operational Cost (Hypothetical Publish)

### 2.1 Infrastructure (kalau publish)

#### v4 model: BYOK = zero infrastructure cost

ChibiClaw v4 design = **Bring Your Own Key**:
- User input own API key untuk Claude/GPT/Gemini
- ChibiClaw tidak proxy
- ChibiClaw tidak hosting model
- Model download dari Hugging Face (free, atau user host sendiri)

**Cost ChibiClaw side**: **$0/month** untuk hosting.

#### Hosting kalau eventual offer hosted tier (deferred)
- Cloud LLM cost: per user usage
- App distribution: Play Store one-time fee $25
- Website: $5/month basic hosting (Vercel free tier)
- Domain: $10-15/year

### 2.2 Compliance + legal (kalau publish enterprise)

| Item | Estimate |
|------|----------|
| UU PDP DPO consultation (1 hour Indonesian privacy lawyer) | $200-500 |
| Privacy policy template + customization | $100-300 |
| Terms of Service drafting | $200-500 |
| Trademark search Indonesia (DJKI) | $50 self-search atau $200 lawyer-assisted |
| Trademark registration (kalau pursue) | $100-300 |

**Total legal pre-publish**: $500-1,500 one-time. Defer until traction confirmed.

### 2.3 Marketing + community (optional)

Kalau personal project: $0.
Kalau publish:
- Discord/Telegram community: free
- Landing page: $10/month
- Social media ads experiment: $50-200 trial budget
- Content (blog posts, tutorials): time only

---

## 3. User Cost Breakdown

### 3.1 First install (one-time)

| Item | Cost user |
|------|-----------|
| APK download (kalau dari GitHub) | Quota internet ~100MB |
| Gemma 4 E2B model download | Quota internet ~1.6GB |
| Whisper STT model download | Quota internet ~250MB |
| TTS voice download (id-fajri) | Quota internet ~60MB |
| **Total first download** | **~2GB quota** |

Indonesia mobile data cost ~Rp 50/MB on prepaid (varies provider):
- Telkomsel: ~Rp 50/MB (depends on package)
- Indosat: ~Rp 30-50/MB
- XL: ~Rp 30-40/MB
- Smartfren: ~Rp 20-30/MB

Estimated user cost first install: **Rp 40,000 - 100,000** (~$2.50-6.00) data only.

**Mitigation**: WiFi-only download default. Show data usage estimate.

### 3.2 Ongoing usage (offline mode)

**Cost**: $0 / Rp 0
- All inference local
- No telemetry
- Battery drain ~5-10%/hour active use

### 3.3 Ongoing usage (cloud mode)

User bring own API key. Cost varies per provider per session length.

#### Anthropic Claude Sonnet 4.6 ($3/M input, $15/M output)
Asumsi typical command:
- System prompt: ~1,500 tokens (instructions + tools + context)
- User input: ~50 tokens
- Tool calls + responses: ~500 tokens (3 tool calls average)
- Final response: ~100 tokens
- Total: ~1,500 input + ~600 output per turn

Cost per turn:
- Input: 1,500 × $3/1M = $0.0045
- Output: 600 × $15/1M = $0.009
- **Total: ~$0.0135 per turn (~Rp 220)**

Asumsi 50 cloud commands/day:
- Daily: 50 × $0.0135 = $0.675 (~Rp 11,000)
- Monthly: ~$20 (~Rp 320,000)

#### OpenAI GPT-4o-mini ($0.15/M input, $0.60/M output) — **CHEAPEST**
Same per-turn ratio:
- Input: 1,500 × $0.15/1M = $0.000225
- Output: 600 × $0.60/1M = $0.00036
- **Total: ~$0.00059 per turn (~Rp 9.5)**

Monthly 50/day:
- Daily: $0.029 (~Rp 470)
- Monthly: ~$0.88 (~Rp 14,000)

**Significantly cheaper**, lower quality but acceptable untuk simple commands.

#### Google Gemini 2.5 Flash ($0.30/M input, $2.50/M output)
- Input: 1,500 × $0.30/1M = $0.00045
- Output: 600 × $2.50/1M = $0.0015
- **Total: ~$0.002 per turn (~Rp 32)**

Monthly 50/day:
- Daily: $0.10 (~Rp 1,600)
- Monthly: ~$3 (~Rp 50,000)

### 3.4 Cost comparison side-by-side

| Cloud Provider | Per turn | 50/day monthly | Quality | Tool calling reliability |
|----------------|----------|----------------|---------|--------------------------|
| Gemma local | Free | Free | Good | OK (parser bug) |
| GPT-4o-mini | $0.0006 | ~$1 / Rp 14k | Good | Excellent |
| Gemini 2.5 Flash | $0.002 | ~$3 / Rp 50k | Very Good | Excellent |
| Claude Sonnet 4.6 | $0.014 | ~$20 / Rp 320k | Excellent | Best (SOTA) |
| Claude Opus 4.7 | ~$0.040 | ~$60 / Rp 960k | Best | Best |

**Recommended user budget guidance** (di docs/onboarding):
- Hobbyist / personal: **Gemma local default**, occasional cloud → ~$2/month worst case
- Power user: **GPT-4o-mini default**, Claude untuk task kompleks → $5-15/month
- Heavy user (cloud first): Claude Sonnet → $20-50/month

### 3.5 Cost per use case scenarios

#### Scenario A: Hands-free driver
- 30 voice commands/day (alarm, music, message)
- 70% simple → cheap model OK
- Monthly: ~$1-3 (cloud) or $0 (offline)

#### Scenario B: Power user automation enthusiast
- 100 commands/day, 30% complex multi-step
- Mix of models depending on complexity
- Monthly: $10-25

#### Scenario C: Voice-first lansia / disabled
- 50 commands/day, mostly simple (call, message, basic app open)
- Offline mode cukup, cloud opt-in untuk task kompleks
- Monthly: $0-5

---

## 4. Token Budget Optimization

### 4.1 System prompt optimization
- Current draft: ~1,500 tokens (system instruction + tool descriptions + few-shot)
- Optimize: cut redundant phrasing, abbreviate tool descriptions
- Target: ~800 tokens
- Saving: ~50% input cost

### 4.2 Conversation history pruning
- Current: last 5 turns
- Smarter: semantic relevance pruning (keep relevant turns, drop irrelevant)
- Implement with MemoryStore (Phase 3)

### 4.3 Tool result compression
- Tool result yang panjang (e.g., file listing 100 files) → summarize sebelum send back ke LLM
- Or: paginate (kasih first 10, "more available")

### 4.4 Caching prompt
- Anthropic + OpenAI sekarang support prompt caching (cache system prompt, charge cuma diff)
- Up to 90% cost reduction untuk repeated calls
- Implement Phase 1 Week 4

### 4.5 Streaming response with early stop
- Detect "task complete" early, cancel remaining stream
- Save output tokens

---

## 5. Cost Display & Alerts (CostMeter)

### 5.1 Daily cost dashboard

```
Settings → Cloud Usage

Today (10 May 2026):
  Anthropic Claude   $1.20  (89 calls)
  OpenAI GPT-4o      $0.32  (45 calls)
  Total             $1.52  (~Rp 24,300)

This Month (May 2026):
  Total             $14.80  (~Rp 237,000)
  Budget alert      $20/month
  
[bar chart 30 days]
```

### 5.2 Budget alerts
- User set monthly budget (default $10)
- 80% reached → notification "Sudah pakai $8 dari budget $10 bulan ini"
- 100% reached → switch to offline auto, kasih notif

### 5.3 Per-command cost preview (optional)
Kalau user sangat cost-conscious, opt-in: sebelum eksekusi cloud, show estimate "Akan biaya ~$0.01"

---

## 6. ROI Analysis (jika businessify)

### 6.1 Hypothetical pricing tiers

(Speculative — current ChibiClaw v4 plan = personal/free)

| Tier | Price | Target user | What they get |
|------|-------|-------------|---------------|
| Free | $0 | All | Offline only, manual API key for cloud |
| Pro | $5/month | Power user | Premium voice (cloud TTS), advanced memory, priority bug fix |
| Enterprise | Custom | B2B | SSO, audit log retention, on-premise option |

### 6.2 Break-even analysis

Asumsi:
- Cost per Pro user: ~$1/month (cloud TTS quota)
- Net per Pro user: $4/month profit
- Break-even (pay back $8k dev cost): 2,000 Pro user-month

Realistic timeline 3 tahun: cumulative 3000-5000 Pro user-month → break-even Year 2-3.

**Verdict untuk product**: feasibility OK kalau willing 2-year horizon. Personal project: skip ROI.

---

## 7. Cost Risks (cross-reference [06-risk-register.md](06-risk-register.md))

- **TR-04** Model download abandonment → user data cost tinggi → mitigate dengan small model default
- **OR-01** Cloud cost runaway → mitigated by BYOK model
- **MR-01** Cloud price increase by vendor → adapter pattern allows switch
- **PR-01** No users → all dev cost wasted → validate niche pre-Phase 2

---

## 8. Recommendations

### Untuk personal project (default)
- **Cash budget**: $50-100 untuk 4 bulan dev (Claude Code Pro subscription + spike testing)
- **Cloud usage**: < $5/month dengan Gemma default + occasional Claude
- **Total spend Year 1**: $100-200 max
- **Skip**: lawyer, marketing, paid tester

### Untuk eventual product (deferred)
- **Pre-launch**: $1,500 (legal + trademark + landing page)
- **Year 1 ops**: $1,000-3,000 (community + marketing experiment)
- **Need**: 200+ Pro user/month untuk sustainable

### Strategic recommendation
Personal project dulu 6 bulan, validate retention. Kalau ChibiClaw beneran kamu pakai daily dan 5-10 user juga retention tinggi → consider productize Year 2.

---

**Next**: [08-success-metrics.md](08-success-metrics.md) — KPIs.

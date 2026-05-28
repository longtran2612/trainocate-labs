/* eslint-disable react/prop-types */

// ─── Color palette ───────────────────────────────────────────────────────────
const C = {
  fe:      { bg: '#dbeafe', border: '#3b82f6', text: '#1d4ed8' },
  gateway: { bg: '#ede9fe', border: '#7c3aed', text: '#5b21b6' },
  tx:      { bg: '#dcfce7', border: '#16a34a', text: '#166534' },
  kyc:     { bg: '#fef9c3', border: '#ca8a04', text: '#854d0e' },
  limit:   { bg: '#ffedd5', border: '#ea580c', text: '#9a3412' },
  account: { bg: '#cffafe', border: '#0891b2', text: '#0e7490' },
  auth:    { bg: '#f0fdf4', border: '#22c55e', text: '#15803d' },
  ext:     { bg: '#fce7f3', border: '#db2777', text: '#9d174d' },
  napas:   { bg: '#f3e8ff', border: '#9333ea', text: '#6b21a8' },
  db:      { bg: '#fee2e2', border: '#dc2626', text: '#991b1b' },
  kafka:   { bg: '#1c1917', border: '#78716c', text: '#e7e5e4' },
  redis:   { bg: '#fff1f2', border: '#e11d48', text: '#9f1239' },
  debez:   { bg: '#ecfdf5', border: '#059669', text: '#065f46' },
  saga:    { bg: '#f0f9ff', border: '#0284c7', text: '#0c4a6e' },
  comp:    { bg: '#fff7ed', border: '#f97316', text: '#7c2d12' },
  bonus:   { bg: '#fefce8', border: '#eab308', text: '#854d0e' },
};

function Box({ label, sub, color, step, wide, pill }) {
  const c = C[color] || C.tx;
  return (
    <div style={{
      display: 'inline-flex', flexDirection: 'column', alignItems: 'center',
      justifyContent: 'center', textAlign: 'center',
      background: c.bg, border: `2px solid ${c.border}`, color: c.text,
      borderRadius: pill ? 99 : 10,
      padding: wide ? '10px 20px' : '8px 14px',
      minWidth: wide ? 160 : 110,
      fontSize: 13, fontWeight: 600,
      position: 'relative',
      boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
      gap: 2,
    }}>
      {step && (
        <span style={{
          position: 'absolute', top: -10, left: '50%', transform: 'translateX(-50%)',
          background: c.border, color: '#fff', borderRadius: 99,
          fontSize: 10, fontWeight: 700, padding: '1px 7px',
          whiteSpace: 'nowrap',
        }}>{step}</span>
      )}
      <span>{label}</span>
      {sub && <span style={{ fontSize: 10, opacity: 0.75, fontWeight: 400 }}>{sub}</span>}
    </div>
  );
}

function Arrow({ dir = 'right', label, color = '#6b7280', double: isDouble }) {
  const isV = dir === 'down' || dir === 'up';
  const isLeft = dir === 'left';
  const tip = { right: '→', left: '←', down: '↓', up: '↑' }[dir];
  return (
    <div style={{
      display: 'flex', flexDirection: isV ? 'column' : 'row',
      alignItems: 'center', justifyContent: 'center',
      gap: 2,
      margin: isV ? '2px auto' : '0 2px',
    }}>
      {isLeft && <span style={{ color, fontSize: 18, lineHeight: 1 }}>{tip}</span>}
      <div style={{
        background: color,
        width: isV ? 2 : label ? undefined : 28,
        height: isV ? (label ? undefined : 28) : 2,
        minWidth: isV ? 2 : label ? undefined : undefined,
        minHeight: isV ? undefined : undefined,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        {label && (
          <span style={{
            fontSize: 9, color: '#fff', background: color,
            borderRadius: 4, padding: '1px 5px', whiteSpace: 'nowrap',
          }}>{label}</span>
        )}
      </div>
      {!isLeft && <span style={{ color, fontSize: 18, lineHeight: 1 }}>{tip}</span>}
    </div>
  );
}

function SectionTitle({ icon, title, sub }) {
  return (
    <div style={{ marginBottom: 24 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <span style={{ fontSize: 22 }}>{icon}</span>
        <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700, color: '#1e293b' }}>{title}</h3>
      </div>
      {sub && <p style={{ margin: '4px 0 0 32px', fontSize: 12, color: '#64748b' }}>{sub}</p>}
    </div>
  );
}

function FlowRow({ children, gap = 6 }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap, marginBottom: 8 }}>
      {children}
    </div>
  );
}

function Note({ color = '#f59e0b', children }) {
  return (
    <div style={{
      background: color + '18', border: `1px solid ${color}`,
      borderRadius: 8, padding: '6px 12px', fontSize: 11,
      color: '#374151', marginTop: 10,
    }}>
      {children}
    </div>
  );
}

function Card({ children, style }) {
  return (
    <div style={{
      background: '#fff', borderRadius: 14,
      boxShadow: '0 2px 16px rgba(0,0,0,0.08)',
      padding: '28px 32px', marginBottom: 28,
      ...style,
    }}>
      {children}
    </div>
  );
}

// ─── Onboard Flow ────────────────────────────────────────────────────────────
function OnboardFlow() {
  const steps = [
    {
      num: '①', label: 'Register User',      sub: 'auth-service',     color: 'auth',
      detail: 'POST /api/v1/auth/register', result: '→ userId',
    },
    {
      num: '②', label: 'Open Bank Account',  sub: 'account-service',  color: 'account',
      detail: 'POST /api/v1/accounts/create', result: '→ accountNo',
    },
    {
      num: '③', label: 'Setup Credentials',  sub: 'auth-service',     color: 'auth',
      detail: 'update username = accountNo', result: '→ login with accountNo',
    },
    {
      num: '④', label: 'KYC Verification',   sub: 'kyc-service',      color: 'kyc',
      detail: 'POST /api/v1/kyc/verify', result: '→ status: VERIFIED',
    },
    {
      num: '⑤', label: 'Init Limits',        sub: 'limit-service',    color: 'limit',
      detail: 'POST /api/v1/limits/init', result: '→ TIER 1 limits',
    },
    {
      num: '⑥', label: 'Welcome Bonus',      sub: 'account-service',  color: 'bonus',
      detail: 'credit 10,000,000 VND', result: '→ balance seeded',
    },
  ];

  return (
    <div>
      {/* Entry */}
      <FlowRow>
        <Box label="User fills form" sub="fullName, phone, email, idNumber, password" color="fe" wide />
        <Arrow dir="right" label="submit" color="#3b82f6" />
        <Box label="OnboardPage.jsx" sub="sequential API calls" color="fe" />
        <Arrow dir="right" label="Kong :30000" color="#7c3aed" />
        <Box label="Kong Gateway" color="gateway" />
      </FlowRow>

      {/* 6 steps vertical with connecting lines */}
      <div style={{ marginTop: 20 }}>
        {steps.map((s, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: 0, marginBottom: 0 }}>
            {/* Left: step number connector */}
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 32, flexShrink: 0 }}>
              <div style={{
                width: 28, height: 28, borderRadius: '50%',
                background: C[s.color].border, color: '#fff',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 13, fontWeight: 700, flexShrink: 0,
                boxShadow: '0 2px 6px rgba(0,0,0,0.15)',
              }}>{i + 1}</div>
              {i < steps.length - 1 && (
                <div style={{ width: 2, flex: 1, minHeight: 24, background: '#e2e8f0', marginTop: 2, marginBottom: 2 }} />
              )}
            </div>

            {/* Right: step content */}
            <div style={{
              flex: 1, display: 'flex', alignItems: 'center', gap: 10,
              paddingLeft: 12, paddingBottom: i < steps.length - 1 ? 16 : 0,
              paddingTop: 2,
            }}>
              <div style={{
                background: C[s.color].bg, border: `2px solid ${C[s.color].border}`,
                borderRadius: 10, padding: '8px 16px', minWidth: 160,
              }}>
                <div style={{ fontSize: 13, fontWeight: 700, color: C[s.color].text }}>{s.label}</div>
                <div style={{ fontSize: 10, color: C[s.color].border, fontFamily: 'monospace', marginTop: 2 }}>{s.sub}</div>
              </div>

              <Arrow dir="right" color="#94a3b8" />

              <div style={{
                background: '#f8fafc', border: '1px solid #e2e8f0',
                borderRadius: 8, padding: '6px 12px',
              }}>
                <div style={{ fontSize: 11, color: '#475569', fontFamily: 'monospace' }}>{s.detail}</div>
                <div style={{ fontSize: 11, color: '#16a34a', fontWeight: 600, marginTop: 2 }}>{s.result}</div>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Result */}
      <div style={{ marginTop: 16 }}>
        <FlowRow>
          <div style={{
            background: 'linear-gradient(135deg, #dcfce7, #dbeafe)',
            border: '2px solid #16a34a', borderRadius: 12,
            padding: '12px 20px', display: 'flex', alignItems: 'center', gap: 12,
          }}>
            <span style={{ fontSize: 22 }}>🎉</span>
            <div>
              <div style={{ fontSize: 14, fontWeight: 700, color: '#166534' }}>Account Ready!</div>
              <div style={{ fontSize: 11, color: '#475569', marginTop: 2 }}>
                accountNo issued · KYC VERIFIED · TIER 1 limits · 10,000,000 VND welcome bonus
              </div>
            </div>
          </div>
          <Arrow dir="right" color="#16a34a" />
          <Box label="Login with accountNo" sub="→ /login" color="auth" />
        </FlowRow>
      </div>

      <Note color="#22c55e">
        🔗 <strong>Sequential orchestration</strong> — không dùng Saga vì onboarding chạy từ FE (không distributed).
        Nếu bất kỳ bước nào fail, FE hiển thị lỗi tại bước đó và cho phép retry. userId được dùng làm
        foreign key xuyên suốt Auth → Account → KYC → Limit.
      </Note>
    </div>
  );
}

// ─── Saga Steps grid ─────────────────────────────────────────────────────────
function SagaSteps() {
  const steps = [
    { step: '①', label: 'KYC Check',      sub: 'kyc-service',     color: 'kyc',     compensable: false },
    { step: '②', label: 'Limit Check',    sub: 'limit-service',   color: 'limit',   compensable: false },
    { step: '③', label: 'Balance Check',  sub: 'account-service', color: 'account', compensable: false },
    { step: '④', label: 'Debit Sender',   sub: 'account-service', color: 'account', compensable: true  },
    { step: '⑤', label: 'Consume Limit',  sub: 'limit-service',   color: 'limit',   compensable: true  },
    { step: '⑥', label: 'Credit Receiver',sub: 'account-service', color: 'account', compensable: false },
  ];

  return (
    <div>
      {/* Step row */}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'flex-start' }}>
        {steps.map((s, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
              <Box label={s.label} sub={s.sub} color={s.color} step={s.step} />
              {s.compensable && (
                <span style={{
                  fontSize: 9, background: '#fff7ed', border: '1px solid #f97316',
                  color: '#c2410c', borderRadius: 99, padding: '1px 6px',
                }}>compensable</span>
              )}
            </div>
            {i < steps.length - 1 && (
              <Arrow dir="right" color="#94a3b8" />
            )}
          </div>
        ))}
      </div>

      {/* Compensation row */}
      <div style={{
        marginTop: 16, padding: '10px 14px',
        background: '#fff7ed', border: '1px dashed #f97316',
        borderRadius: 8, display: 'flex', alignItems: 'center', gap: 10,
      }}>
        <span style={{ fontSize: 14 }}>🔄</span>
        <span style={{ fontSize: 12, color: '#9a3412', fontWeight: 600 }}>
          Compensation (reverse order on failure after step ④):
        </span>
        <Box label="Release Limit" sub="limit-service" color="comp" />
        <Arrow dir="right" color="#f97316" />
        <Box label="Refund Sender" sub="account-service (credit back)" color="comp" />
        <Arrow dir="right" color="#f97316" />
        <Box label="TX: FAILED" color="db" />
      </div>
    </div>
  );
}

// ─── System Architecture Diagram ─────────────────────────────────────────────
function LayerLabel({ color, children }) {
  return (
    <div style={{
      writingMode: 'vertical-rl', textOrientation: 'mixed', transform: 'rotate(180deg)',
      fontSize: 10, fontWeight: 700, letterSpacing: 1.5, textTransform: 'uppercase',
      color, padding: '8px 4px', userSelect: 'none', opacity: 0.7,
      whiteSpace: 'nowrap',
    }}>{children}</div>
  );
}

function SvcBox({ label, port, sub, color, width }) {
  const c = C[color] || C.tx;
  return (
    <div style={{
      background: c.bg, border: `2px solid ${c.border}`, color: c.text,
      borderRadius: 10, padding: '8px 12px', textAlign: 'center',
      width: width || 120, flexShrink: 0,
      boxShadow: '0 2px 6px rgba(0,0,0,0.07)',
    }}>
      <div style={{ fontSize: 12, fontWeight: 700, lineHeight: 1.3 }}>{label}</div>
      {port && <div style={{ fontSize: 10, fontFamily: 'monospace', color: c.border, marginTop: 2 }}>{port}</div>}
      {sub  && <div style={{ fontSize: 10, color: '#64748b', marginTop: 2 }}>{sub}</div>}
    </div>
  );
}

function LayerRow({ label, labelColor, bgColor, borderColor, children, minH }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'stretch',
      background: bgColor || '#f8fafc',
      border: `1.5px solid ${borderColor || '#e2e8f0'}`,
      borderRadius: 12, overflow: 'hidden',
      marginBottom: 4,
      minHeight: minH || 'auto',
    }}>
      {/* Layer label strip */}
      <div style={{
        background: borderColor || '#e2e8f0', opacity: 0.85,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: '0 6px', flexShrink: 0,
      }}>
        <LayerLabel color="#fff">{label}</LayerLabel>
      </div>
      {/* Content */}
      <div style={{ flex: 1, padding: '16px 20px' }}>
        {children}
      </div>
    </div>
  );
}

function ConnArrow({ label, color = '#94a3b8' }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      gap: 6, margin: '4px 0',
    }}>
      <div style={{ width: 2, height: 16, background: color, margin: '0 auto' }} />
      <div style={{
        fontSize: 9, color: '#fff', background: color,
        borderRadius: 4, padding: '1px 8px', whiteSpace: 'nowrap',
      }}>{label}</div>
      <div style={{ fontSize: 14, color, lineHeight: 1 }}>↓</div>
    </div>
  );
}

function Chip({ children, color }) {
  const c = C[color] || C.tx;
  return (
    <span style={{
      background: c.bg, border: `1px solid ${c.border}`, color: c.text,
      borderRadius: 6, padding: '2px 8px', fontSize: 10, fontWeight: 600,
      whiteSpace: 'nowrap',
    }}>{children}</span>
  );
}

function SystemArchitectureDiagram() {
  return (
    <div style={{ fontFamily: 'inherit' }}>

      {/* ── Layer 0: User ── */}
      <LayerRow label="Client" labelColor="#1d4ed8" bgColor="#eff6ff" borderColor="#3b82f6">
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <div style={{ fontSize: 28 }}>🌐</div>
          <div>
            <div style={{ fontWeight: 700, color: '#1d4ed8', fontSize: 13 }}>Web Browser</div>
            <div style={{ fontSize: 11, color: '#64748b' }}>Customer accesses VikkiBank via browser</div>
          </div>
          <div style={{ flex: 1 }} />
          <SvcBox label="Frontend" port=":30080" sub="React + Nginx (NodePort)" color="fe" width={150} />
        </div>
      </LayerRow>

      <div style={{ display: 'flex', justifyContent: 'center' }}>
        <ConnArrow label="HTTP — nginx proxy_pass → kong:8000" color="#7c3aed" />
      </div>

      {/* ── Layer 1: API Gateway ── */}
      <LayerRow label="API Gateway" labelColor="#5b21b6" bgColor="#faf5ff" borderColor="#7c3aed">
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
          <SvcBox label="Kong Gateway" port=":30000 (proxy)" sub="DB-less · KRaft" color="gateway" width={160} />
          <div style={{ fontSize: 11, color: '#64748b', maxWidth: 340 }}>
            <div style={{ fontWeight: 600, marginBottom: 4 }}>Routes configured:</div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
              {[
                '/api/v1/auth → :8081',
                '/api/v1/accounts → :8082',
                '/api/v1/kyc → :8083',
                '/api/v1/limits → :8084',
                '/api/v1/transactions → :8085',
              ].map(r => <Chip key={r} color="gateway">{r}</Chip>)}
            </div>
          </div>
          <div style={{ flex: 1 }} />
          <div style={{ fontSize: 11, color: '#64748b', textAlign: 'right' }}>
            <div>Admin UI: <span style={{ fontFamily: 'monospace' }}>:30001</span></div>
          </div>
        </div>
      </LayerRow>

      <div style={{ display: 'flex', justifyContent: 'center' }}>
        <ConnArrow label="Feign HTTP — Spring Cloud OpenFeign" color="#16a34a" />
      </div>

      {/* ── Layer 2: Core Microservices ── */}
      <LayerRow label="Microservices" labelColor="#166534" bgColor="#f0fdf4" borderColor="#16a34a" minH={120}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'flex-start' }}>
          <SvcBox label="Auth Service"    port=":8081" sub="JWT · BCrypt" color="auth" />
          <SvcBox label="Account Service" port=":8082" sub="CQRS · Redis" color="account" />
          <SvcBox label="KYC Service"     port=":8083" sub="Identity" color="kyc" />
          <SvcBox label="Limit Service"   port=":8084" sub="Transfer rules" color="limit" />
          <SvcBox label="Transaction Svc" port=":8085" sub="Saga Orchestrator" color="tx" />
        </div>

        {/* Sub-services called by Transaction */}
        <div style={{
          marginTop: 12, paddingTop: 10,
          borderTop: '1px dashed #86efac',
          display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap',
        }}>
          <span style={{ fontSize: 10, color: '#16a34a', fontWeight: 600 }}>
            ↳ called by Transaction Svc via Feign:
          </span>
          <SvcBox label="Internal Transfer" port=":8086" sub="VikkiBank routing" color="tx" width={140} />
          <div style={{ fontSize: 13, color: '#94a3b8' }}>→</div>
          <SvcBox label="External Transfer" port=":8087" sub="NAPAS integration" color="ext" width={140} />
          <div style={{ fontSize: 13, color: '#94a3b8' }}>→</div>
          <SvcBox label="NAPAS Simulator"   port=":8088" sub="Interbank gateway" color="napas" width={140} />
        </div>
      </LayerRow>

      <div style={{ display: 'flex', justifyContent: 'center' }}>
        <ConnArrow label="JDBC / Spring Data JPA — each service owns its DB" color="#dc2626" />
      </div>

      {/* ── Layer 3: Data ── */}
      <LayerRow label="Data Layer" labelColor="#991b1b" bgColor="#fff5f5" borderColor="#dc2626">
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'flex-start' }}>
          {/* PostgreSQL instances */}
          <div style={{
            background: '#fee2e2', border: '2px solid #dc2626',
            borderRadius: 10, padding: '10px 14px',
          }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: '#991b1b', marginBottom: 6 }}>
              🐘 PostgreSQL :5432
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
              {[
                ['auth',         'auth-service'],
                ['account_db',   'account-service'],
                ['kyc_db',       'kyc-service'],
                ['limit_db',     'limit-service'],
                ['transaction_db','transaction-service'],
              ].map(([db, svc]) => (
                <div key={db} style={{
                  background: '#fff', border: '1px solid #fca5a5',
                  borderRadius: 6, padding: '3px 8px', textAlign: 'center',
                }}>
                  <div style={{ fontSize: 11, fontWeight: 600, color: '#991b1b', fontFamily: 'monospace' }}>{db}</div>
                  <div style={{ fontSize: 9, color: '#64748b' }}>{svc}</div>
                </div>
              ))}
            </div>
            <div style={{ fontSize: 9, color: '#dc2626', marginTop: 6 }}>
              WAL level=logical · max_replication_slots=4
            </div>
          </div>

          {/* Redis */}
          <div style={{
            background: '#fff1f2', border: '2px solid #e11d48',
            borderRadius: 10, padding: '10px 14px', minWidth: 130,
          }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: '#9f1239', marginBottom: 6 }}>
              🔴 Redis :6379
            </div>
            <div style={{ fontSize: 10, color: '#64748b' }}>CQRS Read Model</div>
            <div style={{ fontSize: 10, fontFamily: 'monospace', color: '#e11d48', marginTop: 4 }}>
              account:{'{accountNo}'}
            </div>
            <div style={{ fontSize: 9, color: '#64748b', marginTop: 4 }}>
              balance · fullName · status · version
            </div>
          </div>
        </div>
      </LayerRow>

      <div style={{ display: 'flex', justifyContent: 'center' }}>
        <ConnArrow label="CDC — PostgreSQL WAL → Debezium → Kafka → Consumer → Redis" color="#059669" />
      </div>

      {/* ── Layer 4: Event Streaming ── */}
      <LayerRow label="Event Stream" labelColor="#065f46" bgColor="#f0fdf4" borderColor="#059669">
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <SvcBox label="Debezium Connect" port=":8083" sub="pgoutput plugin · ExtractNewRecordState SMT" color="debez" width={200} />
          <div style={{ fontSize: 18, color: '#059669' }}>→</div>
          <SvcBox label="Kafka (KRaft)" port=":9092" sub="account_db.public.accounts" color="kafka" width={170} />
          <div style={{ fontSize: 18, color: '#059669' }}>→</div>
          <div style={{
            background: '#cffafe', border: '2px solid #0891b2',
            borderRadius: 10, padding: '8px 12px', textAlign: 'center', width: 170,
          }}>
            <div style={{ fontSize: 12, fontWeight: 700, color: '#0e7490' }}>Account Consumer</div>
            <div style={{ fontSize: 10, color: '#0891b2', fontFamily: 'monospace', marginTop: 2 }}>@KafkaListener</div>
            <div style={{ fontSize: 10, color: '#64748b', marginTop: 2 }}>idempotency · ack after write</div>
          </div>
          <div style={{ fontSize: 18, color: '#e11d48' }}>→</div>
          <SvcBox label="Redis" port=":6379" sub="Read model synced" color="redis" />
        </div>
      </LayerRow>

      {/* ── Deployment note ── */}
      <div style={{
        marginTop: 14, padding: '10px 16px',
        background: '#f1f5f9', border: '1px solid #cbd5e1',
        borderRadius: 10, display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center',
      }}>
        <span style={{ fontSize: 13, fontWeight: 700, color: '#334155' }}>☸️ Kubernetes (Docker Desktop)</span>
        <span style={{ fontSize: 11, color: '#64748b' }}>namespace: money-transfer</span>
        <span style={{ fontSize: 11, color: '#64748b' }}>•</span>
        <span style={{ fontSize: 11, color: '#64748b' }}>Helm chart deployment</span>
        <span style={{ fontSize: 11, color: '#64748b' }}>•</span>
        <span style={{ fontSize: 11, color: '#64748b' }}>All services: ClusterIP (internal)</span>
        <span style={{ fontSize: 11, color: '#64748b' }}>•</span>
        <span style={{ fontSize: 11, color: '#64748b' }}>Kong + Frontend: NodePort (external)</span>
        <span style={{ fontSize: 11, color: '#64748b' }}>•</span>
        <span style={{ fontSize: 11, color: '#64748b' }}>Init containers wait for PostgreSQL</span>
      </div>
    </div>
  );
}

// ─── Main Page ────────────────────────────────────────────────────────────────
export default function ArchitecturePage() {
  return (
    <div className="page" style={{ maxWidth: 1100, paddingBottom: 40 }}>
      <div className="page-header" style={{ marginBottom: 28 }}>
        <h2 style={{ margin: 0 }}>System Architecture &amp; Flow Diagrams</h2>
        <span className="text-muted">VikkiBank — Money Transfer Platform</span>
      </div>

      {/* ═══════════════════════════════════════════════════════════════
          SYSTEM ARCHITECTURE OVERVIEW
      ═══════════════════════════════════════════════════════════════ */}
      <Card>
        <SectionTitle
          icon="🏗️"
          title="System Architecture Overview"
          sub="Layered view — Client · API Gateway · Microservices · Data · Event Streaming · Kubernetes"
        />
        <SystemArchitectureDiagram />
      </Card>

      {/* ── Legend ─────────────────────────────────────────────────── */}
      <div style={{
        display: 'flex', flexWrap: 'wrap', gap: 8,
        marginBottom: 28, padding: '12px 16px',
        background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 10,
      }}>
        <span style={{ fontSize: 12, color: '#64748b', fontWeight: 600, alignSelf: 'center' }}>Legend:</span>
        {[
          ['fe', 'Frontend'],
          ['gateway', 'API Gateway'],
          ['auth', 'Auth Svc'],
          ['tx', 'Transaction Svc'],
          ['kyc', 'KYC Svc'],
          ['limit', 'Limit Svc'],
          ['account', 'Account Svc'],
          ['ext', 'Ext. Transfer Svc'],
          ['napas', 'NAPAS'],
          ['db', 'PostgreSQL'],
          ['kafka', 'Kafka'],
          ['redis', 'Redis'],
          ['debez', 'Debezium'],
        ].map(([k, label]) => (
          <div key={k} style={{
            background: C[k].bg, border: `1.5px solid ${C[k].border}`,
            color: C[k].text, borderRadius: 6, padding: '2px 10px',
            fontSize: 11, fontWeight: 600,
          }}>{label}</div>
        ))}
      </div>

      {/* ═══════════════════════════════════════════════════════════════
          FLOW 0 — ONBOARDING
      ═══════════════════════════════════════════════════════════════ */}
      <Card>
        <SectionTitle
          icon="🧑‍💼"
          title="Flow 0 — Customer Onboarding (Mở tài khoản)"
          sub="6 bước tuần tự: Register → Account → Credentials → KYC → Limits → Welcome Bonus"
        />
        <OnboardFlow />
      </Card>

      {/* ═══════════════════════════════════════════════════════════════
          FLOW 1 — INTERNAL TRANSFER (SAGA)
      ═══════════════════════════════════════════════════════════════ */}
      <Card>
        <SectionTitle
          icon="🏦"
          title="Flow 1 — Internal Transfer (Saga Orchestration)"
          sub="Chuyển khoản nội bộ VikkiBank — 6 steps, 2 compensable"
        />

        {/* Entry path */}
        <FlowRow>
          <Box label="Browser / FE" sub="React" color="fe" />
          <Arrow dir="right" label="POST /api/v1/transactions/transfer" color="#3b82f6" />
          <Box label="Kong Gateway" sub=":30000" color="gateway" />
          <Arrow dir="right" label="route → :8085" color="#7c3aed" />
          <Box label="Transaction Service" sub=":8085  — Spring Boot" color="tx" wide />
        </FlowRow>

        {/* Create TX */}
        <FlowRow>
          <div style={{ width: 390 }} />
          <Arrow dir="down" color="#16a34a" />
        </FlowRow>
        <FlowRow>
          <div style={{ width: 390 }} />
          <Box label="Create TX record" sub="status = PENDING  → PostgreSQL" color="tx" />
          <Arrow dir="right" color="#94a3b8" />
          <Box label="Saga Orchestrator" sub="SagaOrchestrator.java" color="saga" />
        </FlowRow>

        {/* Saga steps */}
        <div style={{ marginTop: 16, marginBottom: 8 }}>
          <Arrow dir="down" color="#0284c7" />
        </div>
        <SagaSteps />

        {/* Success path */}
        <div style={{ marginTop: 16 }}>
          <FlowRow>
            <Box label="TX → COMPLETED" sub="sagaState = COMPLETED" color="tx" />
            <Arrow dir="right" color="#16a34a" />
            <Box label="Return result" sub="txId, referenceId, status" color="fe" />
          </FlowRow>
        </div>

        <Note color="#16a34a">
          ✅ Steps ①②③ are <strong>read-only</strong> (no compensation needed). Steps ④⑤ are
          <strong> compensable</strong>: if CREDIT_RECEIVER fails, Saga automatically runs RELEASE_LIMIT → REFUND_SENDER in reverse order.
        </Note>
      </Card>

      {/* ═══════════════════════════════════════════════════════════════
          FLOW 2 — EXTERNAL TRANSFER (NAPAS)
      ═══════════════════════════════════════════════════════════════ */}
      <Card>
        <SectionTitle
          icon="🌐"
          title="Flow 2 — External Transfer (NAPAS)"
          sub="Chuyển khoản liên ngân hàng qua NAPAS Simulator"
        />

        <FlowRow>
          <Box label="Browser / FE" sub="React" color="fe" />
          <Arrow dir="right" label="POST /transfer" color="#3b82f6" />
          <Box label="Kong Gateway" color="gateway" />
          <Arrow dir="right" color="#7c3aed" />
          <Box label="Transaction Service" color="tx" />
          <Arrow dir="right" label="detect external bankCode" color="#16a34a" />
          <Box label="External Transfer Svc" sub=":8087" color="ext" />
        </FlowRow>

        <FlowRow gap={6}>
          <div style={{ width: 730 }} />
          <Arrow dir="down" color="#db2777" />
        </FlowRow>

        <FlowRow>
          <Box label="Create TX record" sub="PENDING → PostgreSQL" color="tx" />
          <div style={{ flex: 1, minWidth: 60 }} />
          <Box label="NAPAS Simulator" sub=":8088 — via Feign" color="napas" wide />
        </FlowRow>

        <FlowRow gap={6}>
          <div style={{ width: 530 }} />
          <Arrow dir="down" color="#9333ea" />
        </FlowRow>

        <FlowRow>
          <Box label="TX → COMPLETED" sub="napasRef stored" color="tx" />
          <Arrow dir="left" label="napasRef + status" color="#9333ea" />
          <div style={{ flex: 1 }} />
          <Box label="NAPAS processes" sub="validates & routes" color="napas" />
        </FlowRow>

        <Note color="#db2777">
          🌐 External flow does <strong>not</strong> use Saga — delegated entirely to External Transfer Service → NAPAS.
          On failure, Transaction Service marks TX as FAILED directly.
        </Note>
      </Card>

      {/* ═══════════════════════════════════════════════════════════════
          FLOW 3 — CDC (Debezium + Kafka + Redis CQRS)
      ═══════════════════════════════════════════════════════════════ */}
      <Card>
        <SectionTitle
          icon="⚡"
          title="Flow 3 — CDC Pipeline (CQRS Read Model)"
          sub="PostgreSQL WAL → Debezium → Kafka → Redis — real-time account read model sync"
        />

        {/* Write path */}
        <div style={{ marginBottom: 6, fontSize: 11, fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: 1 }}>
          ✍️  Write Path
        </div>
        <FlowRow>
          <Box label="Account Service" sub="debit / credit" color="account" />
          <Arrow dir="right" label="SQL UPDATE" color="#0891b2" />
          <Box label="PostgreSQL" sub="account_db  WAL level=logical" color="db" />
        </FlowRow>

        {/* CDC path */}
        <div style={{ margin: '14px 0 6px', fontSize: 11, fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: 1 }}>
          📡  CDC Pipeline
        </div>
        <FlowRow>
          <Box label="PostgreSQL" sub="WAL events" color="db" />
          <Arrow dir="right" label="pgoutput plugin" color="#dc2626" />
          <Box label="Debezium Connect" sub=":8083  connector" color="debez" />
          <Arrow dir="right" label="ExtractNewRecordState SMT" color="#059669" />
          <Box label="Kafka" sub="account_db.public.accounts" color="kafka" />
          <Arrow dir="right" label="groupId: account-service-cqrs" color="#78716c" />
          <Box label="AccountEventConsumer" sub="@KafkaListener" color="account" />
        </FlowRow>

        {/* Consumer logic */}
        <div style={{ margin: '14px 0 6px', fontSize: 11, fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: 1 }}>
          🔄  Consumer Logic
        </div>
        <FlowRow>
          <Box label="AccountEventConsumer" color="account" />
          <Arrow dir="right" color="#94a3b8" />
          <Box label="Version Check" sub="skip if version ≤ Redis version" color="saga" />
          <Arrow dir="right" label="newer event" color="#0891b2" />
          <Box label="Redis Write Model" sub="account:{accountNo}" color="redis" />
          <Arrow dir="right" color="#16a34a" />
          <Box label="ack.acknowledge()" sub="only after Redis write" color="debez" />
        </FlowRow>

        {/* Read path */}
        <div style={{ margin: '14px 0 6px', fontSize: 11, fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: 1 }}>
          📖  Read Path (CQRS)
        </div>
        <FlowRow>
          <Box label="Browser / FE" sub="checkBalance, getCustomerInfo" color="fe" />
          <Arrow dir="right" color="#3b82f6" />
          <Box label="Kong Gateway" color="gateway" />
          <Arrow dir="right" color="#7c3aed" />
          <Box label="Account Service" sub="AccountQueryService" color="account" />
          <Arrow dir="right" label="Redis hit (fast)" color="#e11d48" />
          <Box label="Redis" sub="~0ms latency" color="redis" />
        </FlowRow>

        <div style={{ marginTop: 12, display: 'flex', gap: 6, alignItems: 'center' }}>
          <Box label="Account Service" sub="AccountQueryService" color="account" />
          <Arrow dir="right" label="Redis miss / stale" color="#f59e0b" />
          <Box label="PostgreSQL" sub="fallback read" color="db" />
          <Arrow dir="right" label="then backfill Redis" color="#94a3b8" />
          <Box label="Redis" color="redis" />
        </div>

        <Note color="#0891b2">
          ⚡ <strong>Idempotency:</strong> Consumer checks <code>version</code> before writing to Redis — duplicate Kafka messages
          (at-least-once) are safely skipped. Ack happens <em>only after</em> successful Redis write to prevent message loss on crash.
          On repeated failures, Spring Kafka routes the event to the <strong>Dead Letter Topic (DLT)</strong>.
        </Note>
      </Card>

      {/* ═══════════════════════════════════════════════════════════════
          FLOW 4 — INQUIRY
      ═══════════════════════════════════════════════════════════════ */}
      <Card>
        <SectionTitle
          icon="🔍"
          title="Flow 4 — Account Inquiry (Tra cứu tài khoản)"
          sub="Lookup receiver account before transfer"
        />

        <div style={{ marginBottom: 10, fontSize: 12, color: '#475569', fontWeight: 600 }}>Internal:</div>
        <FlowRow>
          <Box label="Browser / FE" color="fe" />
          <Arrow dir="right" label="POST /inquiry?bankCode=970406" color="#3b82f6" />
          <Box label="Transaction Service" color="tx" />
          <Arrow dir="right" label="Feign" color="#16a34a" />
          <Box label="Internal Transfer Svc" sub=":8086" color="tx" />
          <Arrow dir="right" color="#94a3b8" />
          <Box label="Account Service" sub="inquiry → Redis/PG" color="account" />
        </FlowRow>

        <div style={{ margin: '14px 0 6px', fontSize: 12, color: '#475569', fontWeight: 600 }}>External:</div>
        <FlowRow>
          <Box label="Browser / FE" color="fe" />
          <Arrow dir="right" label="POST /inquiry?bankCode=VCB…" color="#3b82f6" />
          <Box label="Transaction Service" color="tx" />
          <Arrow dir="right" label="Feign" color="#db2777" />
          <Box label="External Transfer Svc" sub=":8087" color="ext" />
          <Arrow dir="right" color="#9333ea" />
          <Box label="NAPAS Simulator" sub=":8088" color="napas" />
        </FlowRow>
      </Card>

      {/* ── Overall service map ─────────────────────────────────────── */}
      <Card style={{ background: '#f8fafc' }}>
        <SectionTitle icon="🗺️" title="Service Map" sub="Ports & responsibilities" />
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 10 }}>
          {[
            { color: 'gateway', label: 'Kong Gateway',           port: ':30000', desc: 'API Gateway, routing' },
            { color: 'fe',      label: 'Frontend (React)',        port: ':30080', desc: 'Nginx + React app' },
            { color: 'auth',    label: 'Auth Service',           port: ':8081',  desc: 'JWT login/logout, register' },
            { color: 'account', label: 'Account Service',        port: ':8082',  desc: 'CQRS — PG + Redis' },
            { color: 'kyc',     label: 'KYC Service',            port: ':8083',  desc: 'Identity verification' },
            { color: 'limit',   label: 'Limit Service',          port: ':8084',  desc: 'Transfer limit rules' },
            { color: 'tx',      label: 'Transaction Service',    port: ':8085',  desc: 'Saga Orchestrator' },
            { color: 'tx',      label: 'Internal Transfer Svc',  port: ':8086',  desc: 'Internal routing' },
            { color: 'ext',     label: 'External Transfer Svc',  port: ':8087',  desc: 'NAPAS integration' },
            { color: 'napas',   label: 'NAPAS Simulator',        port: ':8088',  desc: 'Interbank simulator' },
            { color: 'db',      label: 'PostgreSQL',             port: ':5432',  desc: 'Write stores (WAL)' },
            { color: 'redis',   label: 'Redis',                  port: ':6379',  desc: 'CQRS read model' },
            { color: 'kafka',   label: 'Kafka (KRaft)',          port: ':9092',  desc: 'CDC event streaming' },
            { color: 'debez',   label: 'Debezium Connect',       port: ':8083',  desc: 'PG → Kafka CDC' },
          ].map((s, i) => {
            const c = C[s.color];
            return (
              <div key={i} style={{
                background: c.bg, border: `1.5px solid ${c.border}`,
                borderRadius: 10, padding: '10px 14px',
              }}>
                <div style={{ fontSize: 13, fontWeight: 700, color: c.text }}>{s.label}</div>
                <div style={{ fontSize: 11, color: c.border, fontFamily: 'monospace', marginTop: 2 }}>{s.port}</div>
                <div style={{ fontSize: 11, color: '#64748b', marginTop: 4 }}>{s.desc}</div>
              </div>
            );
          })}
        </div>
      </Card>
    </div>
  );
}

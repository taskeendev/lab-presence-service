# lab-presence-service — Progress

ส่วนหนึ่งของ **Feature Lab** — realtime presence: ใคร online/offline เมื่อไหร่ (admin dashboard)
สถาปัตยกรรม: service แยก stateless — ตรวจ JWT เองด้วย secret ร่วม, presence อยู่ใน memory (ไม่มี DB โดยตั้งใจ)

สถานะ: ⬜ ยังไม่เริ่ม · 🔨 กำลังทำ · ✅ เสร็จ

## บันได 5 ขั้น

- [x] 1. โครง service: env/health/request-id/CI (ไม่มี DB) — 2026-06-12
- [x] 2. WebSocket endpoint + auth ด้วย first message + ทะเบียน online ใน memory — 2026-06-12
- [x] 3. GET /api/presence (ADMIN) + push เหตุการณ์เข้า-ออกให้แอดมินแบบสด — 2026-06-12
- [ ] 4. lab-web: ต่อ WS อัตโนมัติเมื่อ login + หน้า Admin รายชื่อสด (role ADMIN)
- [ ] 5. Integration tests + CI เขียว + demo สองตัวตนเห็นสด (เกณฑ์เฟส)

## Log การทำงาน

- 2026-06-12 — ขั้น 3 เสร็จ: starter-security + JwtAuthFilter (pattern เดิม) — permitAll /ws/** (auth ในโปรโตคอลแล้ว); GET /api/presence @PreAuthorize ADMIN (403 ทำงานที่ชั้น filter เพราะไม่มี catch-all advice มากลืน — บทเรียน auth-service ภาคกลับ); handler จำผู้เฝ้า (role ADMIN) ห่อ ConcurrentWebSocketSessionDecorator (session ห้ามส่งพร้อมกันหลาย thread) → ส่ง snapshot ทันที + broadcast online/offline; ผู้เฝ้าที่ส่งไม่ได้ถูกถอดทิ้ง; เทสต์สด: snapshot/online/offline ครบ + REST 401/403/200

- 2026-06-12 — ขั้น 2 เสร็จ: /ws/presence (TextWebSocketHandler) — ข้อความแรกต้อง {"type":"auth","token"} (ไม่ใช้ query param: token ใน URL ติด log), ผิด/ปลอม → ปิด 1008; PresenceRegistry: compute atomic ราย key, connect คืน true เฉพาะ session แรก / disconnect คืน true เฉพาะ session สุดท้าย (multi-tab = online เดียว), ping→pong + touch lastSeen; ทดสอบด้วย node WebSocket จริง: token ปลอม 1008 ✓, 2 แท็บ ready ✓, pong ✓, log online/offline อย่างละครั้งเดียว ✓ — cross-service JWT (auth ออก presence ตรวจ) ทำงานจริง

- 2026-06-12 — ขั้น 1 เสร็จ: โครงตามแบบ auth-service (env ล้วน, /health, RequestIdFilter ใช้ log pattern เดียวกันทั้งระบบ, graceful shutdown, CI) — ไม่มี DB: presence เป็น state ชั่วครู่; JWT_SECRET ใช้ค่าเดียวกับ auth-service (ใน .env ที่ไม่ commit) — สัญญา HS512 ร่วม (อนาคตอัปเกรด RS256/JWKS ได้)

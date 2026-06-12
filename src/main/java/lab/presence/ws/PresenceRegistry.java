package lab.presence.ws;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

// สมุดรายชื่อคน online — in-memory ล้วน (presence คือ state ชั่วครู่ ตายพร้อม service ได้)
// กติกา multi-tab: user เดียวหลาย connection = online หนึ่งคน, ปิด connection สุดท้ายค่อย offline
@Component
public class PresenceRegistry {

    public record Snapshot(String username, Instant since, Instant lastSeen, int sessions) {}

    private static final class Entry {
        final Set<String> sessions = ConcurrentHashMap.newKeySet();
        final Instant since = Instant.now();
        volatile Instant lastSeen = Instant.now();
    }

    private final ConcurrentHashMap<String, Entry> users = new ConcurrentHashMap<>();

    // คืน true เฉพาะตอน "เพิ่ง online" (session แรก) — compute เป็น atomic ราย key
    public boolean connect(String username, String sessionId) {
        boolean[] cameOnline = {false};
        users.compute(username, (k, entry) -> {
            if (entry == null) {
                entry = new Entry();
                cameOnline[0] = true;
            }
            entry.sessions.add(sessionId);
            entry.lastSeen = Instant.now();
            return entry;
        });
        return cameOnline[0];
    }

    // คืน true เฉพาะตอน "เพิ่ง offline" (session สุดท้ายปิด) — คืน null = ลบ key ทิ้ง
    public boolean disconnect(String username, String sessionId) {
        boolean[] wentOffline = {false};
        users.computeIfPresent(username, (k, entry) -> {
            entry.sessions.remove(sessionId);
            if (entry.sessions.isEmpty()) {
                wentOffline[0] = true;
                return null;
            }
            return entry;
        });
        return wentOffline[0];
    }

    public void touch(String username) {
        Entry entry = users.get(username);
        if (entry != null) {
            entry.lastSeen = Instant.now();
        }
    }

    public List<Snapshot> snapshot() {
        return users.entrySet().stream()
                .map(e -> new Snapshot(
                        e.getKey(), e.getValue().since, e.getValue().lastSeen,
                        e.getValue().sessions.size()))
                .sorted(Comparator.comparing(Snapshot::since))
                .toList();
    }
}

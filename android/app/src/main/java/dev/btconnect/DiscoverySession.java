package dev.btconnect;

import java.util.*;

/** Accept delayed SDP results for the current scan, until it is explicitly stopped. */
final class DiscoverySession {
    private final Set<String> candidates=new HashSet<>();
    private final Set<String> servers=new HashSet<>();
    private boolean active;
    void begin() { candidates.clear(); servers.clear(); active=true; }
    void candidate(String address) { if(active) candidates.add(address); }
    boolean services(String address,UUID[] uuids) {
        if(!active || !candidates.contains(address) || uuids==null) return false;
        for(UUID uuid:uuids) if(Protocol.SERVICE_ID.equals(uuid)) return servers.add(address);
        return false;
    }
    int count() { return servers.size(); }
    void stop() { active=false; }
}

package com.pulsehub.service;

public interface PresenceService {

    void markOnline(String email);

    void markOffline(String email);

    void recordActivity(String email);

    /** Scans currently-online users and flips inactive ones to AWAY. */
    void reapInactiveUsers();
}

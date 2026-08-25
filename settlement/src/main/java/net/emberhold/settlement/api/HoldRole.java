package net.emberhold.settlement.api;

/**
 * A member's role in a Hold (spec 07 §A.1).
 *
 * <p>MVP has no election/politics engine; roles map to permission keys
 * {@code ember.hold.<role>.*} via LuckPerms contexts. Only the owner/officer may change roles.</p>
 */
public enum HoldRole {
    OWNER,
    OFFICER,
    ENGINEER,
    HUNTER,
    MEDIC,
    SCOUT,
    WARDEN,
    MEMBER;

    /** Whether this role may change other members' roles (owner/officer). */
    public boolean canManageMembers() {
        return this == OWNER || this == OFFICER;
    }
}

package com.miniclaudecode.tool;

/**
 * Mirrors Claude Code's 5 external permission modes.
 */
public enum PermissionMode {
    /** Normal interactive mode — ask user for confirmation on writes. */
    DEFAULT,
    /** Read-only mode — no write tools available. */
    PLAN,
    /** Auto-approve edit tools. */
    ACCEPT_EDITS,
    /** Skip all confirmations. */
    BYPASS_PERMISSIONS,
    /** CI/non-interactive — auto-deny all confirmations. */
    DONT_ASK
}

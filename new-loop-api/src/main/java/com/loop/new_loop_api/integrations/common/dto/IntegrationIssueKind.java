package com.loop.new_loop_api.integrations.common.dto;

/** What an operator has to do about a send to Aguas/Odoo. Declared in priority order (most urgent first). */
public enum IntegrationIssueKind {
    /** Failed and the automatic retries are used up: needs a manual retry or a fix at the source. */
    RETRIES_EXHAUSTED,
    /** An attempt started but never finished (no answer recorded). */
    STUCK,
    /** Should have been sent, but no attempt was ever recorded (e.g. the async listener died). */
    NEVER_SENT,
    /** Failed, but the scheduler will still retry it on its own. */
    RETRYING
}

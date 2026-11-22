-- Required for combining UUID equality (=) with tstzrange overlap (&&) in a GiST exclusion constraint
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- 1. Users table
CREATE TABLE users (
                       id UUID PRIMARY KEY,
                       name VARCHAR(255) NOT NULL,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Time Slots table
CREATE TABLE time_slots (
                            id UUID PRIMARY KEY,
                            user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            start_time TIMESTAMPTZ NOT NULL,
                            end_time TIMESTAMPTZ NOT NULL,
                            status VARCHAR(20) NOT NULL DEFAULT 'FREE',
                            version BIGINT NOT NULL DEFAULT 0,
                            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Ensure valid time interval and allowed statuses
                            CONSTRAINT chk_time_slot_valid_range CHECK (end_time > start_time),
                            CONSTRAINT chk_time_slot_status CHECK (status IN ('FREE', 'BUSY', 'BOOKED')),

    -- Prevent any two slots for the same user from overlapping (half-open interval '[)' allows back-to-back slots)
                            CONSTRAINT excl_user_overlapping_slots EXCLUDE USING gist (
        user_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    )
);

-- Composite index for fast time-frame and status filtering per user
CREATE INDEX idx_time_slots_user_time_status
    ON time_slots (user_id, start_time, end_time, status);

-- 3. Meetings table
CREATE TABLE meetings (
                          id UUID PRIMARY KEY,
                          slot_id UUID NOT NULL UNIQUE REFERENCES time_slots(id) ON DELETE RESTRICT,
                          title VARCHAR(255) NOT NULL,
                          description TEXT,
                          idempotency_key VARCHAR(255) UNIQUE,
                          request_hash VARCHAR(64),
                          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Ensure request_hash is always present whenever an idempotency_key is supplied
                          CONSTRAINT chk_meeting_idempotency_pair CHECK (
                              (idempotency_key IS NULL AND request_hash IS NULL)
                                  OR (idempotency_key IS NOT NULL AND request_hash IS NOT NULL)
                              )
);

-- 4. Meeting Participants table
CREATE TABLE meeting_participants (
                                      meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
                                      participant_email VARCHAR(255) NOT NULL,
                                      PRIMARY KEY (meeting_id, participant_email)
);

CREATE TABLE account (
 id uuid PRIMARY KEY, email varchar(160) NOT NULL UNIQUE,
 display_name varchar(80) NOT NULL, password_hash varchar(512) NOT NULL,
 role varchar(16) NOT NULL CHECK (role IN ('CLIENT','SUPPORT')), active boolean NOT NULL DEFAULT true
);
CREATE TABLE conversation (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES account(id),
 assigned_agent_id uuid REFERENCES account(id),
 status varchar(12) NOT NULL DEFAULT 'WAITING' CHECK(status IN ('WAITING','ACTIVE','CLOSED')),
 last_sequence bigint NOT NULL DEFAULT 0 CHECK(last_sequence BETWEEN 0 AND 9007199254740991),
 version bigint NOT NULL DEFAULT 0, creation_key uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(owner_id,creation_key)
);
CREATE TABLE message (
 id uuid PRIMARY KEY, conversation_id uuid NOT NULL REFERENCES conversation(id),
 sender_id uuid NOT NULL REFERENCES account(id), client_message_id uuid NOT NULL,
 sequence bigint NOT NULL CHECK(sequence BETWEEN 1 AND 9007199254740991),
 body text NOT NULL CHECK(char_length(body) BETWEEN 1 AND 2000 AND body !~ '^\s*$'),
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(conversation_id,sender_id,client_message_id), UNIQUE(conversation_id,sequence)
);
CREATE TABLE conversation_event (
 conversation_id uuid NOT NULL REFERENCES conversation(id), sequence bigint NOT NULL,
 kind varchar(20) NOT NULL CHECK(kind IN ('message.created','assigned','released','closed')),
 message_id uuid UNIQUE REFERENCES message(id), created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(conversation_id,sequence)
);
CREATE INDEX ix_conversation_owner ON conversation(owner_id,created_at,id);
CREATE INDEX ix_conversation_agent ON conversation(assigned_agent_id,status);

CREATE TABLE SPRING_SESSION (
 PRIMARY_ID CHAR(36) NOT NULL PRIMARY KEY, SESSION_ID CHAR(36) NOT NULL,
 CREATION_TIME BIGINT NOT NULL, LAST_ACCESS_TIME BIGINT NOT NULL,
 MAX_INACTIVE_INTERVAL INT NOT NULL, EXPIRY_TIME BIGINT NOT NULL, PRINCIPAL_NAME VARCHAR(100)
);
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION(SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION(EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION(PRINCIPAL_NAME);
CREATE TABLE SPRING_SESSION_ATTRIBUTES (
 SESSION_PRIMARY_ID CHAR(36) NOT NULL REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE,
 ATTRIBUTE_NAME VARCHAR(200) NOT NULL, ATTRIBUTE_BYTES BYTEA NOT NULL,
 PRIMARY KEY(SESSION_PRIMARY_ID,ATTRIBUTE_NAME)
);

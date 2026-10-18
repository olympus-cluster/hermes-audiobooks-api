CREATE TABLE audiobook (
    id                 uuid PRIMARY KEY,
    title              varchar(300) NOT NULL,
    author             varchar(200) NOT NULL,
    narrator           varchar(200),
    description        text,
    language           varchar(10),
    published_year     integer,
    cover_key          varchar(500),
    cover_content_type varchar(100),
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audiobook_title_lower ON audiobook (lower(title));
CREATE INDEX idx_audiobook_author_lower ON audiobook (lower(author));

CREATE TABLE chapter (
    id               uuid PRIMARY KEY,
    audiobook_id     uuid         NOT NULL REFERENCES audiobook (id) ON DELETE CASCADE,
    position         integer      NOT NULL CHECK (position >= 1),
    title            varchar(300) NOT NULL,
    duration_seconds integer CHECK (duration_seconds >= 0),
    audio_key        varchar(500) NOT NULL,
    content_type     varchar(100) NOT NULL,
    size_bytes       bigint       NOT NULL CHECK (size_bytes >= 0),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_chapter_audiobook_position UNIQUE (audiobook_id, position)
);

CREATE TABLE listening_progress (
    audiobook_id     uuid PRIMARY KEY REFERENCES audiobook (id) ON DELETE CASCADE,
    chapter_id       uuid        NOT NULL REFERENCES chapter (id) ON DELETE CASCADE,
    position_seconds integer     NOT NULL CHECK (position_seconds >= 0),
    updated_at       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_listening_progress_chapter ON listening_progress (chapter_id);

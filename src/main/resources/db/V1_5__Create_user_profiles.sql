-- Global per-user behavioural profile. The bot lives in a single chat, so one row
-- per Telegram user id is global. `description` is a rolling <=300 char dossier the
-- bot rewrites after each reply; the CHECK is defense-in-depth (code also truncates).
create table user_profile (
    user_id      bigint primary key,
    display_name text not null default '',
    description  text not null default '' check (char_length(description) <= 300),
    updated_at   timestamptz not null default now()
);

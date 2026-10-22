-- Invitations are managed by the authenticated Java backend, never the browser Data API.
create table public.household_invites (
    id uuid primary key default gen_random_uuid(),
    household_id uuid not null references public.households(id) on delete cascade,
    email varchar(254) not null check (email = lower(btrim(email)) and char_length(email) > 0),
    invited_by uuid not null references auth.users(id) on delete cascade,
    token_hash varchar(64) not null unique check (token_hash ~ '^[0-9a-f]{64}$'),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    accepted_at timestamptz,
    constraint household_invites_email_unique unique (household_id, email),
    constraint household_invites_expiry_check check (expires_at > created_at)
);

-- The household/email unique index covers household lookups and its foreign key.
create index household_invites_invited_by_idx on public.household_invites (invited_by);
alter table public.household_invites enable row level security;
revoke all on public.household_invites from public, anon, authenticated;

create table if not exists public.households (
    id uuid primary key,
    name varchar(100) not null,
    owner_user_id uuid not null,
    created_at timestamp with time zone not null
);
create table if not exists public.household_members (
    household_id uuid not null,
    user_id uuid not null,
    joined_at timestamp with time zone default current_timestamp,
    primary key (household_id, user_id),
    foreign key (household_id) references public.households(id)
);

create table if not exists public.household_invites (
    id uuid primary key,
    household_id uuid not null references public.households(id) on delete cascade,
    email varchar(254) not null,
    invited_by uuid not null,
    token_hash varchar(64) not null unique,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    accepted_at timestamp with time zone,
    unique (household_id, email),
    check (expires_at > created_at)
);

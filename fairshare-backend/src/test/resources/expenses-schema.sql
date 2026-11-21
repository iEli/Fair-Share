-- Supabase Auth's user table is stubbed for local tests.
create schema if not exists auth;
create table if not exists auth.users (id uuid primary key);

create table if not exists public.expenses (
    id uuid primary key,
    household_id uuid not null references public.households(id) on delete cascade,
    payer_user_id uuid not null,
    description varchar(200) not null check (char_length(trim(description)) between 1 and 200),
    amount_cents bigint not null check (amount_cents between 1 and 999999999999),
    spent_on date not null,
    created_at timestamp with time zone not null
);

create index if not exists expenses_household_date_idx
    on public.expenses (household_id, spent_on desc, created_at desc, id desc);

-- Keep historical participant IDs when household membership changes.
create table if not exists public.expense_shares (
    expense_id uuid not null references public.expenses(id) on delete cascade,
    user_id uuid not null,
    amount_cents bigint not null check (amount_cents between 0 and 999999999999),
    primary key (expense_id, user_id)
);

create table if not exists public.expense_payments (
    expense_id uuid not null,
    user_id uuid not null,
    id uuid not null,
    amount_cents bigint not null check (amount_cents between 1 and 999999999999),
    recorded_at timestamp with time zone not null default now(),
    primary key (expense_id, user_id, id),
    foreign key (expense_id, user_id)
        references public.expense_shares(expense_id, user_id) on delete cascade
);

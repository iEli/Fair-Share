create table public.inventory_items (
    id uuid primary key default gen_random_uuid(),
    household_id uuid not null references public.households(id) on delete cascade,
    name varchar(100) not null check (char_length(btrim(name)) between 1 and 100),
    category varchar(60) not null check (char_length(btrim(category)) between 1 and 60),
    icon varchar(16) not null default '📦',
    quantity integer not null default 0 check (quantity between 0 and 1000000),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index inventory_items_household_name_idx
    on public.inventory_items (household_id, (lower(name)));

alter table public.inventory_items enable row level security;
revoke all on public.inventory_items from public, anon, authenticated;

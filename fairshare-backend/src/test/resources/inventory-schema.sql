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
create table if not exists public.inventory_items (
    id uuid primary key,
    household_id uuid not null references public.households(id) on delete cascade,
    name varchar(100) not null,
    category varchar(60) not null,
    icon varchar(16) not null,
    quantity integer not null check (quantity between 0 and 1000000),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

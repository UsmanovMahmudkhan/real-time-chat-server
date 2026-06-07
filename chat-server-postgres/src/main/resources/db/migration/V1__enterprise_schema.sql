create type tenant_role as enum ('OWNER', 'ADMIN', 'MODERATOR', 'MEMBER', 'READ_ONLY');
create type room_visibility as enum ('PUBLIC', 'PRIVATE', 'RESTRICTED');
create type message_status as enum ('ACTIVE', 'TOMBSTONED');

create table tenants (
    tenant_id uuid primary key,
    name varchar(200) not null,
    active boolean not null default true,
    allowed_origins text[] not null default '{}',
    created_at timestamptz not null default now()
);

create table users (
    user_id uuid primary key,
    display_name varchar(200) not null,
    active boolean not null default true,
    created_at timestamptz not null default now()
);

create table external_identities (
    issuer varchar(500) not null,
    subject varchar(500) not null,
    user_id uuid not null references users(user_id),
    created_at timestamptz not null default now(),
    primary key (issuer, subject)
);

create table tenant_memberships (
    tenant_id uuid not null references tenants(tenant_id),
    user_id uuid not null references users(user_id),
    roles tenant_role[] not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    primary key (tenant_id, user_id)
);

create table rooms (
    tenant_id uuid not null references tenants(tenant_id),
    room_id uuid not null,
    name varchar(200) not null,
    visibility room_visibility not null,
    required_roles text[] not null default '{}',
    active boolean not null default true,
    created_at timestamptz not null default now(),
    primary key (tenant_id, room_id),
    unique (tenant_id, name)
);

create table room_memberships (
    tenant_id uuid not null,
    room_id uuid not null,
    user_id uuid not null references users(user_id),
    active boolean not null default true,
    created_at timestamptz not null default now(),
    primary key (tenant_id, room_id, user_id),
    foreign key (tenant_id, room_id) references rooms(tenant_id, room_id)
);

create table messages (
    message_id uuid not null,
    tenant_id uuid not null,
    room_id uuid not null,
    sender_id uuid not null references users(user_id),
    sender_display_name varchar(200) not null,
    content text not null,
    status message_status not null,
    created_at timestamptz not null,
    tombstoned_at timestamptz,
    primary key (tenant_id, message_id),
    foreign key (tenant_id, room_id) references rooms(tenant_id, room_id)
);
create index messages_room_history on messages(tenant_id, room_id, created_at, message_id);

create table message_idempotency_keys (
    tenant_id uuid not null,
    sender_id uuid not null,
    idempotency_key uuid not null,
    message_id uuid not null,
    created_at timestamptz not null,
    primary key (tenant_id, sender_id, idempotency_key),
    foreign key (tenant_id, message_id) references messages(tenant_id, message_id)
);

create table retention_policies (
    tenant_id uuid primary key references tenants(tenant_id),
    retention_days integer not null check (retention_days between 1 and 3650),
    updated_at timestamptz not null default now()
);

create table legal_holds (
    hold_id uuid primary key,
    tenant_id uuid not null references tenants(tenant_id),
    room_id uuid,
    reason varchar(1000) not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    released_at timestamptz
);

create table audit_events (
    event_id uuid primary key,
    tenant_id uuid,
    actor_id uuid,
    action varchar(200) not null,
    target_type varchar(100) not null,
    target_id varchar(500) not null,
    result varchar(100) not null,
    correlation_id varchar(200) not null,
    metadata jsonb not null default '{}',
    created_at timestamptz not null
);
create index audit_events_tenant_time on audit_events(tenant_id, created_at);

create table outbox_events (
    event_id uuid primary key,
    tenant_id uuid not null,
    aggregate_type varchar(100) not null,
    aggregate_id uuid not null,
    event_type varchar(100) not null,
    payload_reference varchar(500) not null,
    correlation_id varchar(200) not null,
    created_at timestamptz not null,
    published_at timestamptz,
    attempts integer not null default 0
);
create index outbox_unpublished on outbox_events(created_at) where published_at is null;

alter table tenant_memberships enable row level security;
alter table rooms enable row level security;
alter table room_memberships enable row level security;
alter table messages enable row level security;
alter table audit_events enable row level security;

create policy tenant_memberships_isolation on tenant_memberships
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
create policy rooms_isolation on rooms
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
create policy room_memberships_isolation on room_memberships
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
create policy messages_isolation on messages
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
create policy audit_events_isolation on audit_events
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

create or replace function reject_audit_mutation() returns trigger language plpgsql as $$
begin
    raise exception 'audit_events is append-only';
end;
$$;
create trigger audit_events_no_update before update or delete on audit_events
    for each row execute function reject_audit_mutation();

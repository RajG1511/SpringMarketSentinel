create table if not exists asset (
                                     id bigserial primary key,
                                     symbol varchar(20) not null unique,
    name varchar(200) not null,
    asset_type varchar(50) not null,
    created_at timestamptz not null default now()
    );

create table if not exists price_bar (
                                         asset_id bigint not null references asset(id) on delete cascade,
    ts date not null,
    open numeric(18,6),
    high numeric(18,6),
    low numeric(18,6),
    close numeric(18,6) not null,
    volume bigint,
    primary key (asset_id, ts)
    );

create index if not exists idx_price_bar_ts on price_bar(ts);

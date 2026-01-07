create table if not exists asset (
                                     id bigserial primary key,
                                     symbol varchar(20) not null unique,
    name varchar(200) not null,
    asset_type varchar(50) not null,
    created_at timestamptz not null default now()
    );


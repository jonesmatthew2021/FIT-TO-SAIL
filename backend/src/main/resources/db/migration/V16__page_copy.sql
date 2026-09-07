-- Page text the office has rewritten from the page itself (Edit text). One row per sentence key;
-- a key with no row reads as the wording in the code.
create table page_copy (
    copy_key   varchar(120) primary key,
    text       text         not null,
    updated_by varchar(200) not null,
    updated_at timestamptz  not null default now()
);

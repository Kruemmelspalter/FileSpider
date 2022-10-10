create table Document (
    id uuid not null,
    title tinytext default 'Untitled',
    added timestamp,
    renderer varchar(16),
    editor varchar(32),
    mimeType varchar(64),
    primary key (id)
);

create table Tag (
    document uuid not null,
    tag varchar(20) not null,
    primary key (document, tag)
);
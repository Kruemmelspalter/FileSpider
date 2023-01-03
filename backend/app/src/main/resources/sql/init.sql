create table Document (
    id uuid not null,
    title tinytext default 'Untitled',
    added timestamp default now(),
    renderer varchar(16) default 'mimeSpecific',
    editor varchar(32) default 'mimeSpecific',
    mimeType varchar(64) not null,
    fileExtension varchar(10),
    primary key (id)
);

create table Tag (
    document uuid not null,
    tag varchar(20) not null,
    primary key (document, tag)
);
create table Document (
    id uuid not null,
    title tinytext default 'Untitled',
    added timestamp default now(),
    renderer varchar(16) default 'mime',
    editor varchar(32) default 'mime',
    mimeType varchar(64) not null,
    fileExtension varchar(10),
    primary key (id)
);

create table Tag (
    document uuid not null,
    tag varchar(50) not null,
    primary key (document, tag)
);

create table Cache (
    document uuid not null,
    hash binary(16) not null,
    mimeType varchar(64) not null,
    fileName varchar(64) not null,
    primary key (document)
)

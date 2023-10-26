create table if not exists Document (
     id char(16) primary key,
     title varchar(32) not null,
     type varchar(16) not null,
     added integer not null,
     file_extension varchar(16) not null
);

create table if not exists Tag (
    document char(36) not null,
    tag varchar(32) not null,
    primary key (document, tag)
);

create table if not exists Cache (
    document char(36) primary key,
    hash binary(16) not null,
    render_type varchar(16) default 'plain' not null
);

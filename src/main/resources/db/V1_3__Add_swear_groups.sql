create table swear_group(
    id serial primary key,
    chance integer not null
);

create table swear(
    id serial primary key,
    swear text unique not null,
    weight integer not null default 500,
    group_id integer not null,
    constraint swear_group_id_fk foreign key (group_id) references swear_group (id)
);

insert into swear_group(id, chance) values(1, 300);
insert into swear(swear, group_id) 
values
    ('slava Ukraine!', 1),
    ('nu eto Zalupa uje', 1),
    ('da', 1),
    ('tak tochno', 1),
    ('eto ne tak', 1),
    ('infa 100', 1),
    ('nyet', 1),
    ('podderjivau vot etogo', 1),
    ('puk puk', 1),
    ('welcome to the club, buddy', 1);
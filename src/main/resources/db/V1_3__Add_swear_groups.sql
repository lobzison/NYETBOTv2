create table swear_group(
    id serial primary key,
    chance: integer not null
);

create table swear(
    id serial primary key,
    swear text unique not null,
    weight integer not null default 500,
    group_id integer not null foreign key references swear_group(id)
);

insert into swear_group(group_id, chance) values(1, 300);
insert into swear(swear, group_id)
    values("slava Ukraine!", 1)
    values("nu eto Zalupa uje", 1)
    values("da", 1)
    values("tak tochno", 1)
    values("eto ne tak", 1)
    values("infa 100", 1)
    values("nyet", 1)
    values("podderjivau vot etogo", 1)
    values("puk puk", 1)
    values("welcome to the club, buddy", 1);
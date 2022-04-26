create table memes (
    id serial primary key,
    trigger text not null,
    body json not null
);
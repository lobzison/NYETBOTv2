update memes set trigger = '%'||trigger||'%'
where trigger not like '\%%'
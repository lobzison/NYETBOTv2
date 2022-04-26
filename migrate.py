import redis
import json
import os
import telebot
import time
redis_url = os.environ["REDIS_URL"]
c = redis.from_url(redis_url)


token = os.environ["TELEGRAM_TOKEN"]
tb = telebot.TeleBot(token)
chat_id = -703958653

memes = json.loads(c.get("memes"))

pictures = []
gifs = []
stickers = []

for k, v in memes.items():
    print(k, v)
    if v['type'] == 'photo':
        pictures.append((k, v['adress']))
    if v['type'] == 'document':
        gifs.append((k, v['adress']))
    if v['type'] == 'sticker':
        stickers.append((k, v['adress']))

print(pictures)

def create_single_meme_sticker(name, file_id):
    tb.send_message(chat_id, "/add_meme")
    time.sleep(3)
    tb.send_message(chat_id, name)
    time.sleep(3)
    tb.send_sticker(chat_id, file_id)
    time.sleep(3)

def create_single_meme_gif(name, file_id):
    tb.send_message(chat_id, "/add_meme")
    time.sleep(3)
    tb.send_message(chat_id, name)
    time.sleep(3)
    tb.send_animation(chat_id, file_id)
    time.sleep(3)

def create_single_meme_picture(name, file_id):
    tb.send_message(chat_id, "/add_meme")
    time.sleep(3)
    tb.send_message(chat_id, name)
    time.sleep(3)
    tb.send_photo(chat_id, file_id)
    time.sleep(3)

for a in pictures:
    create_single_meme_picture(a[0], a[1])
for a in gifs:
    create_single_meme_gif(a[0], a[1])
for a in stickers:
    create_single_meme_sticker(a[0], a[1])

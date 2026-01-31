#!/usr/bin/env tarantool

-- Базовая конфигурация Tarantool
box.cfg {
    listen = 3301,              -- Порт для подключения
    log_level = 5,              -- Уровень логирования (5 = INFO)
    memtx_memory = 256 * 1024 * 1024,  -- 256MB памяти для данных
    vinyl_memory = 128 * 1024 * 1024,  -- 128MB для vinyl engine
}

-- Создаем пользователя для подключения из Java
box.schema.user.create('searchengine', {
    password = 'searchengine',
    if_not_exists = true
})

-- Даем права пользователю
box.schema.user.grant('searchengine', 'read,write,execute', 'universe', nil, {
    if_not_exists = true
})

-- Создаем спейсы (таблицы)

-- 1. Спейс для сайтов
local site = box.schema.space.create('site', {
    if_not_exists = true,
    format = {
        {name = 'id', type = 'unsigned'},
        {name = 'status', type = 'string'},
        {name = 'status_time', type = 'string'},  -- ISO datetime string
        {name = 'last_error', type = 'string', is_nullable = true},
        {name = 'url', type = 'string'},
        {name = 'name', type = 'string'}
    }
})

site:create_index('primary', {
    parts = {'id'},
    if_not_exists = true
})

site:create_index('url', {
    parts = {'url'},
    unique = true,
    if_not_exists = true
})

-- 2. Спейс для страниц
local page = box.schema.space.create('page', {
    if_not_exists = true,
    format = {
        {name = 'id', type = 'unsigned'},
        {name = 'site_id', type = 'unsigned'},
        {name = 'path', type = 'string'},
        {name = 'code', type = 'integer'},
        {name = 'content', type = 'string'}
    }
})

page:create_index('primary', {
    parts = {'id'},
    if_not_exists = true
})

page:create_index('site_id', {
    parts = {'site_id'},
    unique = false,
    if_not_exists = true
})

page:create_index('path', {
    parts = {'site_id', 'path'},
    unique = true,
    if_not_exists = true
})

-- 3. Спейс для лемм
local lemma = box.schema.space.create('lemma', {
    if_not_exists = true,
    format = {
        {name = 'id', type = 'unsigned'},
        {name = 'site_id', type = 'unsigned'},
        {name = 'lemma', type = 'string'},
        {name = 'frequency', type = 'integer'}
    }
})

lemma:create_index('primary', {
    parts = {'id'},
    if_not_exists = true
})

lemma:create_index('site_id', {
    parts = {'site_id'},
    unique = false,
    if_not_exists = true
})

lemma:create_index('lemma', {
    parts = {'site_id', 'lemma'},
    unique = true,
    if_not_exists = true
})

-- 4. Спейс для индексов
local index_space = box.schema.space.create('index', {
    if_not_exists = true,
    format = {
        {name = 'id', type = 'unsigned'},
        {name = 'page_id', type = 'unsigned'},
        {name = 'lemma_id', type = 'unsigned'},
        {name = 'rank', type = 'double'}
    }
})

index_space:create_index('primary', {
    parts = {'id'},
    if_not_exists = true
})

index_space:create_index('page_id', {
    parts = {'page_id'},
    unique = false,
    if_not_exists = true
})

index_space:create_index('lemma_id', {
    parts = {'lemma_id'},
    unique = false,
    if_not_exists = true
})

index_space:create_index('page_lemma', {
    parts = {'page_id', 'lemma_id'},
    unique = true,
    if_not_exists = true
})

print('Tarantool initialized successfully!')
print('Listening on port 3301')
print('User: searchengine / Password: searchengine')

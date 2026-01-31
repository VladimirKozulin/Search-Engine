# Руководство по миграции на Tarantool

## Шаг 1: Запуск Tarantool через Docker

```bash
# Собрать и запустить контейнер
docker-compose up -d

# Проверить логи
docker-compose logs -f tarantool

# Подключиться к консоли Tarantool
docker exec -it searchengine-tarantool tarantoolctl connect 3301
```

## Шаг 2: Изменения в pom.xml

Тебе нужно будет:

1. **Удалить зависимости MySQL и JPA:**
```xml
<!-- УДАЛИТЬ ЭТИ ЗАВИСИМОСТИ -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
</dependency>
```

2. **Добавить Tarantool Java connector:**
```xml
<!-- ДОБАВИТЬ ЭТУ ЗАВИСИМОСТЬ -->
<dependency>
    <groupId>io.tarantool</groupId>
    <artifactId>cartridge-driver</artifactId>
    <version>0.9.0</version>
</dependency>
```

## Шаг 3: Изменения в application.yaml

Заменить секцию datasource:

```yaml
# БЫЛО (MySQL):
spring:
  datasource:
    username: root
    password: root
    url: jdbc:mysql://localhost:3306/search_engine?useSSL=false
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
    hibernate:
      ddl-auto: update

# СТАНЕТ (Tarantool):
tarantool:
  host: localhost
  port: 3301
  username: searchengine
  password: searchengine
  connections: 10
```

## Шаг 4: Изменения в коде

### 4.1 Удалить JPA аннотации из моделей

Из классов `Site`, `Page`, `Lemma`, `Index` нужно убрать:
- `@Entity`
- `@Id`
- `@GeneratedValue`
- `@Column`
- `@ManyToOne`
- `@OneToMany`
- `@JoinColumn`

Оставить только Lombok аннотации (`@Getter`, `@Setter`, `@ToString`).

### 4.2 Создать конфигурацию Tarantool

Создать класс `TarantoolConfig.java`:

```java
@Configuration
public class TarantoolConfig {
    
    @Value("${tarantool.host}")
    private String host;
    
    @Value("${tarantool.port}")
    private int port;
    
    @Value("${tarantool.username}")
    private String username;
    
    @Value("${tarantool.password}")
    private String password;
    
    @Bean
    public TarantoolClient tarantoolClient() {
        TarantoolClientConfig config = new TarantoolClientConfig.Builder()
            .withCredentials(username, password)
            .build();
            
        TarantoolServerAddress address = new TarantoolServerAddress(host, port);
        
        return TarantoolClientFactory.createClient(address, config);
    }
}
```

### 4.3 Переписать Repository

Вместо `JpaRepository` создать свои репозитории с использованием `TarantoolClient`.

Пример для `SiteRepository`:

```java
@Repository
public class SiteRepository {
    
    @Autowired
    private TarantoolClient client;
    
    public Site save(Site site) {
        TarantoolSpace space = client.space("site");
        
        if (site.getId() == 0) {
            // INSERT - генерируем ID
            int newId = getNextId("site");
            site.setId(newId);
        }
        
        List<Object> tuple = Arrays.asList(
            site.getId(),
            site.getStatus().name(),
            site.getStatusTime().toString(),
            site.getLastError(),
            site.getUrl(),
            site.getName()
        );
        
        space.replace(tuple);
        return site;
    }
    
    public Optional<Site> findById(int id) {
        TarantoolSpace space = client.space("site");
        TarantoolResult result = space.select(Conditions.indexEquals("primary", Collections.singletonList(id)));
        
        if (result.isEmpty()) {
            return Optional.empty();
        }
        
        return Optional.of(mapToSite(result.get(0)));
    }
    
    public List<Site> findAll() {
        TarantoolSpace space = client.space("site");
        TarantoolResult result = space.select(Conditions.any());
        
        return result.stream()
            .map(this::mapToSite)
            .collect(Collectors.toList());
    }
    
    private Site mapToSite(List<?> tuple) {
        Site site = new Site();
        site.setId(((Number) tuple.get(0)).intValue());
        site.setStatus(Status.valueOf((String) tuple.get(1)));
        site.setStatusTime(LocalDateTime.parse((String) tuple.get(2)));
        site.setLastError((String) tuple.get(3));
        site.setUrl((String) tuple.get(4));
        site.setName((String) tuple.get(5));
        return site;
    }
    
    private int getNextId(String spaceName) {
        // Простая реализация - найти максимальный ID + 1
        // В продакшене лучше использовать sequences
        TarantoolSpace space = client.space(spaceName);
        TarantoolResult result = space.select(Conditions.any());
        
        return result.stream()
            .mapToInt(tuple -> ((Number) tuple.get(0)).intValue())
            .max()
            .orElse(0) + 1;
    }
}
```

## Шаг 5: Основные отличия от MySQL/JPA

| Аспект | MySQL + JPA | Tarantool |
|--------|-------------|-----------|
| Подключение | JDBC URL | Host + Port |
| ORM | Hibernate автоматически | Ручной маппинг |
| Транзакции | `@Transactional` | `box.begin()` / `box.commit()` |
| Миграции | Hibernate DDL | Lua скрипты |
| Запросы | JPQL/HQL | Lua функции или Java API |
| Связи | Автоматические JOIN | Ручная загрузка связанных данных |

## Шаг 6: Полезные команды Tarantool

В консоли Tarantool (после `docker exec -it searchengine-tarantool tarantoolctl connect 3301`):

```lua
-- Посмотреть все спейсы
box.space

-- Посмотреть данные в спейсе
box.space.site:select()

-- Вставить данные
box.space.site:insert{1, 'INDEXING', '2024-01-01T10:00:00', nil, 'https://example.com', 'Example'}

-- Обновить данные
box.space.site:update(1, {{'=', 2, 'INDEXED'}})

-- Удалить данные
box.space.site:delete(1)

-- Посмотреть индексы
box.space.site.index

-- Поиск по индексу
box.space.site.index.url:select{'https://example.com'}
```

## Что дальше?

После запуска Docker контейнера тебе нужно будет:

1. Добавить зависимость Tarantool в `pom.xml`
2. Изменить `application.yaml`
3. Создать `TarantoolConfig`
4. Переписать все Repository классы
5. Убрать JPA аннотации из моделей
6. Протестировать подключение

Я буду помогать на каждом шаге!

Протестировать можно [здесь](https://t.me/i_wanna_travel_bot)
# I Wanna Travel 💜 - бот для планировки путешествий

## Как запустить?
### 1. Скачайте репозиторий
```bash
git clone https://github.com/Central-University-IT-prod/backend-Neruxov/tree/main
```

### 2. Перейдите в папку проекта
```bash
cd backend-Neruxov
```

### 3. Настройте переменные окружения в файле `docker-compose.yml`

### 4. Запустите с помощью Docker Compose
```bash
docker compose up -d
```

### Готово! Бот запущен и готов к использованию.

## Как использовать?

### Для запуска бота необходимо написать команду `/start`

https://github.com/Central-University-IT-prod/backend-Neruxov/assets/74096901/082a6245-3181-42c6-9f1a-48b4290ea45e

https://github.com/Central-University-IT-prod/backend-Neruxov/assets/74096901/c30eb34c-40c5-41e1-8db4-5ba5586f115a

https://github.com/Central-University-IT-prod/backend-Neruxov/assets/74096901/94516c9e-0a13-47d1-88e4-5d7e38c63ebb

https://github.com/Central-University-IT-prod/backend-Neruxov/assets/74096901/79f8a2fd-b624-4306-a025-468e6f58867f

https://github.com/Central-University-IT-prod/backend-Neruxov/assets/74096901/b5dbfea7-c7a0-4d70-afc0-4f13cfcd08b7

## Как устроена база данных бота?

![diagram](https://github.com/Central-University-IT-prod/backend-Neruxov/assets/74096901/ef20b231-c162-4f6c-9403-8a1caf217a79)

## Какие технологии используются?

- Kotlin
- Spring Boot
- Spring Data JPA
- [Telegram Bot API](https://github.com/pengrad/java-telegram-bot-api)
- [Spring Boot Telegram Bot Starter](https://github.com/kshashov/spring-boot-starter-telegram)
- Docker Compose
- PostgreSQL

Также используется микросервисная архитектура (частично). 

Технологии микросервиса для статических карт (генерации изображения):
- Python 3.11
- Flask
- [py-staticmaps](https://github.com/flopp/py-staticmaps) и его зависимости
- Docker

### Почему PostgreSQL?

Данные о путешествиях, пользователях и так далее структурированы и хорошо подходят для хранения в реляционной базе данных. PostgreSQL - это мощная и надежная СУБД, которая хорошо подходит для проектов любого масштаба. Иммено поэтому было принято решение использовать PostgreSQL.

## Какие API используются?

- [OpenTripMap](https://dev.opentripmap.org/product) - для получения информации о достопримечательностях, отелях и так далее
- [Open-Meteo](https://open-meteo.com) - для получения информации о погоде
- [OpenStreetMap Tile Server](https://operations.osmfoundation.org/policies/tiles/) - для получения карт
- [Nominatim](https://nominatim.org/release-docs/develop/api/Overview/) - для получения информации о геолокации (поиск по названию, поиск по координатам и так далее)

## Почему именно эти API?

- OpenTripMap - бесплатный источник с квотой 5000 запросов/день, который предоставляет актуальную и полезную информацию
  о достопримечательностях, отелях и так далее.
- Open-Meteo - бесплатный и опен-сурс источник с квотой 10000 запросов/день для некоммерческих огранизаций, который
  предоставляет актуальную и полезную информацию о погоде.
- OpenStreetMap Tile Server - бесплатный источник для некоммерческих организаций, который предоставляет карты для
  построения маршрутов.
- Nominatim - бесплатный и опен-сурс источник, который предоставляет информацию о геолокации.

## Какие функции доступны?

- Рестрация и редактирование профиля
- Создание, редактирование и архивирование путешествий
- Поиск достопримечательностей, отелей, кафе, ресторанов, культурных мест и так далее
- Прогноз погоды в промежуточных точках
- Построение маршрута на карте
- Путешествия с друзьями
- Заметки к путешествиям (файлы, текст, фото и так далее)

#### Небольшое outro: спасибо организаторам за опыт и интересное задание, которое позволило мне попробовать что-то новое и интересное. Было очень весело и интересно работать над этим проектом. Спасибо за внимание! 💜

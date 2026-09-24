# MineDota

Fabric-мод для Minecraft **1.20.1**: упрощённая MOBA в духе DotA 2.

## Что есть в MVP (v0.1)

- Команды `/dota start`, `/dota stop`, `/dota join radiant|dire`, `/dota status`
- Карта в духе DotA (~190×190):
  - Radiant **ЮЗ**, Dire **СВ**
  - **3 линии** (top / mid / bot) + тропинки
  - **река** поперёк мида, песок по берегам
  - **джунгли** (рощи), **ямка Рошана** (декор)
  - базы с фонтаном, раксами, **башни** на линиях, Ancients
- Волны крипов по всем трём линиям (идут к базе врага)
- Башни бьют врагов; победа — уничтожить Ancient
- Герой: ПКМ **Посох Силы** — AoE урон

Это **не** полная DotA 2 — прототип геймплея и карты.

## Требования

- JDK **17+**
- Minecraft 1.20.1 + Fabric Loader + Fabric API

## Сборка

Нужен JDK 17+. В папке проекта уже лежит portable JDK (`.jdk`), `gradlew.bat` подхватит его сам.

```bat
cd "e:\AI Cursor 1C\minecraft-dota-mod"
gradlew.bat build
```

Готовый jar: `build/libs/minedota-<version>.jar` (без `-sources`). Текущая версия — в `gradle.properties` (`mod_version`).

Если мало RAM — закрой браузер/Telegram и повтори; первой сборке нужно ~2 ГБ.

## Git / релизы

Готовый jar кладём в `releases/minedota-<version>.jar` и пушим вместе с исходниками.

Каждый bump `mod_version` → сборка → copy jar → commit + push:

```bat
gradlew.bat build
copy /Y build\libs\minedota-X.Y.Z.jar releases\minedota-X.Y.Z.jar
git add minecraft-dota-mod
git commit -m "minedota: vX.Y.Z — краткое описание"
git push origin main
```

Скачать: `minecraft-dota-mod/releases/minedota-0.1.43.jar`

Не коммитить: `build/`, `.gradle/`, `.jdk/`, `run/` (см. `.gitignore`). `releases/*.jar` — **нужно** коммитить.

## Как играть

1. Новый мир → пресет **Dota 2**
2. Лобби:
   - **Radiant / Dire** — сторона
   - **Heroes** — выбор героя
   - **Start** → таймер пика **60с** (если все выбрали раньше → сразу закуп **15с**)
   - Кто не выбрал к концу пика — **рандом** из свободных
   - После закупа — телепорт на базы
3. Модель героя — отдельная 3D (не скин), свой силуэт/цвет/оружие
4. В матче: **Z X C V**, иконки над хотбаром


### Герои (20)
- **Сила:** Axe, Pudge, Sven, Tiny, Legion Commander
- **Ловкость:** Juggernaut, PA, Anti-Mage, Drow, Sniper
- **Интеллект:** CM, Zeus, Lina, Lion, Witch Doctor
- **Универсал:** Spectre, Venomancer, Abaddon, Void Spirit, Snapfire




## Лицензия

MIT (код мода). DotA / Valve / Valve Corporation — торговые марки третьих лиц; мод — фан-прототип, не связан с Valve.

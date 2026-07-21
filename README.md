# CampfireAnyplace

A Paper 1.21.11 plugin that lets players place any item, including items with
custom data, into campfire slots by right-clicking the top of a campfire.

Items fill slots from 0 through 3. Survival and adventure players use one item;
creative players keep their held stack. Placing a campfire item that contains
block-state item data restores its items and cooking timers instead of losing or
dropping those contents.

## Build

Requires Java 21 and Maven:

```sh
mvn clean package
```

Install `target/campfire-anyplace-1.0.0.jar` in the server's `plugins` directory.

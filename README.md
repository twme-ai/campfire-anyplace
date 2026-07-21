# CampfireAnyplace

A Paper 1.21.11 plugin that lets players place any item, including items with
custom data, into campfire slots by right-clicking the top of a campfire.

Items fill slots from 0 through 3. Survival and adventure players use one item;
creative players keep their held stack. Non-food items stay on the campfire
without being cooked or ejected, and changes are sent to players immediately.
Interacting with an unlit campfire keeps it unlit.

Placing a campfire item that contains block-state item data restores its items,
cooking timers, and disabled cooking state instead of losing or dropping those
contents.

## Build

Requires Java 21 and Maven:

```sh
mvn clean package
```

Install `target/campfire-anyplace-1.0.1.jar` in the server's `plugins` directory.

## License

This project is licensed under the [MIT License](LICENSE).

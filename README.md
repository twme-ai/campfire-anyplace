# CampfireAnyplace

A Paper 1.21.11 plugin that lets players place any item, including items with
custom data, into the slots of regular campfires and soul campfires by
right-clicking their top face.

Items fill slots from 0 through 3. Survival and adventure players use one item;
creative players keep their held stack. The main hand is used first, and the
off hand works when the main hand is empty. Non-food items stay on either
campfire type without being cooked or ejected, and changes are sent to players
immediately. Interacting with an unlit campfire keeps it unlit.

Placing a campfire item that contains block-state item data restores its items,
cooking timers, and disabled cooking state instead of losing or dropping those
contents. In creative mode, using Ctrl + pick block on an unlit campfire also
stores its unlit state so the copied campfire remains unlit when placed.

When the plugin starts or a chunk loads, it also stabilizes non-food items that
were placed by an older plugin version so they cannot resume cooking and eject.

## Build

Requires Java 21 and Maven:

```sh
mvn clean package
```

Install `target/campfire-anyplace-1.1.2.jar` in the server's `plugins` directory.

## License

This project is licensed under the [MIT License](LICENSE).

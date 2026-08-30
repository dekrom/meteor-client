# What this fork changes

A build of [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) with a handful of
bug fixes and the calls home removed. Everything here sits on top of upstream `master`, so it is
a rebase away from vanilla Meteor at any point.

This is **not** an official Meteor build. Releases are versioned `26.3-bephax.<n>` so they cannot
be mistaken for one.

## Bug fixes

All of these are open pull requests against upstream. They live here so the fixes are usable
before they are merged, and they will be dropped from this branch as they land.

| Fix | PR |
|---|---|
| Map hud element scaled by the gui scale twice, so it cannot be positioned at any scale other than 1 | [#6624](https://github.com/MeteorDevelopment/meteor-client/pull/6624) |
| Proxy checker races on `isEmpty()`/`take()` and parks its workers, wedging refresh for the session | [#6625](https://github.com/MeteorDevelopment/meteor-client/pull/6625) |
| Blur closes its texture views but not the textures, leaking ~11 MB of VRAM per resize event | [#6626](https://github.com/MeteorDevelopment/meteor-client/pull/6626) |
| A failed font load leaves the renderer destroyed, so every gui text draw throws until restart | [#6627](https://github.com/MeteorDevelopment/meteor-client/pull/6627) |
| Chams leaves the polygon offset enabled when NoRender cancels a dead entity | [#6628](https://github.com/MeteorDevelopment/meteor-client/pull/6628) |
| ElytraFly Bounce recasts when *any* living entity stops gliding, sending a stray packet | [#6629](https://github.com/MeteorDevelopment/meteor-client/pull/6629) |
| Config saves are not atomic when `/tmp` is a separate mount, which truncates the file first | [#6630](https://github.com/MeteorDevelopment/meteor-client/pull/6630) |
| ESP fade distance is squared twice, so the fade starts at 9 blocks on a setting of 3 | [#6631](https://github.com/MeteorDevelopment/meteor-client/pull/6631) |
| Bold Italic fonts are saved as `"Bold Italic"` and loaded with `valueOf`, so they never persist | [#6632](https://github.com/MeteorDevelopment/meteor-client/pull/6632) |
| Recoloured lightning uses the segment index as an x coordinate | [#6633](https://github.com/MeteorDevelopment/meteor-client/pull/6633) |
| The enchantment name cache is the one cache not cleared on resource reload | [#6634](https://github.com/MeteorDevelopment/meteor-client/pull/6634) |
| Hud text width counts the shadow offset twice, padding every shadowed element | [#6635](https://github.com/MeteorDevelopment/meteor-client/pull/6635) |
| Binds without modifiers stop matching while a modifier is held, so a bind on Left Alt is dead | [#6597](https://github.com/MeteorDevelopment/meteor-client/pull/6597) |

## Network changes

These are deliberate local policy, not bugs, and are not going upstream.

**No cape fetching.** `Capes` no longer pulls the owner and texture lists from
`meteorclient.com`. Nothing is registered, so no capes render. This drops two requests per launch
and the account-to-cape lookup that comes with them.

**No online counter ping.** `OnlinePlayers` no longer posts to `meteorclient.com/api/online/ping`
every five minutes.

**No Discord rich presence.** `DiscordPresence` still computes its status text so the module and
its settings behave normally, but never opens the Discord IPC socket or publishes anything. The
module is otherwise untouched.

**Outbound requests are allowlisted.** `Http` refuses any request to a host that is not on a
short list (Mojang session and profile endpoints, TheAltening, and `bep.dek.to`) before a
connection is opened. Blocked requests come back as a failed response through the normal error
path rather than throwing, so callers behave as if the endpoint were unreachable.

The point of the last one is that it fails closed: a future upstream feature that adds a new
endpoint gets blocked by default rather than silently starting to phone home after a rebase.

## Building

```bash
./gradlew build        # needs JDK 25, MC 26.3 is unobfuscated so there are no mappings
```

Releases are built by `.github/workflows/release.yml`, run by hand from the Actions tab.

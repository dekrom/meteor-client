# What this fork changes

A build of [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) with a handful of
bug fixes and the calls home removed. Everything here sits on top of upstream's last build for
Minecraft 1.21.5 (`21145efd8`, build 54 on the Meteor maven), so it is a rebase away from vanilla
Meteor at any point.

This is **not** an official Meteor build. Releases are versioned `1.21.5-bephax.<n>` so they cannot
be mistaken for one.

## Bug fixes

The same fixes the 26.3 branch carries, limited to the bugs that exist on Minecraft 1.21.5.
Upstream no longer builds 1.21.5, so they stay here for good.

| Fix | PR |
|---|---|
| A failed font load leaves the renderer destroyed, so every gui text draw throws until restart | [#6627](https://github.com/MeteorDevelopment/meteor-client/pull/6627), closed upstream, kept here |
| Chams leaves the polygon offset enabled when NoRender cancels a dead entity | [#6628](https://github.com/MeteorDevelopment/meteor-client/pull/6628) |
| ElytraFly Bounce recasts when *any* living entity stops gliding, sending a stray packet | [#6629](https://github.com/MeteorDevelopment/meteor-client/pull/6629) |
| Config saves copy a temp file over the old one, truncating it first, so a crash mid-save loses the config | [#6630](https://github.com/MeteorDevelopment/meteor-client/pull/6630) |
| ESP fade distance is squared twice, so the fade starts at 9 blocks on a setting of 3 | [#6631](https://github.com/MeteorDevelopment/meteor-client/pull/6631) |
| Bold Italic fonts are saved as `"Bold Italic"` and loaded with `valueOf`, so they never persist | [#6632](https://github.com/MeteorDevelopment/meteor-client/pull/6632) |
| The enchantment name cache is the one cache not cleared on resource reload | [#6634](https://github.com/MeteorDevelopment/meteor-client/pull/6634) |
| Hud text width counts the shadow offset twice, padding every shadowed element | [#6635](https://github.com/MeteorDevelopment/meteor-client/pull/6635) |

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
short list (Mojang session and profile endpoints, the Microsoft and Xbox Live sign-in hosts
that this version's account login goes through, TheAltening, and `bep.dek.to`) before a
connection is opened. Blocked requests come back as a failed response without throwing or reaching
the caller's error handler, so the features that need another host quietly do nothing: the addon
update markers on the title screen and the commits screen (GitHub), and `.name-history` (laby.net).

The point of the last one is that it fails closed: a future upstream feature that adds a new
endpoint gets blocked by default rather than silently starting to phone home after a rebase.

## Building

```bash
./gradlew build        # needs JDK 21, MC 1.21.5 is obfuscated and built with Yarn mappings
```

Releases are cut by `.github/workflows/release.yml`, run by hand from the Actions tab. It works
out the next version from the existing tags, checks that the version inside the jar matches the
tag, and writes the changelog from the commits this branch carries on top of upstream.

# Bazaar Helper

Bazaar Helper is a tiny Fabric mod that keeps track of your Bazaar orders, overlays Bazaar data in-screen, and automates the sell picker for you. The prebuilt jar is the only artifact you need—building locally pulls in big dependencies and takes longer than using the release asset.

## Download the Jar

1. Open this repository's **Releases** page (look for the latest entry with “bazaar-helper” in the title).
2. Download the matching `bazaar-helper-*.jar` file from the release assets.
3. Copy the jar into your Minecraft instance’s `mods/` folder.

## Why You Shouldn’t Build

The jar already contains everything the mod needs. Building it yourself requires matching Fabric, mappings, and remapping steps, so trying to compile it on your own usually ends up out of sync with the packaged release. Stick with the release jar, and rely on the Fabric loader to run it—no build step is necessary.
